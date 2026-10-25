import { of, Subject } from 'rxjs';
import { fakeAsync, tick } from '@angular/core/testing';
import { ModernHomeComponent } from './modern-home.component';

describe('ModernHomeComponent recommendations', () => {
  function createComponent(data: {
    history?: any[];
    tracks?: any[];
    favorites?: any[];
    community?: { topArtists: any[]; topTracks: any[] };
    createdAt?: string;
    indexing?: boolean;
    fullOverview?: Subject<any>;
  }) {
    const music: any = {
      getHistory: () => of(data.history || []),
      getRecentTracks: () => of([]),
      getArtistProfiles: () => of([]),
      getTopArtists: () => of([]),
      getFavorites: () => of(data.favorites || []),
      getCommunityDiscover: () => of(data.community || { topArtists: [], topTracks: [] }),
      fetchCoverIfNeeded: jasmine.createSpy('fetchCoverIfNeeded'),
      resolveArtistImages: jasmine.createSpy('resolveArtistImages'),
      BASE: 'http://backend'
    };
    const state: any = { getOverview: (_pathId: number, limit?: number) =>
      !limit && data.fullOverview ? data.fullOverview : of({ tracks: data.tracks || [], indexing: data.indexing || false }) };
    const auth: any = { getCurrentUser: () => ({ createdAt: data.createdAt }) };
    const component = new ModernHomeComponent(music, state, {} as any, auth);
    spyOn<any>(component, 'checkFeaturedFav').and.stub();
    spyOn<any>(component, 'startFeaturedRotation').and.stub();
    spyOn<any>(component, 'resolveAutoArtistImages').and.stub();
    spyOn<any>(component, 'prefetchHomeCovers').and.stub();
    (component as any).load(1);
    return component;
  }

  it('renders the home before the full library arrives, then enriches it without a blocking spinner', () => {
    const fullOverview = new Subject<any>();
    const component = createComponent({ fullOverview,
      tracks: [{ title: 'First', path: 'first.mp3', album: 'First album', artist: 'Artist' }] });
    expect(component.loading).toBeFalse();
    expect(component.listenNow.length).toBe(1);
    fullOverview.next({ indexing: false, tracks: [
      { title: 'First', path: 'first.mp3', album: 'First album', artist: 'Artist' },
      { title: 'Second', path: 'second.mp3', album: 'Second album', artist: 'Artist' }
    ] });
    expect(component.loading).toBeFalse();
    expect(component.listenNow.length).toBe(2);
    component.ngOnDestroy();
  });

  it('refreshes nonempty partial recommendations and cancels polling on destroy', fakeAsync(() => {
    const component = createComponent({ tracks: [{ path: 'song.mp3' }], indexing: true });
    const load = spyOn<any>(component, 'load').and.callThrough();
    tick(6000);
    expect(load).toHaveBeenCalledTimes(1);
    component.ngOnDestroy();
    tick(12000);
    expect(load).toHaveBeenCalledTimes(1);
  }));

  it('keeps the complete home visible during a slow index refresh without replaying the small preview', fakeAsync(() => {
    const fullOverview = new Subject<any>();
    const first = { title: 'First', path: 'first.mp3', album: 'First album', artist: 'Artist' };
    const second = { title: 'Second', path: 'second.mp3', album: 'Second album', artist: 'Artist' };
    const component = createComponent({ fullOverview, tracks: [first] });
    fullOverview.next({ tracks: [first, second], indexing: true });
    fullOverview.complete();
    const refresh = new Subject<any>();
    const overview = spyOn((component as any).state, 'getOverview').and.returnValue(refresh);

    tick(6000);
    expect(component.loading).toBeFalse();
    expect(component.listenNow.length).toBe(2);
    expect(overview).toHaveBeenCalledOnceWith(1);
    tick(1200);
    expect(component.loading).toBeFalse();
    refresh.next({ tracks: [first, second], indexing: false });
    refresh.complete();
    tick(12000);
    expect(overview).toHaveBeenCalledTimes(1);
    component.ngOnDestroy();
  }));

  it('retains personalized recommendations when a background refresh fails', fakeAsync(() => {
    const fullOverview = new Subject<any>();
    const track = { title: 'Favorite', path: 'favorite.mp3', album: 'My album', artist: 'Artist' };
    const component = createComponent({ fullOverview, tracks: [track] });
    fullOverview.next({ tracks: [track], indexing: true });
    fullOverview.complete();
    const refresh = new Subject<any>();
    spyOn((component as any).state, 'getOverview').and.returnValue(refresh);
    const fallback = jasmine.createSpy('getRandomTracks');
    (component as any).music.getRandomTracks = fallback;
    tick(6000);
    refresh.error(new Error('Temporary network failure'));
    expect(component.loading).toBeFalse();
    expect(component.listenNow[0].album).toBe('My album');
    expect(fallback).not.toHaveBeenCalled();
    component.ngOnDestroy();
  }));

  it('uses community popularity for a new account with no listening signals', () => {
    const component = createComponent({
      createdAt: '2026-09-26T00:00:00',
      tracks: [
        { title: 'Quiet track', path: 'quiet.mp3', album: 'Quiet album', artist: 'Quiet artist', nasPathId: 1 },
        { title: 'Popular track', path: 'popular.mp3', album: 'Popular album', artist: 'Popular artist', nasPathId: 1 }
      ],
      community: {
        topArtists: [{ artist: 'Popular artist', playCount: 20 }, { artist: 'Quiet artist', playCount: 1 }],
        topTracks: [{ title: 'Popular track', artist: 'Popular artist', album: 'Popular album', playCount: 20 }]
      }
    });

    expect(component.featured?.track.title).toBe('Popular track');
    expect(component.listenNow[0].album).toBe('Popular album');
    expect(component.topArtists.map(artist => artist.artist))
      .withContext(JSON.stringify(component.topArtists.map(artist => ({ artist: artist.artist, count: artist.tracks.length }))))
      .toEqual(['Popular artist', 'Quiet artist']);
  });

  it('excludes unavailable history and favorites and keeps albums separate by NAS and artist', () => {
    const component = createComponent({
      createdAt: '2026-09-26T00:00:00',
      history: [
        { title: 'Missing', trackPath: 'missing.mp3', album: 'Lost album', artist: 'Lost artist', available: false },
        { title: 'History track', trackPath: 'history.mp3', album: 'Shared', artist: 'Artist A', nasPathId: 3, available: true }
      ],
      favorites: [{ title: 'Missing favorite', trackPath: 'missing-fav.mp3', album: 'Lost album', artist: 'Lost artist', available: false }],
      tracks: [
        { title: 'Track one', path: 'one.mp3', album: 'Shared', artist: 'Artist A', nasPathId: 3 },
        { title: 'Track two', path: 'two.mp3', album: 'Shared', artist: 'Artist A', nasPathId: 4 },
        { title: 'Track three', path: 'three.mp3', album: 'Shared', artist: 'Artist B', nasPathId: 3 },
        { title: '', path: 'empty.mp3', album: '   ', artist: 'Artist A', nasPathId: 3 }
      ]
    });

    expect(component.listenNow.map(card => `${card.pathId}:${card.artist}`)).toEqual([
      '3:Artist A', '4:Artist A', '3:Artist B'
    ]);
    expect(component.listenNow.some(card => card.album === 'Lost album')).toBeFalse();
    expect(component.featuredPool.some(item => item.track.title === 'Missing')).toBeFalse();
    expect(component.featuredPool.some(item => item.track.title === 'Missing favorite')).toBeFalse();
  });
});
