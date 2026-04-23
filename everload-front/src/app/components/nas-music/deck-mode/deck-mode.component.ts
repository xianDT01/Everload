import { Component, OnInit, OnDestroy, AfterViewInit, HostListener, ViewChild, ElementRef, NgZone, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicMetadataDto, MusicService, PlayerState } from '../../../services/music.service';

interface QueueItem {
  track: MusicMetadataDto;
  pathId: number; // -1 para local/youtube
}

interface TreeNode {
  key: string;
  name: string;
  depth: number;
  expanded: boolean;
  loading: boolean;
  childrenLoaded: boolean;
  children: TreeNode[];
  source: 'nas' | 'local';
  isRoot: boolean;
  // NAS-specific
  pathId?: number;
  subPath?: string;
  // Local-specific
  localHandle?: any;        // FileSystemDirectoryHandle (FS API) | string (fallback root name)
  localVirtualPath?: string; // Only in fallback mode
  localFallbackMode?: boolean;
}
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../services/auth.service';
import { NasPath, NasService } from '../../../services/nas.service';
import { MidiService, MidiDevice } from '../../../services/midi.service';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-deck-mode',
  templateUrl: './deck-mode.component.html',
  styleUrls: ['./deck-mode.component.css']
})
export class DeckModeComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('spectrumA') canvasA?: ElementRef<HTMLCanvasElement>;
  @ViewChild('spectrumB') canvasB?: ElementRef<HTMLCanvasElement>;

  private animFrameId: number | null = null;
  private translate = inject(TranslateService);

  paths: NasPath[] = [];
  selectedPathId: number | null = null;
  currentSubPath = '';
  items: MusicMetadataDto[] = [];

  // ── Queue ─────────────────────────────────────────────────────────────────
  queueA: QueueItem[] = [];
  queueB: QueueItem[] = [];
  showQueueA = false;
  showQueueB = false;
  private prevPlayingA = false;
  private prevPlayingB = false;

  // ── Folder Tree ───────────────────────────────────────────────────────────
  treeRoots: TreeNode[] = [];
  visibleTreeNodes: TreeNode[] = [];
  treeSelectedKey: string | null = null;
  treeWidth = 210;

  stateA: PlayerState | null = null;
  stateB: PlayerState | null = null;

  crossValue = 0;   // -1 … 0 … +1
  volA = 1;
  volB = 1;
  muteA = false;
  muteB = false;

  eqA = { low: 0, mid: 0, high: 0 };
  eqB = { low: 0, mid: 0, high: 0 };

  pitchA = 0;
  pitchB = 0;
  
  filterA = 0; // -100 … 0 … 100
  filterB = 0;
  
  fxA = { level: 0, feedback: 0.5, time: 0.5 };
  fxB = { level: 0, feedback: 0.5, time: 0.5 };

  hotCuesA: number[] = [];
  hotCuesB: number[] = [];

  sessionHistory: MusicMetadataDto[] = [];
  private historyTimers: any = { A: null, B: null };

  browserTab: 'nas' | 'youtube' | 'midi' | 'local' | 'history' = 'nas';
  ytSearchQuery = '';
  ytSearchResults: any[] = [];
  ytSearching = false;
  ytDirectUrl = '';

  nasSearchQuery = '';
  nasSearchResults: MusicMetadataDto[] | null = null;
  nasSearchLoading = false;
  private nasSearchDebounce?: ReturnType<typeof setTimeout>;
  
  localRootHandle: any = null;
  localDirStack: any[] = [];
  localItems: MusicMetadataDto[] = [];
  localCurrentPathStr = '';
  localFsApiSupported = typeof (window as any) !== 'undefined' && typeof (window as any).showDirectoryPicker === 'function';
  localFallbackMode = false;
  private localFallbackAllFiles: File[] = [];
  private localFallbackCurrentPath = '';

  @ViewChild('localFileInput') localFileInputRef?: ElementRef<HTMLInputElement>;
  @ViewChild('vdjLayout') vdjLayoutRef?: ElementRef<HTMLElement>;

  selectedIndex: number = -1;

  // ── Vertical resize (decks / browser split) ───────────────────────────────
  private static readonly DECK_HEIGHT_KEY = 'ev_dj_deck_height_pct';
  deckHeightPct = 58; // % of total height given to .decks-section
  private resizeDragging = false;
  private resizeStartY = 0;
  private resizeStartPct = 0;

  private subs: Subscription[] = [];

  midiDevices: MidiDevice[] = [];
  activeMidiDeviceId: string | null = null;
  isMidiLearning = false;
  showMidiConfig = false;
  showHelp = false;
  midiAutoDetectMsg = '';
  private midiToastTimer: any = null;

  // Album art fetched from iTunes API (clave = track.path)
  coverOverrideMap = new Map<string, string>();
  private lastCoverPath = { A: '', B: '' };

  midiActions = [
    { id: 'VOL_A', label: 'MUSIC.DJ_MIDI_VOL', params: { deck: 'A' } },
    { id: 'VOL_B', label: 'MUSIC.DJ_MIDI_VOL', params: { deck: 'B' } },
    { id: 'CROSSFADER', label: 'MUSIC.DJ_MIDI_CROSSFADER' },
    { id: 'PLAY_A', label: 'MUSIC.DJ_MIDI_PLAY', params: { deck: 'A' } },
    { id: 'PLAY_B', label: 'MUSIC.DJ_MIDI_PLAY', params: { deck: 'B' } },
    { id: 'CUE_A', label: 'MUSIC.DJ_MIDI_CUE', params: { deck: 'A' } },
    { id: 'CUE_B', label: 'MUSIC.DJ_MIDI_CUE', params: { deck: 'B' } },
    { id: 'SYNC_A', label: 'MUSIC.DJ_MIDI_SYNC', params: { deck: 'A' } },
    { id: 'SYNC_B', label: 'MUSIC.DJ_MIDI_SYNC', params: { deck: 'B' } },
    { id: 'MUTE_A', label: 'MUSIC.DJ_MIDI_MUTE', params: { deck: 'A' } },
    { id: 'MUTE_B', label: 'MUSIC.DJ_MIDI_MUTE', params: { deck: 'B' } },
    { id: 'NEXT_A', label: 'MUSIC.DJ_MIDI_NEXT', params: { deck: 'A' } },
    { id: 'PREV_A', label: 'MUSIC.DJ_MIDI_PREV', params: { deck: 'A' } },
    { id: 'RESET_A', label: 'MUSIC.DJ_MIDI_RESET', params: { deck: 'A' } },
    { id: 'NEXT_B', label: 'MUSIC.DJ_MIDI_NEXT', params: { deck: 'B' } },
    { id: 'PREV_B', label: 'MUSIC.DJ_MIDI_PREV', params: { deck: 'B' } },
    { id: 'RESET_B', label: 'MUSIC.DJ_MIDI_RESET', params: { deck: 'B' } },
    { id: 'EQ_LOW_A', label: 'MUSIC.DJ_MIDI_EQ_LOW', params: { deck: 'A' } },
    { id: 'EQ_MID_A', label: 'MUSIC.DJ_MIDI_EQ_MID', params: { deck: 'A' } },
    { id: 'EQ_HIGH_A', label: 'MUSIC.DJ_MIDI_EQ_HIGH', params: { deck: 'A' } },
    { id: 'EQ_LOW_B', label: 'MUSIC.DJ_MIDI_EQ_LOW', params: { deck: 'B' } },
    { id: 'EQ_MID_B', label: 'MUSIC.DJ_MIDI_EQ_MID', params: { deck: 'B' } },
    { id: 'EQ_HIGH_B', label: 'MUSIC.DJ_MIDI_EQ_HIGH', params: { deck: 'B' } },
    { id: 'PITCH_A', label: 'MUSIC.DJ_MIDI_PITCH', params: { deck: 'A' } },
    { id: 'PITCH_B', label: 'MUSIC.DJ_MIDI_PITCH', params: { deck: 'B' } }
  ];

  constructor(
    public musicService: MusicService,
    private nasService: NasService,
    private http: HttpClient,
    private auth: AuthService,
    public midiService: MidiService,
    private ngZone: NgZone
  ) {}

  ngOnInit(): void {
    const saved = localStorage.getItem(DeckModeComponent.DECK_HEIGHT_KEY);
    if (saved) {
      const n = parseFloat(saved);
      if (n >= 20 && n <= 80) this.deckHeightPct = n;
    }
    this.applyVolumes();

    this.nasService.getPaths().subscribe(paths => {
      this.paths = paths;
      if (paths.length > 0) this.selectPath(paths[0].id);
      this.buildNasTree();
    });

    this.subs.push(
      this.musicService.deckAPlayer.state$.subscribe(s => {
        this.stateA = s;
        this.checkQueueAdvance('A', s);
        if (s.currentTrack?.path !== this.lastCoverPath.A) {
          this.lastCoverPath.A = s.currentTrack?.path ?? '';
          this.fetchCoverIfNeeded(s.currentTrack);
        }
      }),
      this.musicService.deckBPlayer.state$.subscribe(s => {
        this.stateB = s;
        this.checkQueueAdvance('B', s);
        if (s.currentTrack?.path !== this.lastCoverPath.B) {
          this.lastCoverPath.B = s.currentTrack?.path ?? '';
          this.fetchCoverIfNeeded(s.currentTrack);
        }
      }),
      this.midiService.devices$.subscribe(devs => {
        this.midiDevices = devs;
        if (!this.activeMidiDeviceId && devs.length > 0) this.activeMidiDeviceId = devs[0].id;
      }),
      this.midiService.activeDeviceId$.subscribe(id => this.activeMidiDeviceId = id),
      this.midiService.isLearning$.subscribe(l => this.isMidiLearning = l),
      this.midiService.action$.subscribe(action => this.handleMidiAction(action)),
      this.midiService.autoDetected$.subscribe(ev => this.showMidiAutoDetectToast(ev.deviceName, ev.presetName)),

      // Auto-add to history after 15s
      this.musicService.deckAPlayer.state$.subscribe(s => this.trackHistoryA(s)),
      this.musicService.deckBPlayer.state$.subscribe(s => this.trackHistoryB(s))
    );
  }

  ngAfterViewInit(): void {
    this.ngZone.runOutsideAngular(() => {
      const loop = () => {
        this.drawSpectrum('A');
        this.drawSpectrum('B');
        this.animFrameId = requestAnimationFrame(loop);
      };
      this.animFrameId = requestAnimationFrame(loop);
    });
  }

  ngOnDestroy(): void {
    if (this.animFrameId !== null) cancelAnimationFrame(this.animFrameId);
    this.subs.forEach(s => s.unsubscribe());
    this.musicService.deckAPlayer.pause();
    this.musicService.deckBPlayer.pause();
    this.removeResizeListeners();
  }

  // ── Resize logic ──────────────────────────────────────────────────────────

  onResizeStart(e: MouseEvent): void {
    e.preventDefault();
    this.resizeDragging = true;
    this.resizeStartY = e.clientY;
    this.resizeStartPct = this.deckHeightPct;
    document.addEventListener('mousemove', this.onResizeMove);
    document.addEventListener('mouseup', this.onResizeEnd);
    document.body.style.cursor = 'ns-resize';
    document.body.style.userSelect = 'none';
  }

  private onResizeMove = (e: MouseEvent): void => {
    if (!this.resizeDragging) return;
    const layout = this.vdjLayoutRef?.nativeElement;
    if (!layout) return;
    const totalH = layout.offsetHeight;
    if (totalH === 0) return;
    const delta = e.clientY - this.resizeStartY;
    const deltaPct = (delta / totalH) * 100;
    const newPct = Math.min(80, Math.max(20, this.resizeStartPct + deltaPct));
    this.deckHeightPct = Math.round(newPct * 10) / 10;
  };

  private onResizeEnd = (): void => {
    if (!this.resizeDragging) return;
    this.resizeDragging = false;
    localStorage.setItem(DeckModeComponent.DECK_HEIGHT_KEY, String(this.deckHeightPct));
    this.removeResizeListeners();
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
  };

  private removeResizeListeners(): void {
    document.removeEventListener('mousemove', this.onResizeMove);
    document.removeEventListener('mouseup', this.onResizeEnd);
  }

  private drawSpectrum(deck: 'A' | 'B'): void {
    const canvas = deck === 'A' ? this.canvasA?.nativeElement : this.canvasB?.nativeElement;
    const player = deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer;
    const state  = deck === 'A' ? this.stateA : this.stateB;
    if (!canvas) return;

    // Sync canvas resolution to CSS size
    if (canvas.offsetWidth > 0 && canvas.width !== canvas.offsetWidth) {
      canvas.width = canvas.offsetWidth;
    }

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const W = canvas.width;
    const H = canvas.height;

    // Dark LCD background
    ctx.fillStyle = '#080808';
    ctx.fillRect(0, 0, W, H);

    // Grid lines (BMP Studio feel)
    ctx.strokeStyle = 'rgba(255,255,255,0.03)';
    ctx.lineWidth = 1;
    for (let y = 0; y < H; y += 6) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }

    const data = player.getFrequencyData();
    const isPlaying = state?.playing;

    const barCount = 28;
    const gap = 2;
    const barW = Math.max(2, (W - (barCount - 1) * gap) / barCount);

    const ledH = 4;
    const ledGap = 1;
    const ledRows = Math.floor(H / (ledH + ledGap));

    for (let i = 0; i < barCount; i++) {
      let value = 0;
      if (data && isPlaying) {
        const di = Math.floor((i / barCount) * data.length * 0.75);
        value = data[di] / 255;
        // Apply slight logarithmic curve for better visual
        value = Math.pow(value, 0.7);
      }

      const activeLeds = Math.floor(value * ledRows);
      const x = i * (barW + gap);

      for (let j = 0; j < ledRows; j++) {
        const ratio = j / ledRows;
        let r: number, g: number, b: number;

        if (ratio < 0.6) {
          // Green zone
          r = 0; g = 220; b = 60;
        } else if (ratio < 0.8) {
          // Yellow zone
          r = 230; g = 200; b = 0;
        } else {
          // Red zone
          r = 255; g = 30; b = 0;
        }

        const y = H - (j + 1) * (ledH + ledGap);

        if (j < activeLeds) {
          ctx.fillStyle = `rgba(${r},${g},${b},0.95)`;
          // Glow on active bars
          ctx.shadowColor = `rgb(${r},${g},${b})`;
          ctx.shadowBlur = 4;
        } else {
          // Dim inactive LEDs
          ctx.fillStyle = `rgba(${r},${g},${b},0.07)`;
          ctx.shadowBlur = 0;
        }
        ctx.fillRect(x, y, barW, ledH);
      }
    }
    ctx.shadowBlur = 0;

    // Peak line indicators
    if (data && isPlaying) {
      ctx.strokeStyle = deck === 'A' ? 'rgba(243,156,18,0.6)' : 'rgba(52,152,219,0.6)';
      ctx.lineWidth = 1;
      for (let i = 0; i < barCount; i++) {
        const di = Math.floor((i / barCount) * data.length * 0.75);
        const value = Math.pow(data[di] / 255, 0.7);
        const y = H - value * H - 2;
        const x = i * (barW + gap);
        ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + barW, y); ctx.stroke();
      }
    }
  }

  // ── Volume / Crossfade ────────────────────────────────────────────────────

  /**
   * Single source of truth for all volume math.
   * Combines per-deck fader (volA/volB), mute, and equal-power crossfade.
   * Call this any time volA, volB, crossValue, muteA, or muteB changes.
   */
  applyVolumes(): void {
    const t = (this.crossValue + 1) / 2;                  // 0 … 1
    const crossA = Math.cos(t * Math.PI / 2);             // equal-power
    const crossB = Math.cos((1 - t) * Math.PI / 2);

    const effA = this.muteA ? 0 : this.volA * crossA;
    const effB = this.muteB ? 0 : this.volB * crossB;

    this.musicService.deckAPlayer.setVolume(effA);
    this.musicService.deckBPlayer.setVolume(effB);
  }

  onVolA(e: Event) {
    this.volA = +(e.target as HTMLInputElement).value;
    this.applyVolumes();
  }

  onVolB(e: Event) {
    this.volB = +(e.target as HTMLInputElement).value;
    this.applyVolumes();
  }

  onCrossfade(e: Event) {
    this.crossValue = +(e.target as HTMLInputElement).value;
    this.applyVolumes();
  }

  toggleMute(deck: 'A' | 'B') {
    if (deck === 'A') this.muteA = !this.muteA;
    else              this.muteB = !this.muteB;
    this.applyVolumes();
  }

  // ── Equalizer ─────────────────────────────────────────────────────────────

  onEqChange(deck: 'A'|'B', band: 'low'|'mid'|'high', e: Event) {
    const val = +(e.target as HTMLInputElement).value;
    this.setEqValue(deck, band, val);
  }

  setEqValue(deck: 'A'|'B', band: 'low'|'mid'|'high', value: number) {
    if (deck === 'A') {
      this.eqA[band] = value;
      this.musicService.deckAPlayer.setEq(band, value);
    } else {
      this.eqB[band] = value;
      this.musicService.deckBPlayer.setEq(band, value);
    }
  }

  resetEq(deck: 'A'|'B', band: 'low'|'mid'|'high') {
    this.setEqValue(deck, band, 0);
  }

  // ── Pitch & BPM ───────────────────────────────────────────────────────────

  onPitchChange(deck: 'A'|'B', e: Event) {
    const val = +(e.target as HTMLInputElement).value;
    this.setPitchValue(deck, val);
  }

  setPitchValue(deck: 'A'|'B', value: number) {
    // value is -0.1 to +0.1
    if (deck === 'A') {
       this.pitchA = value;
       this.musicService.deckAPlayer.setPlaybackRate(1 + value);
    } else {
       this.pitchB = value;
       this.musicService.deckBPlayer.setPlaybackRate(1 + value);
    }
  }

  resetPitch(deck: 'A'|'B') {
    this.setPitchValue(deck, 0);
  }

  getCurrentBpm(deck: 'A' | 'B'): number {
    const state = deck === 'A' ? this.stateA : this.stateB;
    const pitch = deck === 'A' ? this.pitchA : this.pitchB;
    const originalBpm = state?.currentTrack?.bpm || 0;
    if (!originalBpm) return 0;
    return originalBpm * (1 + pitch);
  }

  getPitchPct(deck: 'A' | 'B'): string {
    const p = deck === 'A' ? this.pitchA : this.pitchB;
    const pct = p * 100;
    return (pct > 0 ? '+' : '') + pct.toFixed(1) + '%';
  }

  // ── Combo Filter & FX ─────────────────────────────────────────────────────

  onFilterChange(deck: 'A' | 'B', e: Event) {
    const val = +(e.target as HTMLInputElement).value;
    if (deck === 'A') {
      this.filterA = val;
      this.musicService.deckAPlayer.setComboFilter(val);
    } else {
      this.filterB = val;
      this.musicService.deckBPlayer.setComboFilter(val);
    }
  }

  resetFilter(deck: 'A' | 'B') {
    if (deck === 'A') {
      this.filterA = 0;
      this.musicService.deckAPlayer.setComboFilter(0);
    } else {
      this.filterB = 0;
      this.musicService.deckBPlayer.setComboFilter(0);
    }
  }

  onFxChange(deck: 'A' | 'B', param: 'level' | 'feedback' | 'time', e: Event) {
    const val = +(e.target as HTMLInputElement).value;
    const fx = deck === 'A' ? this.fxA : this.fxB;
    (fx as any)[param] = val;
    const player = deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer;
    player.setDelayFX(fx.level, fx.feedback, fx.time);
  }

  // ── Sync ─────────────────────────────────────────────────────────────────

  /**
   * Sync targetDeck to match the BMP of the other deck.
   * If other deck is at 128 BPM and target is at 120, sets pitch to +6.6%.
   */
  syncBpm(targetDeck: 'A' | 'B'): void {
    const refDeck = targetDeck === 'A' ? 'B' : 'A';
    const refBpm = this.getCurrentBpm(refDeck);
    const targetState = targetDeck === 'A' ? this.stateA : this.stateB;
    const originalBpm = targetState?.currentTrack?.bpm || 0;

    if (!refBpm || !originalBpm) return;

    // targetPitch = (refBpm / originalBpm) - 1
    const newPitch = (refBpm / originalBpm) - 1;
    this.setPitchValue(targetDeck, newPitch);
  }

  // ── Hot Cues ─────────────────────────────────────────────────────────────

  setHotCue(deck: 'A' | 'B', index: number) {
    const state = deck === 'A' ? this.stateA : this.stateB;
    if (!state) return;
    const cues = deck === 'A' ? this.hotCuesA : this.hotCuesB;
    cues[index] = state.currentTime;
  }

  jumpToHotCue(deck: 'A' | 'B', index: number) {
    const cues = deck === 'A' ? this.hotCuesA : this.hotCuesB;
    const time = cues[index];
    if (time === undefined) return;
    const player = deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer;
    player.seek(time);
  }

  clearHotCue(deck: 'A' | 'B', index: number, e: Event) {
    e.stopPropagation();
    const cues = deck === 'A' ? this.hotCuesA : this.hotCuesB;
    delete cues[index];
  }

  // ── Looping ──────────────────────────────────────────────────────────────

  toggleLoop(deck: 'A' | 'B', beats: number) {
    const state = deck === 'A' ? this.stateA : this.stateB;
    const player = deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer;
    if (!state?.currentTrack || !state.duration) return;

    if (state.loopActive) {
      player.disableLoop();
    } else {
      const bpm = this.getCurrentBpm(deck) || 120;
      const beatDuration = 60 / bpm;
      const loopDuration = beatDuration * beats;
      const start = state.currentTime;
      const end = Math.min(state.duration, start + loopDuration);
      player.setLoop(start, end, true);
    }
  }

  // ── History ──────────────────────────────────────────────────────────────
  
  private trackHistoryA(s: PlayerState | null) { this.trackHistory(s, 'A'); }
  private trackHistoryB(s: PlayerState | null) { this.trackHistory(s, 'B'); }

  private trackHistory(s: PlayerState | null, deck: 'A' | 'B') {
    if (!s?.currentTrack || !s.playing) {
      if (this.historyTimers[deck]) { clearTimeout(this.historyTimers[deck]); this.historyTimers[deck] = null; }
      return;
    }
    // Only schedule if not already scheduled
    if (!this.historyTimers[deck]) {
      this.historyTimers[deck] = setTimeout(() => {
        if (s.currentTrack) this.addToHistory(s.currentTrack);
      }, 15000); // 15 seconds
    }
  }

  private addToHistory(track: MusicMetadataDto) {
    // Avoid duplicates (consecutive or same track within session)
    if (this.sessionHistory.length > 0) {
      const last = this.sessionHistory[this.sessionHistory.length - 1];
      if (last.path === track.path && last.source === track.source) return;
    }
    this.sessionHistory.push({ ...track });
  }

  clearHistory() {
    this.sessionHistory = [];
  }

  // ── Sync ─────────────────────────────────────────────────────────────────

  /**
   * Sync targetDeck to the same relative position as the other deck.
   * Example: if B is 42% through its track, sync('A') jumps A to 42%.
   * This gives a simple, honest positional sync useful for manual mixing.
   */
  sync(targetDeck: 'A' | 'B'): void {
    const refState    = targetDeck === 'A' ? this.stateB    : this.stateA;
    const targetState = targetDeck === 'A' ? this.stateA    : this.stateB;
    const player      = targetDeck === 'A'
      ? this.musicService.deckAPlayer
      : this.musicService.deckBPlayer;

    if (!refState?.duration || !targetState?.duration) return;

    const pct = refState.currentTime / refState.duration;
    player.seek(pct * targetState.duration);
  }

  /** SYNC is enabled only when both decks have tracks with known duration */
  canSync(deck: 'A' | 'B'): boolean {
    const self  = deck === 'A' ? this.stateA : this.stateB;
    const other = deck === 'A' ? this.stateB : this.stateA;
    return !!(self?.currentTrack && self.duration > 0
           && other?.currentTrack && other.duration > 0);
  }

  // ── Browser ───────────────────────────────────────────────────────────────

  selectPath(id: number) {
    this.selectedPathId = id;
    this.currentSubPath = '';
    this.clearNasSearch();
    this.loadDir();
  }

  loadDir() {
    if (this.selectedPathId === null) return;
    this.musicService.browse(this.selectedPathId, this.currentSubPath, 0, 200).subscribe(result => {
      this.items = result.items;
      this.selectedIndex = -1;
    });
  }

  onNasSearchChange(): void {
    clearTimeout(this.nasSearchDebounce);
    if (!this.nasSearchQuery.trim()) {
      this.nasSearchResults = null;
      this.nasSearchLoading = false;
      return;
    }
    this.nasSearchLoading = true;
    this.nasSearchDebounce = setTimeout(() => this.runNasSearch(), 400);
  }

  private runNasSearch(): void {
    if (!this.selectedPathId || !this.nasSearchQuery.trim()) return;
    this.musicService.search(this.selectedPathId, undefined, this.nasSearchQuery.trim()).subscribe({
      next: results => {
        this.nasSearchResults = results;
        this.nasSearchLoading = false;
        this.selectedIndex = -1;
      },
      error: () => { this.nasSearchLoading = false; }
    });
  }

  clearNasSearch(): void {
    this.nasSearchQuery = '';
    this.nasSearchResults = null;
    this.nasSearchLoading = false;
  }

  navigate(item: MusicMetadataDto) {
    if (!item.directory) return;
    this.currentSubPath = item.path;
    this.loadDir();
  }

  goUp() {
    const parts = this.currentSubPath.split(/[/\\]/).filter(Boolean);
    parts.pop();
    this.currentSubPath = parts.join('/');
    this.loadDir();
  }

  get isRoot()   { return !this.currentSubPath; }
  get folders()  { return this.items.filter(i =>  i.directory); }
  get tracks()   { return this.items.filter(i => !i.directory); }

  // ── Deck load ─────────────────────────────────────────────────────────────

  loadNas(deck: 'A' | 'B', track: MusicMetadataDto) {
    if (!this.selectedPathId) return;
    track.source = 'nas';
    const player = deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer;
    player.load(track, this.selectedPathId);
  }

  loadYoutube(deck: 'A' | 'B', video: any) {
    const videoId = video.id?.videoId || video.id;
    const title   = video.snippet?.title       || videoId;
    const channel = video.snippet?.channelTitle || 'YouTube';

    const track: MusicMetadataDto = {
      name: videoId, path: videoId, source: 'youtube',
      directory: false, size: 0, lastModified: '',
      title, artist: channel,
      album: 'YouTube', duration: 0,
      format: 'mp3', hasCover: true, bpm: 0
    };

    const player = deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer;
    player.load(track, -1);
  }

  // ── YouTube Browser ───────────────────────────────────────────────────────

  searchYouTube() {
    if (!this.ytSearchQuery.trim()) return;
    this.ytSearching = true;

    const base = (() => {
      const host = typeof window !== 'undefined' ? window.location.hostname : '';
      return (host === 'localhost' || host === '127.0.0.1') ? 'http://localhost:8080' : '';
    })();

    this.http.get<any>(`${base}/api/youtube/search`, {
      params: { query: this.ytSearchQuery }
    }).subscribe({
      next:  (r) => { this.ytSearchResults = r.items || []; this.ytSearching = false; },
      error: ()  => { this.ytSearching = false; }
    });
  }

  getYtThumbnail(video: any): string {
    return video.snippet?.thumbnails?.high?.url
        || video.snippet?.thumbnails?.default?.url
        || '';
  }

  loadDirectUrl(deck: 'A' | 'B') {
    if (!this.ytDirectUrl.trim()) return;
    const videoId = this.extractVideoId(this.ytDirectUrl);
    if (!videoId) return;

    const track: MusicMetadataDto = {
      name: videoId, path: videoId, source: 'youtube',
      directory: false, size: 0, lastModified: '',
      title: 'YouTube – ' + videoId, artist: 'YouTube',
      album: 'YouTube', duration: 0,
      format: 'mp3', hasCover: true, bpm: 0
    };
    const player = deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer;
    player.load(track, -1);
    this.ytDirectUrl = '';
  }

  // ── Local Browser (File System Access API) ────────────────────────────────

  async openLocalFolder() {
    if (this.localFsApiSupported) {
      // File System Access API — Chrome, Edge, Opera (con navegación de subcarpetas)
      try {
        const handle = await (window as any).showDirectoryPicker({ mode: 'read' });
        this.localFallbackMode = false;
        this.localRootHandle = handle;
        this.localDirStack = [handle];
        this.localCurrentPathStr = handle.name;
        await this.loadLocalDir(handle);
        this.addLocalRootToTree(handle.name, handle, false);
      } catch (e: any) {
        if (e?.name !== 'AbortError') console.warn('Error accediendo a carpeta:', e);
      }
    } else {
      // Fallback — Firefox, Safari (carga plana de todos los archivos de audio)
      this.localFileInputRef?.nativeElement.click();
    }
  }

  onLocalFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const audioRe = /\.(mp3|wav|flac|ogg|m4a|aac|opus|wma)$/i;
    // Guardamos TODOS los ficheros de audio para el árbol virtual
    this.localFallbackAllFiles = Array.from(input.files).filter(f => audioRe.test(f.name));
    if (this.localFallbackAllFiles.length === 0) { input.value = ''; return; }

    const rootName = ((this.localFallbackAllFiles[0] as any).webkitRelativePath as string)
      .split('/')[0] || 'Archivos locales';

    this.localFallbackMode = true;
    this.localRootHandle = rootName;
    this.localFallbackCurrentPath = rootName;
    this.localDirStack = [rootName];
    this.localCurrentPathStr = rootName;

    this.renderFallbackDir(rootName);
    this.addLocalRootToTree(rootName, rootName, true);
    input.value = '';
  }

  // ── File System Access API (Chrome/Edge) ─────────────────────────────────

  async loadLocalDir(dirHandle: any) {
    const items: MusicMetadataDto[] = [];
    const audioRe = /\.(mp3|wav|flac|ogg|m4a|aac|opus|wma)$/i;
    try {
      for await (const entry of dirHandle.values()) {
        if (entry.kind === 'file') {
          if (audioRe.test(entry.name)) {
            items.push({
              name: entry.name, path: '', directory: false, size: 0,
              lastModified: '', title: entry.name.replace(/\.[^.]+$/, ''),
              artist: '', album: '',
              duration: 0, format: 'local', hasCover: false, bpm: 0,
              source: 'local', localHandle: entry
            });
          }
        } else if (entry.kind === 'directory') {
          items.push({
            name: entry.name, path: '', directory: true, size: 0,
            lastModified: '', title: entry.name, artist: '', album: '',
            duration: 0, format: 'dir', hasCover: false, bpm: 0,
            source: 'local', localHandle: entry
          });
        }
      }
    } catch (e) {
      console.error('Error leyendo directorio:', e);
    }
    items.sort((a, b) => {
      if (a.directory && !b.directory) return -1;
      if (!a.directory && b.directory) return 1;
      return a.name.localeCompare(b.name);
    });
    this.localItems = items;
    this.selectedIndex = -1;
  }

  async navigateLocal(item: MusicMetadataDto) {
    if (!item.directory) return;
    if (this.localFallbackMode) {
      // Fallback: item.path = ruta virtual ("Musica/SubCarpeta")
      this.localFallbackCurrentPath = item.path;
      this.localDirStack.push(item.path);
      this.localCurrentPathStr = item.path;
      this.renderFallbackDir(item.path);
    } else {
      this.localDirStack.push(item.localHandle);
      this.localCurrentPathStr += '/' + item.name;
      await this.loadLocalDir(item.localHandle);
    }
  }

  async goUpLocal() {
    if (this.localDirStack.length <= 1) return;
    this.localDirStack.pop();
    if (this.localFallbackMode) {
      const parentPath = this.localDirStack[this.localDirStack.length - 1] as string;
      this.localFallbackCurrentPath = parentPath;
      this.localCurrentPathStr = parentPath;
      this.renderFallbackDir(parentPath);
    } else {
      const handle = this.localDirStack[this.localDirStack.length - 1];
      const parts = this.localCurrentPathStr.split('/');
      parts.pop();
      this.localCurrentPathStr = parts.join('/');
      await this.loadLocalDir(handle);
    }
  }

  // ── Fallback: árbol virtual desde webkitRelativePath ─────────────────────

  private renderFallbackDir(currentPath: string) {
    const audioRe = /\.(mp3|wav|flac|ogg|m4a|aac|opus|wma)$/i;
    const subdirs = new Map<string, string>(); // name → fullPath
    const files: MusicMetadataDto[] = [];

    for (const f of this.localFallbackAllFiles) {
      const rel = (f as any).webkitRelativePath as string; // "Root/SubDir/file.mp3"
      if (!rel.startsWith(currentPath + '/')) continue;
      const remainder = rel.slice(currentPath.length + 1); // "SubDir/file.mp3" or "file.mp3"
      const parts = remainder.split('/');

      if (parts.length === 1 && audioRe.test(parts[0])) {
        // Archivo directo en este nivel
        files.push({
          name: f.name, path: URL.createObjectURL(f),
          directory: false, size: f.size,
          lastModified: new Date(f.lastModified).toISOString(),
          title: f.name.replace(/\.[^.]+$/, ''),
          artist: '', album: '', duration: 0,
          format: (f.name.split('.').pop() || '').toLowerCase(),
          hasCover: false, bpm: 0,
          source: 'local' as const, localHandle: null
        });
      } else if (parts.length > 1) {
        // Subdirectorio
        const subdirName = parts[0];
        const fullSubPath = currentPath + '/' + subdirName;
        subdirs.set(subdirName, fullSubPath);
      }
    }

    const dirItems: MusicMetadataDto[] = Array.from(subdirs.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([name, fullPath]) => ({
        name, path: fullPath, directory: true, size: 0,
        lastModified: '', title: name, artist: '', album: '',
        duration: 0, format: 'dir', hasCover: false, bpm: 0,
        source: 'local' as const, localHandle: null
      }));

    files.sort((a, b) => a.name.localeCompare(b.name));
    this.localItems = [...dirItems, ...files];
    this.selectedIndex = -1;
  }

  // ── Carga recursiva de carpeta completa ───────────────────────────────────

  async loadAllFromDir(deck: 'A' | 'B', item: MusicMetadataDto) {
    const tracks: MusicMetadataDto[] = [];

    if (this.localFallbackMode) {
      // Fallback: filtrar todos los ficheros bajo ese path
      const audioRe = /\.(mp3|wav|flac|ogg|m4a|aac|opus|wma)$/i;
      const prefix = item.path + '/';
      for (const f of this.localFallbackAllFiles) {
        const rel = (f as any).webkitRelativePath as string;
        if (rel.startsWith(prefix) && audioRe.test(f.name)) {
          tracks.push({
            name: f.name, path: URL.createObjectURL(f),
            directory: false, size: f.size,
            lastModified: new Date(f.lastModified).toISOString(),
            title: f.name.replace(/\.[^.]+$/, ''),
            artist: '', album: '', duration: 0,
            format: (f.name.split('.').pop() || '').toLowerCase(),
            hasCover: false, bpm: 0,
            source: 'local' as const, localHandle: null
          });
        }
      }
    } else {
      // File System Access API: recoger tracks recursivamente
      await this.collectDirTracks(item.localHandle, tracks);
    }

    if (tracks.length === 0) return;
    tracks.sort((a, b) => a.name.localeCompare(b.name));
    await this.loadLocalTrack(deck, tracks[0]);
  }

  private async collectDirTracks(dirHandle: any, result: MusicMetadataDto[]) {
    const audioRe = /\.(mp3|wav|flac|ogg|m4a|aac|opus|wma)$/i;
    try {
      for await (const entry of dirHandle.values()) {
        if (entry.kind === 'file' && audioRe.test(entry.name)) {
          result.push({
            name: entry.name, path: '', directory: false, size: 0,
            lastModified: '', title: entry.name.replace(/\.[^.]+$/, ''),
            artist: '', album: '', duration: 0, format: 'local',
            hasCover: false, bpm: 0, source: 'local' as const, localHandle: entry
          });
        } else if (entry.kind === 'directory') {
          await this.collectDirTracks(entry, result);
        }
      }
    } catch (e) { console.error('Error leyendo subdirectorio:', e); }
  }

  get isLocalRoot() { return this.localDirStack.length <= 1; }
  get localFolders() { return this.localItems.filter(i => i.directory); }
  get localTracks() { return this.localItems.filter(i => !i.directory); }

  async loadLocalTrack(deck: 'A' | 'B', track: MusicMetadataDto) {
    try {
      let url: string;
      if (track.localHandle) {
        // File System Access API: crear blob URL desde el handle
        const file = await track.localHandle.getFile();
        url = URL.createObjectURL(file);
      } else {
        // Fallback: path ya contiene la blob URL creada en onLocalFilesSelected
        url = track.path;
      }
      const playingTrack = { ...track, path: url };
      const player = deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer;
      player.load(playingTrack, -1);
    } catch(e) { console.error('Error al cargar archivo local', e); }
  }

  get currentPlayableList(): any[] {
    if (this.browserTab === 'nas') return this.nasSearchResults !== null ? this.nasSearchResults : this.tracks;
    if (this.browserTab === 'youtube') return this.ytSearchResults;
    if (this.browserTab === 'local') return this.localTracks;
    return [];
  }

  // ── Keyboard Navigation ───────────────────────────────────────────────────

  @HostListener('window:keydown', ['$event'])
  handleKeyboardEvent(event: KeyboardEvent) {
    // Ignore input if user is typing in text boxes
    const target = event.target as HTMLElement;
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT') {
      return;
    }

    const list = this.currentPlayableList;
    const maxIndex = list.length - 1;

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.selectedIndex = this.selectedIndex < maxIndex ? this.selectedIndex + 1 : this.selectedIndex;
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.selectedIndex = this.selectedIndex > 0 ? this.selectedIndex - 1 : (maxIndex >= 0 ? 0 : -1);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      if (this.selectedIndex >= 0 && this.selectedIndex <= maxIndex) {
        const item = list[this.selectedIndex];
        // Shift+Enter -> Deck B, Enter -> Deck A
        const d = event.shiftKey ? 'B' : 'A';
        if (this.browserTab === 'nas') this.loadNas(d as any, item);
        else if (this.browserTab === 'youtube') this.loadYoutube(d as any, item);
        else if (this.browserTab === 'local') this.loadLocalTrack(d as any, item);
      }
    } else if (event.key === 'Backspace') {
      if (this.browserTab === 'nas' && !this.isRoot) {
        event.preventDefault();
        this.goUp();
      } else if (this.browserTab === 'local' && !this.isLocalRoot) {
        event.preventDefault();
        this.goUpLocal();
      }
    }
  }

  private extractVideoId(url: string): string | null {
    const m = url.match(/(?:v=|\/)([0-9A-Za-z_-]{11})(?:[&?]|$)/);
    return m ? m[1] : null;
  }

  // ── Deck controls ─────────────────────────────────────────────────────────

  togglePlay(deck: 'A' | 'B') {
    (deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer).togglePlay();
  }

  cue(deck: 'A' | 'B') {
    (deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer).cue();
  }

  onSeek(deck: 'A' | 'B', e: Event) {
    const t = +(e.target as HTMLInputElement).value;
    (deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer).seek(t);
  }

  resetTrack(deck: 'A' | 'B') {
    (deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer).seek(0);
  }

  nextTrack(deck: 'A' | 'B') {
    this.shiftTrack(deck, 1);
  }

  prevTrack(deck: 'A' | 'B') {
    this.shiftTrack(deck, -1);
  }

  private shiftTrack(deck: 'A' | 'B', offset: number) {
    const state = deck === 'A' ? this.stateA : this.stateB;
    if (!state?.currentTrack) return;
    
    // Find current track in current visible list to jump to next
    const list = this.currentPlayableList;
    if (list.length === 0) return;

    let currentIndex = -1;
    if (this.browserTab === 'nas') {
      currentIndex = list.findIndex(i => i.path === state.currentTrack?.path);
    } else if (this.browserTab === 'youtube') {
      currentIndex = list.findIndex(i => {
         const vId = i.id?.videoId || i.id;
         return vId === state.currentTrack?.path;
      });
    } else if (this.browserTab === 'local') {
      currentIndex = list.findIndex(i => i.localHandle?.name === state.currentTrack?.localHandle?.name);
    }

    let nextIndex = currentIndex + offset;
    if (nextIndex < 0) nextIndex = 0;
    if (nextIndex >= list.length) nextIndex = list.length - 1;

    const nextItem = list[nextIndex];
    if (!nextItem) return;

    this.selectedIndex = nextIndex; // feedback visual

    if (this.browserTab === 'nas') {
      this.loadNas(deck, nextItem);
    } else if (this.browserTab === 'local') {
      this.loadLocalTrack(deck, nextItem);
    } else {
      this.loadYoutube(deck, nextItem);
    }
  }

  // ── Double-click: carga en el deck inactivo ───────────────────────────────

  /** Elige el deck que NO está sonando (si ambos suenan o ninguno → A). */
  private idleDeck(): 'A' | 'B' {
    return (this.stateA?.playing && !this.stateB?.playing) ? 'B' : 'A';
  }

  loadToIdleDeck(track: MusicMetadataDto) {
    const deck = this.idleDeck();
    const player = deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer;
    if (track.source === 'local' || track.source === 'youtube') {
      player.load(track, -1);
    } else {
      if (!this.selectedPathId) return;
      track.source = 'nas';
      player.load(track, this.selectedPathId);
    }
  }

  loadYoutubeToIdleDeck(video: any) {
    this.loadYoutube(this.idleDeck(), video);
  }

  // ── Album art desde iTunes API ─────────────────────────────────────────────

  private fetchCoverIfNeeded(track: MusicMetadataDto | null) {
    if (!track || track.source === 'youtube') return; // YT ya tiene miniatura
    if (track.hasCover) return;                        // el servidor ya la tiene
    if (this.coverOverrideMap.has(track.path)) return; // ya buscada o en curso

    const query = [track.artist, track.album || track.title].filter(Boolean).join(' ');
    if (!query.trim()) return;

    // Marcar como "en curso" para no lanzar peticiones duplicadas
    this.coverOverrideMap.set(track.path, '');

    this.http.get<any>('https://itunes.apple.com/search', {
      params: { term: query, entity: 'album', limit: '3', media: 'music' }
    }).subscribe({
      next: (r) => {
        const result = r.results?.find((x: any) => x.artworkUrl100);
        if (result?.artworkUrl100) {
          // Sustituir resolución 100x100 → 600x600 para mejor calidad
          const url = result.artworkUrl100.replace('100x100bb', '600x600bb');
          this.coverOverrideMap.set(track.path, url);
        } else {
          this.coverOverrideMap.delete(track.path); // sin resultado, permitir reintento
        }
      },
      error: () => this.coverOverrideMap.delete(track.path)
    });
  }

  hasCoverToShow(state: PlayerState | null): boolean {
    if (!state?.currentTrack) return false;
    const t = state.currentTrack;
    if (t.source === 'youtube') return true;
    const override = this.coverOverrideMap.get(t.path);
    if (override) return true;
    return !!t.hasCover && !!state.pathId;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  coverUrl(state: PlayerState | null): string {
    if (!state?.currentTrack) return '';
    const track = state.currentTrack;
    if (track.source === 'youtube') {
      return `https://img.youtube.com/vi/${track.path}/hqdefault.jpg`;
    }
    // Carátula buscada en iTunes API
    const override = this.coverOverrideMap.get(track.path);
    if (override) return override;
    if (!track.hasCover || !state.pathId) return '';
    return this.musicService.getCoverUrl(state.pathId, track.path, track.source);
  }

  progressPct(state: PlayerState | null): number {
    if (!state?.duration) return 0;
    return (state.currentTime / state.duration) * 100;
  }

  fmt(seconds: number | undefined): string {
    if (!seconds || seconds <= 0) return '0:00';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  }

  remaining(state: PlayerState | null): string {
    if (!state) return '0:00';
    return this.fmt((state.duration || 0) - (state.currentTime || 0));
  }

  crossPctA(): number {
    return Math.round(Math.cos(((this.crossValue + 1) / 2) * Math.PI / 2) * 100);
  }
  crossPctB(): number {
    return Math.round(Math.cos((1 - (this.crossValue + 1) / 2) * Math.PI / 2) * 100);
  }

  // ── Queue ─────────────────────────────────────────────────────────────────

  addToQueue(deck: 'A' | 'B', track: MusicMetadataDto, pathId: number = -1) {
    const item: QueueItem = { track: { ...track }, pathId };
    if (deck === 'A') this.queueA.push(item);
    else              this.queueB.push(item);
  }

  removeFromQueue(deck: 'A' | 'B', index: number) {
    if (deck === 'A') this.queueA.splice(index, 1);
    else              this.queueB.splice(index, 1);
  }

  clearQueue(deck: 'A' | 'B') {
    if (deck === 'A') this.queueA = [];
    else              this.queueB = [];
  }

  private checkQueueAdvance(deck: 'A' | 'B', state: PlayerState | null) {
    const prevPlaying = deck === 'A' ? this.prevPlayingA : this.prevPlayingB;
    const queue       = deck === 'A' ? this.queueA       : this.queueB;

    // Detect natural end: was playing, now stopped, at least 95% through
    if (prevPlaying && state && !state.playing
        && state.duration > 0
        && state.currentTime / state.duration > 0.95
        && queue.length > 0) {
      const next = queue.shift()!;
      this.loadQueueItem(deck, next);
    }

    if (deck === 'A') this.prevPlayingA = state?.playing ?? false;
    else              this.prevPlayingB = state?.playing ?? false;
  }

  private loadQueueItem(deck: 'A' | 'B', item: QueueItem) {
    const track = item.track;
    if (track.source === 'youtube') {
      this.loadYoutube(deck, { id: track.path, snippet: { title: track.title, channelTitle: track.artist } });
    } else if (track.source === 'local') {
      this.loadLocalTrack(deck, track);
    } else {
      this.loadNas(deck, track);
    }
  }

  queueTrackLabel(item: QueueItem): string {
    return item.track.title || item.track.name || '—';
  }

  // ── Folder Tree ──────────────────────────────────────────────────────────

  private buildNasTree() {
    // Keep any existing local roots in the tree
    const localRoots = this.treeRoots.filter(r => r.source === 'local');
    const nasRoots: TreeNode[] = this.paths.map(p => ({
      key: `nas:${p.id}:`,
      name: p.name,
      depth: 0,
      expanded: false,
      loading: false,
      childrenLoaded: false,
      children: [],
      source: 'nas' as const,
      pathId: p.id,
      subPath: '',
      isRoot: true
    }));
    this.treeRoots = [...nasRoots, ...localRoots];
    this.buildVisibleTree();
  }

  addLocalRootToTree(name: string, handle: any, fallbackMode: boolean) {
    const key = `local:${name}`;
    // Remove any existing root with the same name
    this.treeRoots = this.treeRoots.filter(r => r.key !== key);
    const root: TreeNode = {
      key,
      name,
      depth: 0,
      expanded: false,
      loading: false,
      childrenLoaded: false,
      children: [],
      source: 'local',
      isRoot: true,
      localHandle: handle,
      localVirtualPath: fallbackMode ? name : undefined,
      localFallbackMode: fallbackMode
    };
    this.treeRoots.push(root);
    this.buildVisibleTree();
  }

  buildVisibleTree() {
    this.visibleTreeNodes = [];
    this.flattenVisible(this.treeRoots);
  }

  private flattenVisible(nodes: TreeNode[]) {
    for (const n of nodes) {
      this.visibleTreeNodes.push(n);
      if (n.expanded) this.flattenVisible(n.children);
    }
  }

  toggleTreeNode(node: TreeNode) {
    if (node.expanded) {
      node.expanded = false;
      this.buildVisibleTree();
      return;
    }
    if (!node.childrenLoaded) {
      if (node.source === 'nas') {
        node.loading = true;
        this.musicService.browse(node.pathId!, node.subPath!, 0, 200).subscribe(result => {
          node.children = result.items
            .filter((i: MusicMetadataDto) => i.directory)
            .sort((a: MusicMetadataDto, b: MusicMetadataDto) => a.name.localeCompare(b.name))
            .map((i: MusicMetadataDto) => {
              const sub = node.subPath ? `${node.subPath}/${i.name}` : i.name;
              return {
                key: `nas:${node.pathId}:${sub}`,
                name: i.name,
                depth: node.depth + 1,
                expanded: false,
                loading: false,
                childrenLoaded: false,
                children: [],
                source: 'nas' as const,
                pathId: node.pathId,
                subPath: sub,
                isRoot: false
              };
            });
          node.childrenLoaded = true;
          node.loading = false;
          node.expanded = true;
          this.buildVisibleTree();
        });
      } else {
        // Local source
        node.loading = true;
        this.loadLocalTreeChildren(node).then(() => {
          node.childrenLoaded = true;
          node.loading = false;
          node.expanded = true;
          this.ngZone.run(() => this.buildVisibleTree());
        });
      }
    } else {
      node.expanded = true;
      this.buildVisibleTree();
    }
  }

  private async loadLocalTreeChildren(node: TreeNode): Promise<void> {
    if (node.localFallbackMode) {
      // Build children from virtual path using localFallbackAllFiles
      const currentPath = node.localVirtualPath!;
      const subdirs = new Map<string, string>();
      for (const f of this.localFallbackAllFiles) {
        const rel = (f as any).webkitRelativePath as string;
        if (!rel.startsWith(currentPath + '/')) continue;
        const remainder = rel.slice(currentPath.length + 1);
        const parts = remainder.split('/');
        if (parts.length > 1) {
          subdirs.set(parts[0], currentPath + '/' + parts[0]);
        }
      }
      node.children = Array.from(subdirs.entries())
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([name, vPath]) => ({
          key: `local:${vPath}`,
          name,
          depth: node.depth + 1,
          expanded: false,
          loading: false,
          childrenLoaded: false,
          children: [],
          source: 'local' as const,
          isRoot: false,
          localHandle: null,
          localVirtualPath: vPath,
          localFallbackMode: true
        }));
    } else {
      // File System Access API
      const children: TreeNode[] = [];
      try {
        for await (const entry of node.localHandle.values()) {
          if (entry.kind === 'directory') {
            const childKey = node.key + '/' + entry.name;
            children.push({
              key: childKey,
              name: entry.name,
              depth: node.depth + 1,
              expanded: false,
              loading: false,
              childrenLoaded: false,
              children: [],
              source: 'local' as const,
              isRoot: false,
              localHandle: entry,
              localFallbackMode: false
            });
          }
        }
      } catch (e) { console.error('Error reading directory tree:', e); }
      children.sort((a, b) => a.name.localeCompare(b.name));
      node.children = children;
    }
  }

  selectTreeFolder(node: TreeNode) {
    this.treeSelectedKey = node.key;
    if (node.source === 'nas') {
      this.selectedPathId = node.pathId!;
      this.currentSubPath = node.subPath!;
      this.browserTab = 'nas';
      this.loadDir();
    } else {
      // Local node: switch to local tab and navigate into this folder
      this.browserTab = 'local';
      if (node.localFallbackMode) {
        const vPath = node.localVirtualPath!;
        this.localFallbackCurrentPath = vPath;
        // Reconstruct dirStack from path segments
        const parts = vPath.split('/');
        this.localDirStack = parts.reduce((acc: string[], _, i) => {
          acc.push(parts.slice(0, i + 1).join('/'));
          return acc;
        }, []);
        this.localCurrentPathStr = vPath;
        this.renderFallbackDir(vPath);
      } else {
        // FS API: navigate to this handle
        this.localRootHandle = node.isRoot ? node.localHandle : this.localRootHandle;
        this.localDirStack = [node.localHandle];
        this.localCurrentPathStr = node.name;
        this.loadLocalDir(node.localHandle);
      }
    }
  }

  get breadcrumbs(): { label: string; subPath: string }[] {
    const rootName = this.paths.find(p => p.id === this.selectedPathId)?.name ?? '';
    const crumbs: { label: string; subPath: string }[] = [{ label: rootName, subPath: '' }];
    if (!this.currentSubPath) return crumbs;
    let acc = '';
    for (const part of this.currentSubPath.split('/').filter(Boolean)) {
      acc = acc ? `${acc}/${part}` : part;
      crumbs.push({ label: part, subPath: acc });
    }
    return crumbs;
  }

  navigateToBreadcrumb(crumb: { label: string; subPath: string }) {
    this.currentSubPath = crumb.subPath;
    this.loadDir();
    // sync tree selection
    if (this.selectedPathId !== null) {
      const key = `nas:${this.selectedPathId}:${crumb.subPath}`;
      this.treeSelectedKey = crumb.subPath === '' ? `nas:${this.selectedPathId}:` : key;
    }
  }

  startTreeResize(e: MouseEvent) {
    const startX = e.clientX;
    const startW = this.treeWidth;
    const move = (ev: MouseEvent) => {
      this.treeWidth = Math.max(150, Math.min(420, startW + ev.clientX - startX));
    };
    const up = () => {
      document.removeEventListener('mousemove', move);
      document.removeEventListener('mouseup', up);
    };
    document.addEventListener('mousemove', move);
    document.addEventListener('mouseup', up);
    e.preventDefault();
  }

  // ── MIDI Handling ─────────────────────────────────────────────────────────

  handleMidiAction(action: any) {
    const isButtonRelease = action.rawValue === 0;

    switch (action.actionId) {
      case 'VOL_A':
        this.volA = action.normalizedValue;
        this.applyVolumes();
        break;
      case 'VOL_B':
        this.volB = action.normalizedValue;
        this.applyVolumes();
        break;
      case 'CROSSFADER':
        this.crossValue = (action.normalizedValue * 2) - 1;
        this.applyVolumes();
        break;
      case 'PLAY_A':
        if (!isButtonRelease) this.togglePlay('A');
        break;
      case 'PLAY_B':
        if (!isButtonRelease) this.togglePlay('B');
        break;
      case 'CUE_A':
        if (!isButtonRelease) this.cue('A');
        break;
      case 'CUE_B':
        if (!isButtonRelease) this.cue('B');
        break;
      case 'SYNC_A':
        if (!isButtonRelease && this.canSync('A')) this.sync('A');
        break;
      case 'SYNC_B':
        if (!isButtonRelease && this.canSync('B')) this.sync('B');
        break;
      case 'MUTE_A':
        if (!isButtonRelease) this.toggleMute('A');
        break;
      case 'MUTE_B':
        if (!isButtonRelease) this.toggleMute('B');
        break;
      case 'NEXT_A':
        if (!isButtonRelease) this.nextTrack('A');
        break;
      case 'PREV_A':
        if (!isButtonRelease) this.prevTrack('A');
        break;
      case 'RESET_A':
        if (!isButtonRelease) this.resetTrack('A');
        break;
      case 'NEXT_B':
        if (!isButtonRelease) this.nextTrack('B');
        break;
      case 'PREV_B':
        if (!isButtonRelease) this.prevTrack('B');
        break;
      case 'RESET_B':
        if (!isButtonRelease) this.resetTrack('B');
        break;
      case 'EQ_LOW_A':
        this.setEqValue('A', 'low', (action.normalizedValue * 30) - 15);
        break;
      case 'EQ_MID_A':
        this.setEqValue('A', 'mid', (action.normalizedValue * 30) - 15);
        break;
      case 'EQ_HIGH_A':
        this.setEqValue('A', 'high', (action.normalizedValue * 30) - 15);
        break;
      case 'EQ_LOW_B':
        this.setEqValue('B', 'low', (action.normalizedValue * 30) - 15);
        break;
      case 'EQ_MID_B':
        this.setEqValue('B', 'mid', (action.normalizedValue * 30) - 15);
        break;
      case 'EQ_HIGH_B':
        this.setEqValue('B', 'high', (action.normalizedValue * 30) - 15);
        break;
      case 'PITCH_A':
        // Map 0..1 to -0.1 .. +0.1
        this.setPitchValue('A', (action.normalizedValue * 0.2) - 0.1);
        break;
      case 'PITCH_B':
        this.setPitchValue('B', (action.normalizedValue * 0.2) - 0.1);
        break;
    }
  }

  selectMidiDevice(event: Event) {
    const id = (event.target as HTMLSelectElement).value;
    this.midiService.setActiveDevice(id || null);
  }

  learnMidi(actionId: string) {
    this.midiService.startLearning(actionId);
  }

  cancelLearnMidi() {
    this.midiService.stopLearning();
  }

  clearMidiMapping(actionId: string) {
    this.midiService.clearMapping(actionId);
  }

  resetMidiToPreset() {
    this.midiService.resetToPreset();
  }

  private showMidiAutoDetectToast(deviceName: string, presetName: string) {
    if (this.midiToastTimer) clearTimeout(this.midiToastTimer);
    this.midiAutoDetectMsg = this.translate.instant('MUSIC.DJ_MIDI_DETECTED', { deviceName, presetName });
    this.midiToastTimer = setTimeout(() => {
      this.midiAutoDetectMsg = '';
      this.midiToastTimer = null;
    }, 5000);
  }
}
