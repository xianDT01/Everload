import { ComponentFixture, TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { DeckModeComponent } from './deck-mode.component';
import { MusicService } from '../../../services/music.service';
import { NasService } from '../../../services/nas.service';
import { AuthService } from '../../../services/auth.service';
import { MidiService } from '../../../services/midi.service';
import { of } from 'rxjs';

describe('DeckModeComponent pitch controls', () => {
  let fixture: ComponentFixture<DeckModeComponent>;
  let component: DeckModeComponent;
  let playerA: jasmine.SpyObj<any>;
  let playerB: jasmine.SpyObj<any>;

  beforeEach(async () => {
    playerA = jasmine.createSpyObj('deckAPlayer', ['setPlaybackRate', 'pause', 'seek', 'setLoop', 'disableLoop']);
    playerB = jasmine.createSpyObj('deckBPlayer', ['setPlaybackRate', 'pause', 'seek', 'setLoop', 'disableLoop']);

    await TestBed.configureTestingModule({
      imports: [FormsModule, HttpClientTestingModule, TranslateModule.forRoot()],
      declarations: [DeckModeComponent],
      providers: [
        { provide: MusicService, useValue: { deckAPlayer: playerA, deckBPlayer: playerB } },
        { provide: NasService, useValue: {} },
        { provide: AuthService, useValue: {} },
        { provide: MidiService, useValue: { isSupported: true, getActivePresetName: () => null,
          getMappingForAction: () => null, status$: of('READY'), lastMessage$: of('') } }
      ]
    }).compileComponents();

    // Isolate rendered controls from NAS loading, MIDI and the spectrum loop.
    spyOn(DeckModeComponent.prototype, 'ngOnInit');
    spyOn(DeckModeComponent.prototype, 'ngAfterViewInit');
    fixture = TestBed.createComponent(DeckModeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function slider(deck: 'A' | 'B'): HTMLInputElement {
    return fixture.nativeElement.querySelector(`.deck-${deck.toLowerCase()} .pitch-slider`);
  }

  for (const deck of ['A', 'B'] as const) {
    it(`allows deck ${deck} pitch to move through the full +/-10 percent range`, () => {
      const input = slider(deck);
      expect(input.min).toBe('-0.1');
      expect(input.max).toBe('0.1');
      expect(input.step).toBe('0.001');
      expect(input.valueAsNumber).toBe(0);

      const player = deck === 'A' ? playerA : playerB;
      const otherPlayer = deck === 'A' ? playerB : playerA;
      for (const value of [-0.1, -0.05, 0, 0.05, 0.1]) {
        input.value = String(value);
        input.dispatchEvent(new Event('input', { bubbles: true }));
        fixture.detectChanges();

        expect(input.valueAsNumber).toBe(value);
        expect(deck === 'A' ? component.pitchA : component.pitchB).toBe(value);
        expect(player.setPlaybackRate).toHaveBeenCalledWith(1 + value);
      }
      expect(otherPlayer.setPlaybackRate).not.toHaveBeenCalled();
    });

    it(`resets deck ${deck} pitch to the centre when its label is double-clicked`, () => {
      const input = slider(deck);
      input.value = '0.075';
      input.dispatchEvent(new Event('input', { bubbles: true }));
      fixture.detectChanges();

      const label = fixture.nativeElement.querySelector(`.deck-${deck.toLowerCase()} .pitch-label`);
      label.dispatchEvent(new MouseEvent('dblclick', { bubbles: true }));
      fixture.detectChanges();

      expect(input.valueAsNumber).toBe(0);
      expect(deck === 'A' ? component.pitchA : component.pitchB).toBe(0);
      expect((deck === 'A' ? playerA : playerB).setPlaybackRate).toHaveBeenCalledWith(1);
    });
  }

  function loadedTrack(): void {
    component.stateA = {
      playing: false, currentTime: 10, duration: 200, volume: 1,
      currentTrack: { path: 'test.mp3', source: 'nas', bpm: 120 } as any,
      pathId: 1, loading: false, error: null, playbackRate: 1.1,
      loopActive: false, loopStart: 0, loopEnd: 0
    };
  }

  it('uses original track tempo for loops even when pitch is raised', () => {
    loadedTrack();
    component.pitchA = 0.1;
    component.toggleLoop('A', 8);
    expect(playerA.setLoop).toHaveBeenCalledWith(10, 14, true);
  });

  it('resizes a loop around its original start', () => {
    loadedTrack();
    component.stateA = { ...component.stateA!, loopActive: true, loopStart: 8, loopEnd: 10 };
    component.resizeLoop('A', 2);
    expect(playerA.setLoop).toHaveBeenCalledWith(8, 12, true);
    expect(component.performance.A.loopBeats).toBe(8);
  });

  it('exits an active loop before beat jumping', () => {
    loadedTrack();
    component.stateA!.loopActive = true;
    component.beatJump('A', 1);
    expect(playerA.disableLoop).toHaveBeenCalled();
    expect(playerA.seek).toHaveBeenCalledWith(12);
    expect(playerB.seek).not.toHaveBeenCalled();
  });

  it('clamps a beat jump at track boundaries', () => {
    loadedTrack();
    component.stateA!.currentTime = 1;
    component.beatJump('A', -1);
    expect(playerA.seek).toHaveBeenCalledWith(0);
  });

  it('does not invent a tempo for unknown songs', () => {
    loadedTrack();
    component.stateA!.currentTrack!.bpm = 0;
    component.toggleLoop('A', 8);
    component.beatJump('A', 1);
    expect(playerA.setLoop).not.toHaveBeenCalled();
    expect(playerA.seek).not.toHaveBeenCalled();
  });

  it('converts tapped playback tempo back to source tempo', () => {
    loadedTrack();
    component.tapBpm('A', 1000);
    component.tapBpm('A', 1500);
    component.tapBpm('A', 2000);
    expect(component.baseBpm('A')).toBeCloseTo(120 / 1.1);
  });

  it('does not exceed the physical pitch range on BPM sync', () => {
    component.setPitchValue('A', 2);
    expect(component.pitchA).toBe(0.1);
    expect(playerA.setPlaybackRate).toHaveBeenCalledWith(1.1);
  });

  it('marks embedded YouTube audio as unavailable for effects and signal analysis', () => {
    loadedTrack();
    component.stateA!.currentTrack!.source = 'youtube';
    expect(component.effectsAvailable('A')).toBeFalse();
  });

  it('disables the visible pitch and echo controls for embedded YouTube tracks', () => {
    loadedTrack();
    component.stateA!.currentTrack!.source = 'youtube';
    fixture.detectChanges();
    expect(slider('A').disabled).toBeTrue();
    const echo: HTMLInputElement = fixture.nativeElement.querySelector('#echo-A');
    expect(echo.disabled).toBeTrue();
    component.setPitchValue('A', 0.05);
    expect(playerA.setPlaybackRate).not.toHaveBeenCalled();
  });

  it('snaps new hot cues when quantization is enabled', () => {
    loadedTrack();
    component.stateA!.currentTime = 10.22;
    component.performance.A.quantize = true;
    component.setHotCue('A', 0);
    expect(component.hotCuesA[0]).toBe(10);
  });

  it('resets cues and tapped tempo when a different NAS root loads the same path', () => {
    loadedTrack();
    (component as any).refreshPerformance('A', component.stateA);
    component.hotCuesA = [10];
    component.performance.A.bpm = 125;
    (component as any).refreshPerformance('A', { ...component.stateA, pathId: 2 });
    expect(component.hotCuesA).toEqual([]);
    expect(component.performance.A.bpm).toBe(0);
  });

  it('preserves performance state across normal progress updates', () => {
    loadedTrack();
    (component as any).refreshPerformance('A', component.stateA);
    component.hotCuesA = [10];
    (component as any).refreshPerformance('A', { ...component.stateA, currentTime: 15 });
    expect(component.hotCuesA).toEqual([10]);
  });

  it('does not run transport commands on a MIDI release with non-zero release velocity', () => {
    const toggle = spyOn(component, 'togglePlay');
    component.handleMidiAction({ actionId: 'PLAY_A', rawValue: 64, normalizedValue: 0, pressed: false });
    expect(toggle).not.toHaveBeenCalled();
  });

  it('routes learned MIDI hot cues to the correct deck', () => {
    loadedTrack();
    component.handleMidiAction({ actionId: 'HOTCUE_1_A', rawValue: 127, normalizedValue: 1, pressed: true });
    expect(component.hotCuesA[0]).toBe(10);
    expect(component.hotCuesB).toEqual([]);
  });

  it('does not apply stale NAS pitch when tapping tempo for embedded YouTube', () => {
    loadedTrack();
    component.stateA!.currentTrack!.source = 'youtube';
    component.pitchA = 0.1;
    component.tapBpm('A', 1000);
    component.tapBpm('A', 1500);
    component.tapBpm('A', 2000);
    expect(component.getCurrentBpm('A')).toBe(120);
  });

  it('keeps the selected MIDI port visible when reconnect recreates its option', fakeAsync(() => {
    component.showMidiConfig = true;
    const device = { id: 'test-port', name: 'Controller', manufacturer: 'Test' };
    component.midiDevices = [device];
    component.activeMidiDeviceId = device.id;
    fixture.detectChanges(); flushMicrotasks(); fixture.detectChanges();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('.md-select');
    expect(select.value).toBe(device.id);
    component.midiDevices = []; component.activeMidiDeviceId = null;
    fixture.detectChanges(); flushMicrotasks(); fixture.detectChanges();
    expect(select.value).toBe('');
    component.midiDevices = [device]; component.activeMidiDeviceId = device.id;
    fixture.detectChanges(); flushMicrotasks(); fixture.detectChanges();
    expect(select.value).toBe(device.id);
  }));

  it('switches skins without replacing player state, cues or queues', () => {
    loadedTrack();
    const state = component.stateA;
    const queue = component.queueA;
    component.hotCuesA = [10];
    component.setSkin('classic');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.pro-skin')).toBeNull();
    component.setSkin('pro');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.pro-skin')).not.toBeNull();
    expect(component.stateA).toBe(state);
    expect(component.queueA).toBe(queue);
    expect(component.hotCuesA).toEqual([10]);
    expect(playerA.pause).not.toHaveBeenCalled();
    expect(playerB.pause).not.toHaveBeenCalled();
  });

  it('renders five Pro transport commands while preserving Classic controls', () => {
    for (const deck of ['a', 'b']) {
      const panel = fixture.nativeElement.querySelector(`.deck-${deck}`);
      expect(panel.querySelectorAll('.pro-transport button').length).toBe(5);
      expect(panel.querySelector('.pro-deck-letter').textContent.trim()).toBe(deck.toUpperCase());
      expect(panel.querySelector('.pro-deck-tools')).not.toBeNull();
    }
    component.setSkin('classic');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.pro-transport')).toBeNull();
    expect(fixture.nativeElement.querySelector('.deck-a .btn-reset')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.deck-b .btn-queue-toggle')).not.toBeNull();
  });

  it('opens the Pro queue and keeps positional sync and reset available', () => {
    const panel = fixture.nativeElement.querySelector('.deck-a .pro-deck-tools');
    const buttons = panel.querySelectorAll('button');
    buttons[0].click();
    fixture.detectChanges();
    expect(component.showQueueA).toBeTrue();
    expect(component.showQueueB).toBeFalse();
    const reset = spyOn(component, 'resetTrack');
    buttons[1].click();
    expect(reset).toHaveBeenCalledWith('A');
    expect(buttons[2].disabled).toBeTrue();
  });

  it('applies the dark palette independently of either skin without interrupting playback', () => {
    loadedTrack();
    const state = component.stateA;
    const queue = component.queueA;
    const toggle: HTMLInputElement = fixture.nativeElement.querySelector('.dj-theme-toggle input');
    toggle.checked = true;
    toggle.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
    expect(component.darkTheme).toBeTrue();
    expect(fixture.nativeElement.querySelector('.pro-skin.dark-skin')).not.toBeNull();
    component.setSkin('classic');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.dark-skin')).not.toBeNull();
    expect(toggle.checked).toBeTrue();
    component.setDarkTheme(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.dark-skin')).toBeNull();
    expect(component.skin).toBe('classic');
    expect(component.stateA).toBe(state);
    expect(component.queueA).toBe(queue);
    expect(playerA.pause).not.toHaveBeenCalled();
    expect(playerB.pause).not.toHaveBeenCalled();
  });

  it('persists dark and graphite preferences even when browser storage is unavailable', () => {
    const save = spyOn(localStorage, 'setItem');
    component.setDarkTheme(true);
    expect(save).toHaveBeenCalledWith('everload.dj.theme', 'dark');
    component.setDarkTheme(false);
    expect(save).toHaveBeenCalledWith('everload.dj.theme', 'graphite');
    save.and.throwError('Storage blocked');
    expect(() => component.setDarkTheme(true)).not.toThrow();
    expect(component.darkTheme).toBeTrue();
  });

  it('anchors each seek input to its visible bar in both skins and preserves fractional seconds', () => {
    loadedTrack();
    component.stateB = { ...component.stateA! };
    for (const skin of ['classic', 'pro'] as const) {
      component.setSkin(skin);
      fixture.detectChanges();
      for (const deck of ['A', 'B'] as const) {
        const input: HTMLInputElement = fixture.nativeElement.querySelector(`.deck-${deck.toLowerCase()} .progress-seek`);
        expect(input.parentElement!.classList.contains('progress-bar')).toBeTrue();
        expect(input.min).toBe('0');
        expect(input.step).toBe('0.01');
        expect(input.disabled).toBeFalse();
        input.value = '123.45';
        input.dispatchEvent(new Event('input', { bubbles: true }));
        expect((deck === 'A' ? playerA : playerB).seek).toHaveBeenCalledWith(123.45);
      }
    }
  });

  it('disables seeking until a duration is available', () => {
    for (const input of fixture.nativeElement.querySelectorAll('.progress-seek')) {
      expect(input.disabled).toBeTrue();
    }
  });

  it('remembers the skin and tolerates blocked browser storage', () => {
    const save = spyOn(localStorage, 'setItem');
    component.setSkin('classic');
    expect(save).toHaveBeenCalledWith('everload.dj.skin', 'classic');
    save.and.throwError('Storage blocked');
    expect(() => component.setSkin('pro')).not.toThrow();
    expect(component.skin).toBe('pro');
  });
});
