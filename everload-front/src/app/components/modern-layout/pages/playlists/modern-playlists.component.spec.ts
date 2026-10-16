import { ModernPlaylistsComponent } from './modern-playlists.component';
import { fakeAsync, tick } from '@angular/core/testing';
import { Subject } from 'rxjs';

describe('ModernPlaylistsComponent playlist playback', () => {
  let component: ModernPlaylistsComponent;
  let music: jasmine.SpyObj<any>;

  beforeEach(() => {
    music = jasmine.createSpyObj('MusicService', ['setQueue']);
    component = Object.create(ModernPlaylistsComponent.prototype);
    Object.assign(component, {
      music,
      state: { pathId: 9 },
      selectedPlaylist: {
        tracks: [
          { title: 'Missing', trackPath: 'missing.mp3', available: false },
          { title: 'First', trackPath: 'first.mp3', nasPathId: 3, available: true },
          { title: 'Second', trackPath: 'second.mp3', nasPathId: 8, available: true },
        ]
      }
    });
  });

  it('omits unavailable tracks and starts at the selected playable track NAS', () => {
    component.playFrom(2);

    const [pathId, queue, index] = music.setQueue.calls.mostRecent().args;
    expect(pathId).toBe(8);
    expect(index).toBe(1);
    expect(queue.map((track: any) => track.path)).toEqual(['first.mp3', 'second.mp3']);
  });

  it('ignores a direct play request for a track marked unavailable', () => {
    component.playFrom(0);

    expect(music.setQueue).not.toHaveBeenCalled();
  });

  it('does nothing when the playlist has no playable tracks', () => {
    component.selectedPlaylist = { tracks: [{ title: 'Missing', available: false }] };

    (component as any).playFromPlaylist(component.selectedPlaylist, 0);

    expect(music.setQueue).not.toHaveBeenCalled();
  });

  it('clamps a stale selected index to the final playable track', () => {
    (component as any).playFromPlaylist(component.selectedPlaylist, 99);

    const [, queue, index] = music.setQueue.calls.mostRecent().args;
    expect(index).toBe(1);
    expect(queue).toHaveSize(2);
  });
});

describe('ModernPlaylistsComponent track search', () => {
  let component: ModernPlaylistsComponent;
  let search: jasmine.Spy;

  beforeEach(() => {
    search = jasmine.createSpy('search');
    component = Object.create(ModernPlaylistsComponent.prototype);
    Object.assign(component, { music: { search }, state: { pathId: 9 },
      searchQuery: '', searchResults: [], searching: false });
  });

  afterEach(() => component.ngOnDestroy());

  it('cancels stale results immediately, including during the debounce interval', fakeAsync(() => {
    const oldResults = new Subject<any[]>();
    const newResults = new Subject<any[]>();
    search.and.returnValues(oldResults, newResults);
    component.searchQuery = 'old';
    component.doSearch();
    component.searchQuery = '  Tacata  ';
    component.onSearchInput();
    oldResults.next([{ path: 'old.mp3' }]);
    expect(component.searchResults).toEqual([]);
    tick(249);
    expect(search).toHaveBeenCalledTimes(1);
    tick(1);
    expect(search).toHaveBeenCalledWith(9, undefined, 'Tacata', 50);
    newResults.next([{ path: 'tacata.mp3' }]);
    expect(component.searchResults[0].path).toBe('tacata.mp3');
    expect(component.searching).toBeFalse();
  }));

  it('clearing the input cancels pending work and removes the loading state', fakeAsync(() => {
    component.searchQuery = 'Tacata';
    component.onSearchInput();
    component.searchQuery = ' ';
    component.onSearchInput();
    tick(500);
    expect(search).not.toHaveBeenCalled();
    expect(component.searchResults).toEqual([]);
    expect(component.searching).toBeFalse();
  }));

  it('switching playlists cancels requests from the previous playlist', () => {
    const results = new Subject<any[]>();
    search.and.returnValue(results);
    component.searchQuery = 'Tacata';
    component.doSearch();
    component.openPlaylist({ id: 2, tracks: [] });
    results.next([{ path: 'stale.mp3' }]);
    expect(component.searchResults).toEqual([]);
    expect(component.searching).toBeFalse();
  });
});
