import { of, throwError } from 'rxjs';
import { LibraryModeComponent } from './library-mode.component';

describe('LibraryModeComponent unavailable NAS tracks', () => {
  let component: LibraryModeComponent;
  let music: any;
  let nas: any;

  beforeEach(() => {
    music = {
      setQueue: jasmine.createSpy('setQueue'),
      browse: jasmine.createSpy('browse').and.returnValue(of({ items: [], totalTracks: 0 })),
      getHistory: jasmine.createSpy('getHistory').and.returnValue(of([])),
      getFavorites: jasmine.createSpy('getFavorites').and.returnValue(of([])),
      fetchCoverIfNeeded: jasmine.createSpy('fetchCoverIfNeeded')
    };
    nas = { getPaths: jasmine.createSpy('getPaths').and.returnValue(of([])) };
    component = new LibraryModeComponent(music, nas, {} as any, { instant: (key: string) => key } as any, { markForCheck: () => {} } as any);
  });

  it('filters unavailable tracks and plays from the track NAS in the queue', () => {
    const tracks: any[] = [
      { path: 'gone.mp3', nasPathId: 2, available: false },
      { path: 'first.mp3', nasPathId: 3, available: true },
      { path: 'selected.mp3', nasPathId: 8, available: true }
    ];
    component.items = tracks;
    component.selectedPathId = 3;
    component.searchResults = null;

    component.playTrack(tracks[2]);

    expect(music.setQueue).toHaveBeenCalledWith(8, [tracks[1], tracks[2]], 1);
  });

  it('does not queue unavailable tracks or tracks without a NAS path', () => {
    component.selectedPathId = 3;
    component.playTrack({ path: 'gone.mp3', available: false } as any);
    component.selectedPathId = null;
    component.playTrack({ path: 'local.mp3', available: true } as any);

    expect(music.setQueue).not.toHaveBeenCalled();
  });

  it('reloads the current view directly when a NAS path is already known', () => {
    component.paths = [{ id: 1, readable: true } as any];
    component.loadError = true;
    spyOn(component, 'load');

    component.retryLoad();

    expect(component.loadError).toBeTrue();
    expect(component.load).toHaveBeenCalled();
  });

  it('selects the first readable path when retrying path discovery', () => {
    component.paths = [];
    component.loadError = true;
    nas.getPaths.and.returnValue(of([
      { id: 2, readable: false },
      { id: 8, readable: true }
    ]));
    spyOn(component, 'selectPath');

    component.retryLoad();

    expect(component.selectPath).toHaveBeenCalledWith(8);
    expect(component.loadError).toBeFalse();
  });

  it('keeps the load error when no NAS path is readable or discovery fails', () => {
    component.paths = [];
    nas.getPaths.and.returnValue(of([{ id: 2, readable: false }]));
    component.retryLoad();
    expect(component.loadError).toBeTrue();

    expect(nas.getPaths).toHaveBeenCalledTimes(1);
    nas.getPaths.and.returnValue(throwError(() => new Error('offline')));
    component.retryLoad();
    expect(component.loadError).toBeTrue();
    expect(nas.getPaths).toHaveBeenCalledTimes(2);
  });

  it('shows a recoverable error when loading a NAS folder fails', () => {
    component.paths = [{ id: 8, readable: true } as any];
    component.currentView = 'folder';
    component.selectedPathId = 8;
    music.browse.and.returnValue(throwError(() => new Error('offline')));

    component.load();

    expect(component.loadError).toBeTrue();
    expect(component.pageLoading).toBeFalse();
  });

  it('loads folder tracks, resets loading state, and updates pagination totals', () => {
    const tracks = [{ path: 'folder/song.mp3', title: 'Song' }];
    music.browse.and.returnValue(of({ items: tracks, totalTracks: 14 }));
    component.currentView = 'folder';
    component.selectedPathId = 8;
    component.currentSubPath = 'folder';
    spyOn<any>(component, 'fetchCoversForVisible');
    spyOn<any>(component, 'checkPendingAutoPlay');

    component.load();

    expect(music.browse).toHaveBeenCalledWith(8, 'folder', 0, (component as any).PAGE_SIZE);
    expect(component.items).toEqual(tracks as any);
    expect(component.totalTracks).toBe(14);
    expect(component.pageLoading).toBeFalse();
  });

  it('maps favorites to tracks with availability and applies title sorting', () => {
    music.getFavorites.and.returnValue(of([
      { title: 'Zulu', trackPath: 'z.mp3', artist: 'Artist', nasPathId: 4, available: false },
      { title: 'Alpha', trackPath: 'a.mp3', artist: 'Artist', nasPathId: 5, available: true }
    ]));
    component.currentView = 'liked';
    component.likedSortBy = 'title';
    spyOn<any>(component, 'checkPendingAutoPlay');

    component.load();

    expect(component.items.map(track => track.title)).toEqual(['Alpha', 'Zulu']);
    expect(component.items[1].available).toBeFalse();
    expect(component.items[0].nasPathId).toBe(5);
    expect(music.fetchCoverIfNeeded).toHaveBeenCalledTimes(2);
  });

  it('maps playback history with its availability and played-at timestamp', () => {
    music.getHistory.and.returnValue(of([{
      title: 'Unavailable', trackPath: 'missing.mp3', nasPathId: 6,
      available: false, durationSeconds: 91, playedAt: '2026-10-10T10:00:00'
    }]));
    component.currentView = 'history';
    spyOn<any>(component, 'checkPendingAutoPlay');

    component.load();

    expect(music.getHistory).toHaveBeenCalledWith(50);
    expect(component.items[0].available).toBeFalse();
    expect(component.items[0].duration).toBe(91);
    expect(component.items[0].lastModified).toBe('2026-10-10T10:00:00');
  });

  it('restores playlist tracks and triggers visible cover lookups', () => {
    component.currentView = 'playlist';
    component.selectedPlaylist = { tracks: [{
      title: 'Saved', trackPath: 'saved.mp3', durationSeconds: 20, nasPathId: 3
    }] } as any;

    component.load();

    expect(component.items[0]).toEqual(jasmine.objectContaining({
      title: 'Saved', path: 'saved.mp3', duration: 20, source: 'nas'
    }));
    expect(component.totalTracks).toBe(1);
    expect(music.fetchCoverIfNeeded).toHaveBeenCalledWith(component.items[0]);
  });

  it('sets a recoverable error when home browsing fails', () => {
    music.browse.and.returnValue(throwError(() => new Error('offline')));
    music.getHistory.and.returnValue(of([]));
    component.currentView = 'home';
    component.paths = [{ id: 8, readable: true } as any];

    component.load();

    expect(music.browse).toHaveBeenCalledWith(8, '', 0, (component as any).PAGE_SIZE);
    expect(component.loadError).toBeTrue();
  });
});
