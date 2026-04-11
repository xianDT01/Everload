import { Component, OnInit, OnDestroy, AfterViewInit, HostListener, ViewChild, ElementRef, NgZone } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicMetadataDto, MusicService, PlayerState } from '../../../services/music.service';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../services/auth.service';
import { NasPath, NasService } from '../../../services/nas.service';
import { MidiService, MidiDevice } from '../../../services/midi.service';

@Component({
  selector: 'app-deck-mode',
  templateUrl: './deck-mode.component.html',
  styleUrls: ['./deck-mode.component.css']
})
export class DeckModeComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('spectrumA') canvasA?: ElementRef<HTMLCanvasElement>;
  @ViewChild('spectrumB') canvasB?: ElementRef<HTMLCanvasElement>;

  private animFrameId: number | null = null;

  paths: NasPath[] = [];
  selectedPathId: number | null = null;
  currentSubPath = '';
  items: MusicMetadataDto[] = [];

  stateA: PlayerState | null = null;
  stateB: PlayerState | null = null;

  crossValue = 0;   // -1 … 0 … +1
  volA = 1;
  volB = 1;
  muteA = false;
  muteB = false;

  eqA = { low: 0, mid: 0, high: 0 };
  eqB = { low: 0, mid: 0, high: 0 };

  pitchA = 0; // -0.1 ... 0 ... +0.1 (significa +/- 10%)
  pitchB = 0;

  browserTab: 'nas' | 'youtube' | 'midi' | 'local' = 'nas';
  ytSearchQuery = '';
  ytSearchResults: any[] = [];
  ytSearching = false;
  ytDirectUrl = '';
  
  localRootHandle: any = null;
  localDirStack: any[] = [];
  localItems: MusicMetadataDto[] = [];
  localCurrentPathStr = '';
  localFsApiSupported = typeof (window as any) !== 'undefined' && typeof (window as any).showDirectoryPicker === 'function';
  localFallbackMode = false;

  @ViewChild('localFileInput') localFileInputRef?: ElementRef<HTMLInputElement>;
  
  selectedIndex: number = -1;

  private subs: Subscription[] = [];

  midiDevices: MidiDevice[] = [];
  activeMidiDeviceId: string | null = null;
  isMidiLearning = false;

  midiActions = [
    { id: 'VOL_A', label: 'Volumen Deck A' },
    { id: 'VOL_B', label: 'Volumen Deck B' },
    { id: 'CROSSFADER', label: 'Crossfader' },
    { id: 'PLAY_A', label: 'Play/Pause Deck A' },
    { id: 'PLAY_B', label: 'Play/Pause Deck B' },
    { id: 'CUE_A', label: 'Cue Deck A' },
    { id: 'CUE_B', label: 'Cue Deck B' },
    { id: 'SYNC_A', label: 'Sync Deck A' },
    { id: 'SYNC_B', label: 'Sync Deck B' },
    { id: 'MUTE_A', label: 'Mute Deck A' },
    { id: 'MUTE_B', label: 'Mute Deck B' },
    { id: 'NEXT_A', label: 'Siguiente Deck A' },
    { id: 'PREV_A', label: 'Anterior Deck A' },
    { id: 'RESET_A', label: 'Reset Deck A' },
    { id: 'NEXT_B', label: 'Siguiente Deck B' },
    { id: 'PREV_B', label: 'Anterior Deck B' },
    { id: 'RESET_B', label: 'Reset Deck B' },
    { id: 'EQ_LOW_A', label: 'EQ Low Deck A' },
    { id: 'EQ_MID_A', label: 'EQ Mid Deck A' },
    { id: 'EQ_HIGH_A', label: 'EQ High Deck A' },
    { id: 'EQ_LOW_B', label: 'EQ Low Deck B' },
    { id: 'EQ_MID_B', label: 'EQ Mid Deck B' },
    { id: 'EQ_HIGH_B', label: 'EQ High Deck B' },
    { id: 'PITCH_A', label: 'Pitch Deck A' },
    { id: 'PITCH_B', label: 'Pitch Deck B' }
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
    this.applyVolumes();

    this.nasService.getPaths().subscribe(paths => {
      this.paths = paths;
      if (paths.length > 0) this.selectPath(paths[0].id);
    });

    this.subs.push(
      this.musicService.deckAPlayer.state$.subscribe(s => this.stateA = s),
      this.musicService.deckBPlayer.state$.subscribe(s => this.stateB = s),
      this.midiService.devices$.subscribe(d => this.midiDevices = d),
      this.midiService.activeDeviceId$.subscribe(id => this.activeMidiDeviceId = id),
      this.midiService.isLearning$.subscribe(l => this.isMidiLearning = l),
      this.midiService.action$.subscribe(action => this.handleMidiAction(action))
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
    this.loadDir();
  }

  loadDir() {
    if (this.selectedPathId === null) return;
    this.musicService.browse(this.selectedPathId, this.currentSubPath).subscribe(items => {
      this.items = items;
      this.selectedIndex = -1;
    });
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
    const files = Array.from(input.files).filter(f => audioRe.test(f.name));

    const items: MusicMetadataDto[] = files.map(f => ({
      name: f.name,
      path: URL.createObjectURL(f),
      directory: false, size: f.size,
      lastModified: new Date(f.lastModified).toISOString(),
      title: f.name.replace(/\.[^.]+$/, ''),
      artist: '', album: '',
      duration: 0,
      format: (f.name.split('.').pop() || '').toLowerCase(),
      hasCover: false, bpm: 0,
      source: 'local' as const,
      localHandle: null
    }));

    items.sort((a, b) => a.name.localeCompare(b.name));

    const rootName = (files[0] as any)?.webkitRelativePath?.split('/')[0] || 'Archivos locales';
    this.localFallbackMode = true;
    this.localRootHandle = rootName;
    this.localDirStack = [];
    this.localCurrentPathStr = rootName;
    this.localItems = items;
    this.selectedIndex = -1;

    input.value = ''; // Permite reseleccionar la misma carpeta
  }

  async loadLocalDir(dirHandle: any) {
    const items: MusicMetadataDto[] = [];
    for await (const entry of dirHandle.values()) {
      if (entry.kind === 'file') {
         if (entry.name.match(/\.(mp3|wav|flac|ogg|m4a|aac)$/i)) {
           items.push({
             name: entry.name, path: '', directory: false, size: 0,
             lastModified: '', title: entry.name, artist: '', album: '',
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
    items.sort((a,b) => {
      if (a.directory && !b.directory) return -1;
      if (!a.directory && b.directory) return 1;
      return a.name.localeCompare(b.name);
    });
    this.localItems = items;
    this.selectedIndex = -1;
  }

  async navigateLocal(item: MusicMetadataDto) {
    if (!item.directory) return;
    this.localDirStack.push(item.localHandle);
    this.localCurrentPathStr += '/' + item.name;
    await this.loadLocalDir(item.localHandle);
  }

  async goUpLocal() {
    if (this.localDirStack.length <= 1) return;
    this.localDirStack.pop();
    const handle = this.localDirStack[this.localDirStack.length - 1];
    const parts = this.localCurrentPathStr.split('/');
    parts.pop();
    this.localCurrentPathStr = parts.join('/');
    await this.loadLocalDir(handle);
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
    if (this.browserTab === 'nas') return this.tracks;
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

  // ── Helpers ───────────────────────────────────────────────────────────────

  coverUrl(state: PlayerState | null): string {
    if (!state?.currentTrack) return '';
    const track = state.currentTrack;
    if (track.source === 'youtube') {
      return `https://img.youtube.com/vi/${track.path}/hqdefault.jpg`;
    }
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
}
