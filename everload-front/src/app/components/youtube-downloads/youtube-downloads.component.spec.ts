import { ComponentFixture, TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { NO_ERRORS_SCHEMA, Pipe, PipeTransform } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { YoutubeDownloadsComponent } from './youtube-downloads.component';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { ChatService } from '../../services/chat.service';
import { ApiBaseService } from '../../services/api-base.service';

/** Stand-in for the real `translate` pipe so the template can render without ngx-translate's loader machinery. */
@Pipe({ name: 'translate' })
class FakeTranslatePipe implements PipeTransform {
  transform(value: string): string { return value; }
}

describe('YoutubeDownloadsComponent', () => {
  let component: YoutubeDownloadsComponent;
  let fixture: ComponentFixture<YoutubeDownloadsComponent>;
  let translateSpy: jasmine.SpyObj<TranslateService>;

  beforeEach(async () => {
    translateSpy = jasmine.createSpyObj<TranslateService>('TranslateService', ['setDefaultLang', 'use', 'instant']);
    translateSpy.instant.and.callFake((key: string) => key);

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, HttpClientTestingModule, FormsModule],
      declarations: [YoutubeDownloadsComponent, FakeTranslatePipe],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: TranslateService, useValue: translateSpy },
        { provide: AuthService, useValue: jasmine.createSpyObj<AuthService>('AuthService', ['hasNasAccess']) },
        { provide: NotificationService, useValue: jasmine.createSpyObj<NotificationService>('NotificationService', ['showToast']) },
        { provide: ChatService, useValue: jasmine.createSpyObj<ChatService>('ChatService', ['getGroups', 'getActiveUsers']) },
        { provide: ApiBaseService, useValue: { backendUrl: '', isNativePlatform: () => false, withBackend: (s: string) => s } },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(new Map()) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(YoutubeDownloadsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows device download actions without a NAS save action', () => {
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    expect(buttons.some(button => button.textContent?.includes('DOWNLOAD_VIDEO'))).toBeTrue();
    expect(buttons.some(button => button.textContent?.includes('DOWNLOAD_MUSIC'))).toBeTrue();
    expect(buttons.some(button => button.textContent?.includes('NAS.SAVE_TO_NAS'))).toBeFalse();
    expect(fixture.nativeElement.textContent).not.toContain('NAS.SELECT_FOLDER');
  });

  it('persists the chosen language preference', () => {
    spyOn(localStorage, 'setItem');

    component.changeLanguage('en');

    expect(translateSpy.use).toHaveBeenCalledWith('en');
    expect(localStorage.setItem).toHaveBeenCalledWith('language', 'en');
  });

  describe('addToQueue()', () => {
    it('alerts and skips when the URL is blank', () => {
      spyOn(window, 'alert');
      component.videoUrl = '   ';

      component.addToQueue('video');

      expect(window.alert).toHaveBeenCalledWith('PLEASE_ENTER_YOUTUBE_LINK');
      expect(component.queue.length).toBe(0);
    });

    it('alerts and skips when the URL has no recognizable video id', () => {
      spyOn(window, 'alert');
      component.videoUrl = 'https://example.com/not-a-video';

      component.addToQueue('video');

      expect(window.alert).toHaveBeenCalledWith('INVALID_YOUTUBE_LINK');
      expect(component.queue.length).toBe(0);
    });

    it('queues a valid video URL with the chosen resolution and reveals the queue panel', () => {
      component.videoUrl = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';
      component.resolution = '1080';

      component.addToQueue('video');

      expect(component.queue.length).toBe(1);
      expect(component.queue[0].videoId).toBe('dQw4w9WgXcQ');
      expect(component.queue[0].type).toBe('video');
      expect(component.queue[0].resolution).toBe('1080');
      // processQueue() picks up the item immediately and flips it to 'downloading'
      expect(component.queue[0].status).toBe('downloading');
      expect(component.showQueue).toBeTrue();
    });

    it('queues a music download without a resolution', () => {
      component.videoUrl = 'https://youtu.be/dQw4w9WgXcQ';

      component.addToQueue('music');

      expect(component.queue.length).toBe(1);
      expect(component.queue[0].type).toBe('music');
      expect(component.queue[0].resolution).toBeUndefined();
    });
  });

  it('exposes isLoading based on whether any queue item is downloading', () => {
    expect(component.isLoading).toBeFalse();

    component.queue = [{ id: '1', videoId: 'abc', title: 'abc', type: 'video', status: 'downloading', progress: 10 } as any];

    expect(component.isLoading).toBeTrue();
  });

  describe('device audio delivery', () => {
    let http: HttpTestingController;
    let deliver: jasmine.Spy;
    const job = { jobId: 'test-job', status: 'QUEUED', progress: 5, filename: "Don't Stop 100%.mp3" };
    const statusUrl = '/api/downloadMusic/jobs/test-job';
    const fileUrl = `${statusUrl}/file`;
    const item = () => ({ id: 'item', videoId: 'jNQXAC9IVRw', type: 'music', title: 'Demo',
      audioFormat: 'mp3', progress: 0, status: 'pending' } as any);
    const start = (entry: any) => {
      (component as any).processMusicItemAsJob(entry);
      http.expectOne(r => r.method === 'POST' && r.url === '/api/downloadMusic/jobs').flush(job);
    };
    beforeEach(() => {
      http = TestBed.inject(HttpTestingController);
      deliver = spyOn<any>(component, 'triggerDownload');
    });
    afterEach(() => { (component as any).cancelActiveDownload?.(); http.verify(); });

    it('does not overlap slow status requests or fetch a prepared file twice', fakeAsync(() => {
      const entry = item(); start(entry);
      const status = http.expectOne(statusUrl);
      tick(7500);
      http.expectNone(statusUrl);
      status.flush({ ...job, status: 'DONE' });
      const file = http.expectOne(fileUrl);
      tick(7500);
      http.expectNone(statusUrl);
      http.expectNone(fileUrl);
      file.flush(new Blob(['audio']), { headers: { 'Content-Disposition': `attachment; filename="${job.filename}"` } });
      flushMicrotasks();
      tick(10000);
      expect(entry.status).toBe('completed');
      expect(deliver).toHaveBeenCalledOnceWith(jasmine.any(Blob), job.filename);
    }));

    it('waits after each response before scheduling another progress read', fakeAsync(() => {
      const entry = item(); start(entry);
      http.expectOne(statusUrl).flush({ ...job, status: 'RUNNING' });
      tick(2499); http.expectNone(statusUrl);
      tick(1); http.expectOne(statusUrl).flush({ ...job, status: 'ERROR', error: 'Unavailable' });
      flushMicrotasks();
      expect(entry.status).toBe('failed');
      expect(entry.error).toBe('Unavailable');
    }));

    it('honors Retry-After on a temporary rate limit', fakeAsync(() => {
      const entry = item(); start(entry);
      http.expectOne(statusUrl).flush({}, { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '60' } });
      tick(59999); http.expectNone(statusUrl);
      tick(1); http.expectOne(statusUrl).flush({ ...job, status: 'ERROR', error: 'Unavailable' });
      flushMicrotasks();
      expect(entry.status).toBe('failed');
    }));

    it('times out a hung status request and permits retry', fakeAsync(() => {
      const entry = item(); start(entry);
      const first = http.expectOne(statusUrl);
      tick(15000);
      expect(first.cancelled).toBeTrue();
      tick(2500);
      http.expectOne(statusUrl).flush({ ...job, status: 'ERROR', error: 'Unavailable' });
      flushMicrotasks();
      expect(entry.status).toBe('failed');
    }));

    it('retries delivery of the prepared audio without starting another extraction', fakeAsync(() => {
      const entry = item(); start(entry);
      http.expectOne(statusUrl).flush({ ...job, status: 'DONE' });
      http.expectOne(fileUrl).flush(new Blob(), { status: 503, statusText: 'Unavailable' });
      flushMicrotasks();
      expect(entry.status).toBe('failed');
      (component as any).processMusicItemAsJob(entry);
      http.expectNone(r => r.method === 'POST');
      http.expectOne(fileUrl).flush(new Blob(['audio']));
      flushMicrotasks();
      expect(entry.status).toBe('completed');
      expect(deliver).toHaveBeenCalledTimes(1);
    }));

    it('clears an expired file so the next retry can start a new download', fakeAsync(() => {
      const entry = item(); start(entry);
      http.expectOne(statusUrl).flush({ ...job, status: 'DONE' });
      http.expectOne(fileUrl).flush(new Blob(), { status: 404, statusText: 'Not Found' });
      flushMicrotasks();
      expect(entry.preparedDownload).toBeUndefined();
      expect(entry.status).toBe('failed');
    }));

    it('cancels an active request without issuing another poll', fakeAsync(() => {
      const entry = item(); start(entry);
      const request = http.expectOne(statusUrl);
      (component as any).cancelActiveDownload();
      flushMicrotasks(); tick(30000);
      expect(request.cancelled).toBeTrue();
      expect(entry.status).toBe('cancelled');
      http.expectNone(statusUrl);
    }));
  });

  it('parses filenames without decoding literal percent signs or truncating apostrophes', () => {
    const filename = (header: string | null) => (component as any).downloadFilename(header, 'fallback.mp3');
    expect(filename(`attachment; filename="Don't Stop 100%.mp3"`)).toBe("Don't Stop 100%.mp3");
    expect(filename(`attachment; filename="100%20mix.mp3"`)).toBe('100%20mix.mp3');
    expect(filename(`attachment; filename="fallback.mp3"; filename*=UTF-8''Canci%C3%B3n.mp3`)).toBe('Canci\u00f3n.mp3');
    expect(filename(`attachment; filename*=UTF-8''bad%ZZ.mp3; filename="safe.mp3"`)).toBe('safe.mp3');
    expect(filename(null)).toBe('fallback.mp3');
  });

  it('keeps the blob URL alive while the browser starts saving the audio', fakeAsync(() => {
    spyOn(window.URL, 'createObjectURL').and.returnValue('blob:test');
    const revoke = spyOn(window.URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click');
    (component as any).triggerDownload(new Blob(['audio']), 'song.mp3');
    tick(59999); expect(revoke).not.toHaveBeenCalled();
    tick(1); expect(revoke).toHaveBeenCalledWith('blob:test');
  }));
});
