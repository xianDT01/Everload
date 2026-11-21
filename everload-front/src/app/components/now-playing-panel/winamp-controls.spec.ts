import { NowPlayingPanelComponent } from './now-playing-panel.component';
import { ElementRef } from '@angular/core';

describe('Winamp controls', () => {
  let component: NowPlayingPanelComponent;
  let player: jasmine.SpyObj<any>;
  beforeEach(() => {
    component = Object.create(NowPlayingPanelComponent.prototype);
    player = jasmine.createSpyObj('player', ['seek', 'setWinampEq', 'setBalance']);
    Object.assign(component, { musicService: { mainPlayer: player }, state: { duration: 200 },
      waEqBands: Array(10).fill(0), waPreamp: 0, waEqOn: true, waShaded: false, waImportedPresets: [],
      waEqPresets: [{ id: 'flat', bands: Array(10).fill(0) }] });
  });
  it('updates individual bands without changing the adjacent band', () => {
    component.setWaEqBand(1, 6);
    expect(component.waEqBands.slice(0, 3)).toEqual([0, 6, 0]);
    expect(player.setWinampEq).toHaveBeenCalledWith(component.waEqBands, 0, true);
    component.toggleWaEq();
    expect(player.setWinampEq).toHaveBeenCalledWith(component.waEqBands, 0, false);
  });
  it('preserves edited gains while bypassed and restores them when enabled', () => {
    component.toggleWaEq(); component.setWaPreamp(-4); component.setWaEqBand(8, 5);
    component.toggleWaEq();
    expect(player.setWinampEq).toHaveBeenCalledWith(component.waEqBands, -4, true);
    expect(component.waEqBands[8]).toBe(5);
  });
  it('uses rendered coordinates for scaled progress bars and clamps outside clicks', () => {
    const bar = { getBoundingClientRect: () => ({ left: 100, width: 400 }) };
    component.onSeek({ currentTarget: bar, clientX: 300, offsetX: 10 } as any);
    expect(player.seek).toHaveBeenCalledWith(100);
    component.onSeek({ currentTarget: bar, clientX: 900 } as any);
    expect(player.seek).toHaveBeenCalledWith(200);
  });
  it('bounds seek and balance and ignores invalid numbers', () => {
    component.seekWa(1000); expect(player.seek).toHaveBeenCalledWith(200);
    component.seekWa(NaN); expect(player.seek.calls.count()).toBe(1);
    component.setWaBalance(-500); expect(player.setBalance).toHaveBeenCalledWith(-1);
    component.setWaEqBand(20, 4); component.setWaPreamp(NaN);
    expect(player.setWinampEq).not.toHaveBeenCalled();
  });
  it('shrinks to a reachable compact title bar without changing visibility preferences', () => {
    component.waEqVisible = true; component.waPlVisible = true;
    spyOn<any>(component, 'fitPlayerWindowToSkin');
    component.toggleWaShade();
    expect((component as any).getWinampContentHeight()).toBe(26);
    expect(component.waEqVisible).toBeTrue(); expect(component.waPlVisible).toBeTrue();
  });

  it('closes the preset menu on outside activation, not on its own controls', () => {
    const menu = document.createElement('details'), control = document.createElement('button');
    menu.appendChild(control); menu.open = true;
    component.waPresetMenu = new ElementRef(menu);
    component.closeWaPresetMenuOutside({ target: control } as unknown as Event);
    expect(menu.open).toBeTrue();
    component.closeWaPresetMenuOutside({ target: document.createElement('div') } as unknown as Event);
    expect(menu.open).toBeFalse();
  });

  it('does not reserve space for the hidden EQF actions', () => {
    component.waEqVisible = true; component.waPlVisible = true;
    expect((component as any).getWinampContentHeight()).toBe(449);
  });
});
