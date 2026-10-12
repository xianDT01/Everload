import { NowPlayingPanelComponent } from './now-playing-panel.component';

describe('NowPlayingPanelComponent unavailable NAS tracks', () => {
  let component: NowPlayingPanelComponent;
  let music: jasmine.SpyObj<any>;

  beforeEach(() => {
    music = jasmine.createSpyObj('MusicService', ['setQueue']);
    component = Object.create(NowPlayingPanelComponent.prototype);
    Object.assign(component, {
      musicService: music,
      state: { pathId: 9 },
      winampQueue: { pathId: 12, tracks: [] },
      selectedPlaylist: {
        tracks: [
          { title: 'Missing', trackPath: 'missing.mp3', available: false },
          { title: 'First', trackPath: 'first.mp3', nasPathId: 3, available: true },
          { title: 'Second', trackPath: 'second.mp3', nasPathId: 8, available: true }
        ]
      }
    });
  });

  it('starts a playlist at its first playable track and retains its NAS path', () => {
    component.playSelectedPlaylist();

    const [pathId, tracks, index] = music.setQueue.calls.mostRecent().args;
    expect(pathId).toBe(3);
    expect(index).toBe(0);
    expect(tracks.map((track: any) => track.path)).toEqual(['first.mp3', 'second.mp3']);
    expect(tracks[0].source).toBe('nas');
    expect(tracks[0].available).toBeTrue();
  });

  it('ignores an unavailable playlist click and skips it when starting later', () => {
    component.playPlaylistTrack(0);
    expect(music.setQueue).not.toHaveBeenCalled();

    component.playPlaylistTrack(2);

    const [pathId, tracks, index] = music.setQueue.calls.mostRecent().args;
    expect(pathId).toBe(8);
    expect(index).toBe(1);
    expect(tracks).toHaveSize(2);
  });

  it('does not start a playlist that has no available tracks', () => {
    component.selectedPlaylist = { tracks: [{ title: 'Missing', available: false }] };

    component.playPlaylist(component.selectedPlaylist);

    expect(music.setQueue).not.toHaveBeenCalled();
  });

  it('refuses unavailable history and plays available history from its own NAS', () => {
    component.playHistoryItem({ available: false, nasPathId: 2, trackPath: 'missing.mp3' });
    expect(music.setQueue).not.toHaveBeenCalled();

    component.playHistoryItem({ available: true, nasPathId: 8, trackPath: 'history.mp3', title: 'History' });

    expect(music.setQueue).toHaveBeenCalledWith(8, [jasmine.objectContaining({
      path: 'history.mp3', title: 'History', nasPathId: 8, source: 'nas'
    })], 0);
  });
});
