import { NowPlayingPanelComponent } from './now-playing-panel.component';

describe('Desktop icon layout', () => {
  let component: NowPlayingPanelComponent;
  let height: jasmine.Spy;
  const storageKey = 'everload.windows.desktopIcons';
  let saved: string | null;

  beforeEach(() => {
    saved = localStorage.getItem(storageKey);
    localStorage.removeItem(storageKey);
    spyOnProperty(window, 'innerWidth', 'get').and.returnValue(980);
    height = spyOnProperty(window, 'innerHeight', 'get').and.returnValue(700);
    component = Object.create(NowPlayingPanelComponent.prototype);
    Object.assign(component, { desktopTaskbarHeight: 40, desktopIconPositions: {
      equalizer: { x: 18, y: 528 }, snake: { x: 18, y: 570 }, explorer: { x: 118, y: 24 }
    } });
  });

  afterEach(() => {
    if (saved === null) localStorage.removeItem(storageKey);
    else localStorage.setItem(storageKey, saved);
  });

  const assertSeparated = (positions: Record<string, { x: number; y: number }>) => {
    const values = Object.values(positions);
    values.forEach((pos, i) => values.slice(i + 1).forEach(other => {
      expect(Math.abs(pos.x - other.x) >= 92 || Math.abs(pos.y - other.y) >= 74).toBeTrue();
    }));
  };

  it('separates the default Snake and equalizer icons without saved preferences', () => {
    (component as any).loadDesktopIconPositions();
    assertSeparated(component.desktopIconPositions);
  });

  it('repairs saved collisions while retaining valid positions', () => {
    localStorage.setItem(storageKey, JSON.stringify({ snake: { x: 18, y: 528 } }));
    (component as any).loadDesktopIconPositions();
    assertSeparated(component.desktopIconPositions);
    expect(component.desktopIconPositions.explorer).toEqual({ x: 118, y: 24 });
  });

  it('arranges defaults even when storage cannot be read', () => {
    spyOn(Storage.prototype, 'getItem').and.throwError('Storage blocked');
    (component as any).loadDesktopIconPositions();
    assertSeparated(component.desktopIconPositions);
  });

  it('rejects partial overlaps, not only identical coordinates', () => {
    expect((component as any).desktopIconMoveIsFree(component.desktopIconPositions, ['snake'])).toBeFalse();
  });

  it('redistributes icons above the taskbar when the viewport gets shorter', () => {
    height.and.returnValue(480);
    spyOn<any>(component, 'clampAllWindows');
    component.onWindowResize();
    assertSeparated(component.desktopIconPositions);
    Object.values(component.desktopIconPositions).forEach(pos => expect(pos.y + 74).toBeLessThanOrEqual(440));
  });
});
