import { ModernPlaylistsComponent } from './modern-playlists.component';

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
