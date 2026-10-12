import { of } from 'rxjs';
import { ModernHomeComponent } from './modern-home.component';

describe('ModernHomeComponent recommendations', () => {
  function createComponent(data: {
    history?: any[];
    tracks?: any[];
    favorites?: any[];
    community?: { topArtists: any[]; topTracks: any[] };
    createdAt?: string;
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
    const state: any = { getOverview: () => of({ tracks: data.tracks || [], indexing: false }) };
    const auth: any = { getCurrentUser: () => ({ createdAt: data.createdAt }) };
    const component = new ModernHomeComponent(music, state, {} as any, auth);
    spyOn<any>(component, 'checkFeaturedFav').and.stub();
    spyOn<any>(component, 'startFeaturedRotation').and.stub();
    spyOn<any>(component, 'resolveAutoArtistImages').and.stub();
    spyOn<any>(component, 'prefetchHomeCovers').and.stub();
    (component as any).load(1);
    return component;
  }

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
