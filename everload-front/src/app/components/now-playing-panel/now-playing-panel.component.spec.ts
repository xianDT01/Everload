import { NowPlayingPanelComponent } from './now-playing-panel.component';

describe('Winamp window sizing', () => {
  let component: NowPlayingPanelComponent;
  let viewportWidth: jasmine.Spy;
  let viewportHeight: jasmine.Spy;
  const storageKey = 'everload.windows.desktopSizing';
  let originalPreference: string | null;

  beforeEach(() => {
    originalPreference = localStorage.getItem(storageKey);
    viewportWidth = spyOnProperty(window, 'innerWidth', 'get').and.returnValue(1920);
    viewportHeight = spyOnProperty(window, 'innerHeight', 'get').and.returnValue(1080);
    component = Object.create(NowPlayingPanelComponent.prototype);
    Object.assign(component, {
      winampScale: 1.65, waEqVisible: true, waPlVisible: true,
      desktopTaskbarHeight: 40, playerSkin: 'winamp',
      desktopStartMenuSize: { width: 470, height: 500 },
      desktopPanelPosition: { x: 1800, y: 950 },
      desktopWindowZOrder: { player: 10 }
    });
  });

  afterEach(() => {
    if (originalPreference === null) localStorage.removeItem(storageKey);
    else localStorage.setItem(storageKey, originalPreference);
  });

  it('opens at a larger readable size and keeps all panels above the taskbar', () => {
    const style = component.desktopPanelStyle;
    expect(parseFloat(style['width'])).toBe(503);
    expect(parseFloat(style['left']) + parseFloat(style['width'])).toBeLessThanOrEqual(1912);
    expect(parseFloat(style['top']) + parseFloat(style['height'])).toBeLessThanOrEqual(1032);
  });

  it('fits a laptop viewport without losing the preferred size on a larger screen', () => {
    viewportHeight.and.returnValue(768);
    expect(component.effectiveWinampScale).toBeLessThan(1.65);
    expect(component.winampScale).toBe(1.65);
    viewportHeight.and.returnValue(1080);
    expect(component.effectiveWinampScale).toBe(1.65);
  });

  it('fits a narrow viewport and allows larger sizes when EQ and playlist are hidden', () => {
    viewportWidth.and.returnValue(390);
    expect(300 * component.effectiveWinampScale).toBeLessThanOrEqual(366);
    viewportWidth.and.returnValue(1920);
    component.winampScale = 2.5;
    component.waEqVisible = false;
    component.waPlVisible = false;
    expect(component.effectiveWinampScale).toBe(2.5);
  });

  it('upgrades the old default but preserves a manually chosen size and other preferences', () => {
    localStorage.setItem(storageKey, JSON.stringify({ winampScale: 1.1, taskbarHeight: 48 }));
    (component as any).loadDesktopSizing();
    expect(component.winampScale).toBe(1.65);
    expect(component.desktopTaskbarHeight).toBe(48);
    localStorage.setItem(storageKey, JSON.stringify({ winampScale: 1.4 }));
    (component as any).loadDesktopSizing();
    expect(component.winampScale).toBe(1.4);
    component.winampScale = 1.1;
    (component as any).saveDesktopSizing();
    (component as any).loadDesktopSizing();
    expect(component.winampScale).toBe(1.1);
  });

  it('shrinks when dragging left even if the pointer moves down', () => {
    component.desktopPanelPosition = { x: 8, y: 8 };
    (component as any).chromeResizeState = {
      kind: 'winamp', startX: 500, startY: 500, startScale: 1.65
    };
    (component as any).handleChromeResize({ clientX: 410, clientY: 510 });
    expect(component.winampScale).toBeCloseTo(1.35, 5);
    (component as any).saveDesktopSizing();
    expect(JSON.parse(localStorage.getItem(storageKey)!).winampScale).toBeCloseTo(1.35, 5);
  });
});

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
