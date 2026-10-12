import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { MusicService } from './music.service';
import { AuthService } from './auth.service';
import { ApiBaseService } from './api-base.service';

/**
 * Covers the streaming-quality preference, stream URL construction, social
 * "save to NAS" (yt-dlp) requests and the shareReplay-based caches added to
 * MusicService this session.
 */
describe('MusicService', () => {
  const BACKEND = 'http://test-backend';

  let service: MusicService;
  let httpMock: HttpTestingController;
  let authSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    localStorage.clear();

    authSpy = jasmine.createSpyObj<AuthService>('AuthService', ['getToken', 'getCurrentUser']);
    authSpy.getToken.and.returnValue('test-token');
    authSpy.getCurrentUser.and.returnValue(null);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        MusicService,
        { provide: AuthService, useValue: authSpy },
        { provide: ApiBaseService, useValue: { backendUrl: BACKEND } as ApiBaseService },
      ],
    });

    service = TestBed.inject(MusicService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  describe('stream quality preference', () => {
    it('exposes the four quality presets with their bitrate metadata', () => {
      const values = MusicService.QUALITY_OPTIONS.map(o => o.value);
      expect(values).toEqual(['low', 'normal', 'high', 'original']);
      expect(MusicService.QUALITY_OPTIONS.find(o => o.value === 'original')?.kbps).toBe(0);
    });

    it('defaults to "original" when nothing is stored yet', () => {
      expect(service.getStreamQuality()).toBe('original');
    });

    it('persists the chosen quality to localStorage and reads it back', () => {
      service.setStreamQuality('low');

      expect(localStorage.getItem('streamQuality')).toBe('low');
      expect(service.getStreamQuality()).toBe('low');
    });
  });

  describe('getStreamUrl', () => {
    it('builds a URL carrying the auth token, encoded subPath and current quality', () => {
      service.setStreamQuality('high');

      const url = service.getStreamUrl(7, 'Artist/My Song.mp3');

      expect(url).toBe(
        `${BACKEND}/api/music/stream?pathId=7&subPath=${encodeURIComponent('Artist/My Song.mp3')}&token=test-token&quality=high`
      );
    });

    it('falls back to "original" quality in the URL when no preference is stored', () => {
      const url = service.getStreamUrl(1, 'song.mp3');

      expect(url).toContain('&quality=original');
    });

    it('requests compatible transcoding for formats that tablets cannot play reliably', () => {
      const url = service.getStreamUrl(1, 'Album/old-track.wma');

      expect(url).toContain('&quality=normal');
    });
  });

  describe('queue playback across NAS paths', () => {
    it('loads each queued track using its own NAS path id', () => {
      const load = spyOn(service.mainPlayer, 'load').and.returnValue(Promise.resolve());
      spyOn(service.mainPlayer, 'play');
      const tracks: any[] = [
        { path: 'one.mp3', nasPathId: 3, source: 'nas' },
        { path: 'two.mp3', nasPathId: 8, source: 'nas' },
      ];

      service.setQueue(3, tracks, 0);
      service.playNextMain();

      expect(load.calls.allArgs().map(args => args[1])).toEqual([3, 8]);
    });

    it('keeps the originating NAS path when advancing directly to a queued track', () => {
      const load = spyOn(service.mainPlayer, 'load').and.returnValue(Promise.resolve());
      spyOn(service.mainPlayer, 'play');
      spyOn<any>(service, 'scheduleNextPreload').and.stub();
      const tracks: any[] = [
        { path: 'one.mp3', nasPathId: 3, source: 'nas' },
        { path: 'two.mp3', nasPathId: 8, source: 'nas' }
      ];

      (service as any).advanceToTrack(3, tracks, 1);

      expect(load).toHaveBeenCalledWith(tracks[1], 8);
    });

    it('preloads the next track using that track NAS path, falling back to the queue path', () => {
      const tracks: any[] = [
        { path: 'one.mp3', nasPathId: 3 },
        { path: 'two.mp3', nasPathId: 8 },
        { path: 'three.mp3' }
      ];
      (service as any).queueSubj.next({ tracks, pathId: 3, index: 0 });

      expect((service as any).peekNextTrack()).toEqual({ track: tracks[1], pathId: 8 });
      (service as any).queueSubj.next({ tracks, pathId: 3, index: 1 });
      expect((service as any).peekNextTrack()).toEqual({ track: tracks[2], pathId: 3 });
    });

    it('repeats the current track on its own NAS path and wraps repeat-all queues', () => {
      const tracks: any[] = [
        { path: 'one.mp3', nasPathId: 3 },
        { path: 'two.mp3', nasPathId: 8 }
      ];
      (service as any).queueSubj.next({ tracks, pathId: 3, index: 1 });
      (service as any)._repeat = 'one';
      expect((service as any).peekNextTrack()).toEqual({ track: tracks[1], pathId: 8 });

      (service as any)._repeat = 'all';
      (service as any).queueSubj.next({ tracks, pathId: 3, index: 1 });
      expect((service as any).peekNextTrack()).toEqual({ track: tracks[0], pathId: 3 });
      (service as any)._repeat = 'none';
      expect((service as any).peekNextTrack()).toBeNull();
    });

    it('follows shuffle order and wraps it only when repeat-all is enabled', () => {
      const tracks: any[] = [
        { path: 'one.mp3', nasPathId: 3 },
        { path: 'two.mp3', nasPathId: 8 },
        { path: 'three.mp3', nasPathId: 12 }
      ];
      (service as any).queueSubj.next({ tracks, pathId: 3, index: 1 });
      (service as any)._shuffle = true;
      (service as any).shuffleOrder = [1, 2, 0];

      expect((service as any).peekNextTrack()).toEqual({ track: tracks[2], pathId: 12 });
      (service as any).queueSubj.next({ tracks, pathId: 3, index: 0 });
      expect((service as any).peekNextTrack()).toBeNull();
      (service as any)._repeat = 'all';
      expect((service as any).peekNextTrack()).toEqual({ track: tracks[1], pathId: 8 });
    });
  });

  describe('yt-dlp social "save to NAS" requests', () => {
    it('queues a social media URL via POST with url/title/nasPathId/subPath as query params', () => {
      const expectedQuery = new URLSearchParams({
        url: 'https://www.facebook.com/reel/1',
        title: 'Mi video',
        nasPathId: '5',
        subPath: 'Reels',
      }).toString();

      service.ytDlpQueueUrl('https://www.facebook.com/reel/1', 'Mi video', 5, 'Reels')
        .subscribe(res => expect(res.jobId).toBe('job-123'));

      const req = httpMock.expectOne(`${BACKEND}/api/nas/ytdlp/queue-url?${expectedQuery}`);
      expect(req.request.method).toBe('POST');
      req.flush({ jobId: 'job-123' });
    });

    it('fetches job status by id', () => {
      service.ytDlpJobStatus('job-123').subscribe(job => expect(job.status).toBe('RUNNING'));

      const req = httpMock.expectOne(`${BACKEND}/api/nas/ytdlp/status/job-123`);
      expect(req.request.method).toBe('GET');
      req.flush({ jobId: 'job-123', status: 'RUNNING' });
    });
  });

  describe('getTopArtists cache', () => {
    const url = `${BACKEND}/api/library/top-artists?limit=20`;

    it('shares a single in-flight HTTP request across concurrent subscribers', () => {
      const payload = [{ artist: 'A', playCount: 3 }];
      let first: any;
      let second: any;

      service.getTopArtists().subscribe(res => first = res);
      service.getTopArtists().subscribe(res => second = res);

      httpMock.expectOne(url).flush(payload);

      expect(first).toEqual(payload);
      expect(second).toEqual(payload);
    });

    it('reuses the cached observable on a later call instead of issuing a new request', () => {
      const first = [{ artist: 'A', playCount: 3 }];
      let secondResult: any;

      service.getTopArtists().subscribe();
      httpMock.expectOne(url).flush(first);

      service.getTopArtists().subscribe(res => secondResult = res);
      httpMock.expectNone(url);

      expect(secondResult).toEqual(first);
    });

    it('issues a fresh request after invalidateTopArtistsCache()', () => {
      service.getTopArtists().subscribe();
      httpMock.expectOne(url).flush([{ artist: 'A', playCount: 1 }]);

      service.invalidateTopArtistsCache();

      let refetched: any;
      service.getTopArtists().subscribe(res => refetched = res);
      httpMock.expectOne(url).flush([{ artist: 'B', playCount: 2 }]);

      expect(refetched).toEqual([{ artist: 'B', playCount: 2 }]);
    });

    it('does not reuse one account\'s artists after switching users', () => {
      authSpy.getCurrentUser.and.returnValue({ username: 'first' } as any);
      service.getTopArtists().subscribe();
      httpMock.expectOne(url).flush([{ artist: 'First user', playCount: 4 }]);

      authSpy.getCurrentUser.and.returnValue({ username: 'second' } as any);
      let secondUser: any;
      service.getTopArtists().subscribe(result => secondUser = result);
      httpMock.expectOne(url).flush([{ artist: 'Second user', playCount: 2 }]);

      expect(secondUser).toEqual([{ artist: 'Second user', playCount: 2 }]);
    });

    it('uses a separate cache entry for each requested limit', () => {
      service.getTopArtists(20).subscribe();
      httpMock.expectOne(url).flush([]);

      service.getTopArtists(50).subscribe();
      const request = httpMock.expectOne(`${BACKEND}/api/library/top-artists?limit=50`);
      expect(request.request.method).toBe('GET');
      request.flush([]);
    });
  });

  describe('getArtistProfiles cache', () => {
    const url = `${BACKEND}/api/artists`;

    it('reuses the cached observable across calls until invalidated', () => {
      const cached = [{ id: 1, name: 'Cached Artist' }];
      let secondResult: any;
      let thirdResult: any;

      service.getArtistProfiles().subscribe();
      httpMock.expectOne(url).flush(cached);

      service.getArtistProfiles().subscribe(res => secondResult = res);
      httpMock.expectNone(url);
      expect(secondResult).toEqual(cached as any);

      service.invalidateArtistProfilesCache();

      const fresh = [{ id: 2, name: 'Fresh Artist' }];
      service.getArtistProfiles().subscribe(res => thirdResult = res);
      httpMock.expectOne(url).flush(fresh);
      expect(thirdResult).toEqual(fresh as any);
    });
  });
});
