import { ElementRef } from '@angular/core';
import { NowPlayingPanelComponent } from './now-playing-panel.component';

describe('Desktop window activation', () => {
  let component: NowPlayingPanelComponent;
  let host: HTMLElement;
  let repaint: jasmine.Spy;

  beforeEach(() => {
    host = document.createElement('div');
    host.innerHTML = '<section data-desktop-window="explorer"><button>Action</button></section>';
    repaint = jasmine.createSpy('detectChanges');
    component = Object.create(NowPlayingPanelComponent.prototype);
    Object.assign(component, {
      host: new ElementRef(host), cdr: { detectChanges: repaint },
      musicService: { nowPlayingPanelOpen: true },
      activeDesktopWindow: 'player', desktopStartOpen: true,
      desktopWindowZOrder: { player: 9500, explorer: 9501, manager: 9499,
        messenger: 9498, youtube: 9497, browser: 9496, minesweeper: 9495,
        calculator: 9494, notepad: 9493, equalizer: 9492, snake: 9491 }
    });
    spyOnProperty(window, 'innerWidth', 'get').and.returnValue(1440);
    host.addEventListener('pointerdown', event => component.onDesktopWindowActivate(event), true);
    host.addEventListener('focusin', event => component.onDesktopWindowActivate(event), true);
  });

  it('raises the chosen window while retaining the relative order of the others', () => {
    component.focusWindow('notepad');
    const order = (component as any).desktopWindowZOrder;
    expect(order.notepad).toBe(9510);
    expect(order.player).toBeLessThan(order.explorer);
    expect(order.manager).toBeLessThan(order.player);
    expect(component.activeDesktopWindow).toBe('notepad');
    expect(component.desktopStartOpen).toBeFalse();
  });

  it('keeps windows below desktop menus even after repeated activation', () => {
    for (let i = 0; i < 200; i++) {
      component.focusWindow(i % 2 ? 'player' : 'explorer');
    }
    const values = Object.values((component as any).desktopWindowZOrder) as number[];
    expect(Math.max(...values)).toBe(9510);
    expect(new Set(values).size).toBe(11);
  });

  it('captures activation before a child control stops propagation', () => {
    const button = host.querySelector('button')!;
    button.addEventListener('pointerdown', event => event.stopPropagation());
    button.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    expect(component.activeDesktopWindow).toBe('explorer');
    expect(repaint).toHaveBeenCalledTimes(1);
  });

  it('activates a window through keyboard focus without repeatedly repainting it', () => {
    const button = host.querySelector('button')!;
    button.dispatchEvent(new Event('focusin', { bubbles: true }));
    button.dispatchEvent(new Event('focusin', { bubbles: true }));
    expect(component.activeDesktopWindow).toBe('explorer');
    expect(repaint).toHaveBeenCalledTimes(1);
  });

  it('ignores background and unrecognized window targets', () => {
    host.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    host.querySelector('section')!.dataset['desktopWindow'] = 'invalid';
    host.querySelector('button')!.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    expect(repaint).not.toHaveBeenCalled();
    expect(component.activeDesktopWindow).toBe('player');
  });

  it('does not activate windows when the desktop is closed', () => {
    (component as any).musicService.nowPlayingPanelOpen = false;
    host.querySelector('button')!.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    expect(repaint).not.toHaveBeenCalled();
  });

  it('brings a background window forward from the taskbar, then minimizes and restores it', () => {
    component.calculatorOpen = true;
    component.calculatorMinimized = false;
    component.toggleTaskWindow('calculator');
    expect(component.calculatorMinimized).toBeFalse();
    expect(component.activeDesktopWindow).toBe('calculator');
    component.toggleTaskWindow('calculator');
    expect(component.calculatorMinimized).toBeTrue();
    component.toggleTaskWindow('calculator');
    expect(component.calculatorMinimized).toBeFalse();
    expect(component.activeDesktopWindow).toBe('calculator');
  });

  it('focuses the top remaining visible window when closing, skipping minimized windows', () => {
    component.notepadOpen = true;
    component.desktopExplorerOpen = true;
    component.focusWindow('notepad');
    component.focusWindow('explorer');
    component.explorerMinimized = true;
    component.openCalculator();
    component.closeCalculator();
    expect(component.activeDesktopWindow).toBe('notepad');
  });
});

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

  it('keeps a readable size while allowing placement near the right and bottom edges', () => {
    const style = component.desktopPanelStyle;
    expect(parseFloat(style['width'])).toBe(503);
    expect(parseFloat(style['left'])).toBeGreaterThan(1400);
    expect(parseFloat(style['top'])).toBe(950);
    expect(parseFloat(style['left']) + 132).toBeLessThanOrEqual(1912);
    expect(parseFloat(style['top']) + 27).toBeLessThanOrEqual(1032);
  });

  it('keeps enough of the title bar reachable when dragged far beyond every edge', () => {
    const size = (component as any).getWinampWindowSize();
    const leftTop = (component as any).clampPlayerWindowPosition(-5000, -5000, size);
    const rightBottom = (component as any).clampPlayerWindowPosition(5000, 5000, size);
    expect(leftTop.x).toBeLessThan(0);
    expect(leftTop.x + size.width).toBeGreaterThanOrEqual(132);
    expect(leftTop.y).toBe(8);
    expect(rightBottom.x + 132).toBeLessThanOrEqual(1912);
    expect(rightBottom.y + 27).toBeLessThanOrEqual(1032);
  });

  it('does not snap back inside the desktop when beginning another drag near the bottom', () => {
    component.desktopPanelPosition = { x: 1400, y: 900 };
    component.activeDesktopWindow = 'player';
    component.beginWindowDrag({ clientX: 1430, clientY: 910, preventDefault: () => {} } as MouseEvent, 'player');
    expect(component.desktopPanelPosition).toEqual({ x: 1400, y: 900 });
    expect((component as any).dragState).toEqual({ target: 'player', offsetX: 30, offsetY: 10 });
  });

  it('preserves whole-window bounds for the other player skins', () => {
    component.playerSkin = 'wmp';
    const position = (component as any).clampPlayerWindowPosition(1800, 950, { width: 1000, height: 690 });
    expect(position).toEqual({ x: 912, y: 342 });
  });

  it('keeps the title bar above a taller taskbar after shrinking the viewport', () => {
    viewportHeight.and.returnValue(768);
    component.desktopTaskbarHeight = 80;
    (component as any).fitPlayerWindowToSkin();
    const style = component.desktopPanelStyle;
    expect(parseFloat(style['top']) + 24).toBeLessThanOrEqual(680);
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
