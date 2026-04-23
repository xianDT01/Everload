import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';
import { BehaviorSubject, Observable, Subject } from 'rxjs';

export interface PagedMusicResult {
  items: MusicMetadataDto[];
  totalTracks: number;
  page: number;
  size: number;
}

export interface MusicMetadataDto {
  name: string;
  path: string;
  directory: boolean;
  size: number;
  lastModified: string;
  title: string;
  artist: string;
  album: string;
  duration: number;
  format: string;
  hasCover: boolean;
  bpm: number;
  source?: 'nas' | 'youtube' | 'local';
  localHandle?: any;
  nasPathId?: number; // Set when track comes from favorites/history
}

export interface PlayerState {
  playing: boolean;
  currentTime: number;
  duration: number;
  volume: number;
  currentTrack: MusicMetadataDto | null;
  pathId: number | null;
  loading: boolean;
  error: string | null;
  playbackRate: number;
  loopActive: boolean;
  loopStart: number;
  loopEnd: number;
}

// ── YouTube IFrame API loader ─────────────────────────────────────────────────

let ytApiReady: Promise<void> | null = null;

function ensureYouTubeAPI(): Promise<void> {
  if (ytApiReady) return ytApiReady;
  ytApiReady = new Promise<void>((resolve) => {
    if (typeof window === 'undefined') { resolve(); return; }
    if ((window as any).YT && (window as any).YT.Player) { resolve(); return; }
    const existing = (window as any).onYouTubeIframeAPIReady;
    (window as any).onYouTubeIframeAPIReady = () => {
      if (existing) existing();
      resolve();
    };
    if (!document.querySelector('script[src*="youtube.com/iframe_api"]')) {
      const tag = document.createElement('script');
      tag.src = 'https://www.youtube.com/iframe_api';
      document.head.appendChild(tag);
    }
  });
  return ytApiReady;
}

// ── DeckPlayer (unified NAS + YouTube) ────────────────────────────────────────

export class DeckPlayer {
  private audio: HTMLAudioElement;
  private ytPlayer: any = null;
  private ytContainerId: string;
  private ytReady = false;
  private ytInterval: any = null;
  private activeSource: 'nas' | 'youtube' | 'local' | null = null;

  private audioCtx: AudioContext | null = null;
  private analyserNode: AnalyserNode | null = null;
  private analyserData: Uint8Array | null = null;

  // EQ Filters
  private filterLow: BiquadFilterNode | null = null;
  private filterMid: BiquadFilterNode | null = null;
  private filterHigh: BiquadFilterNode | null = null;

  // Combo Filter
  private comboFilter: BiquadFilterNode | null = null;

  // Echo/Delay FX
  private delayNode: DelayNode | null = null;
  private delayGain: GainNode | null = null; // Feedback
  private wetGain: GainNode | null = null;
  private dryGain: GainNode | null = null;

  private loopActive = false;
  private loopStart = 0;
  private loopEnd = 0;

  private stateSubj = new BehaviorSubject<PlayerState>({
    playing: false, currentTime: 0, duration: 0,
    volume: 1, currentTrack: null, pathId: null, loading: false, error: null,
    playbackRate: 1, loopActive: false, loopStart: 0, loopEnd: 0
  });

  public state$ = this.stateSubj.asObservable();
  public onTrackEnded?: () => void;

  constructor(private musicService: MusicService, private deckId: string) {
    this.ytContainerId = 'yt-player-' + deckId;
    this.audio = new Audio();
    this.audio.crossOrigin = 'anonymous'; // Important for CORS Web Audio
    this.setupAudioNodes();

    this.audio.addEventListener('timeupdate',     () => {
      this.patch({ currentTime: this.audio.currentTime });
      this.checkLoop();
    });
    this.audio.addEventListener('play',           () => this.patch({ playing: true, error: null }));
    this.audio.addEventListener('pause',          () => this.patch({ playing: false }));
    this.audio.addEventListener('loadedmetadata', () => this.patch({ duration: this.audio.duration }));
    this.audio.addEventListener('volumechange',   () => this.patch({ volume: this.audio.volume }));
    this.audio.addEventListener('ended',          () => {
      this.patch({ playing: false, currentTime: 0 });
      if (this.onTrackEnded) this.onTrackEnded();
    });
    this.audio.addEventListener('error', () => {
      const e = this.audio.error;
      const msg = e ? `Audio error (code ${e.code}): ${e.message || 'stream no disponible'}` : 'Error de reproduccion';
      this.patch({ playing: false, loading: false, error: msg });
    });
  }

  private patch(partial: Partial<PlayerState>) {
    this.stateSubj.next({ ...this.stateSubj.value, ...partial });
  }

  get state(): PlayerState { return this.stateSubj.value; }

  private setupAudioNodes() {
    if (typeof window === 'undefined') return;
    const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
    if (!AudioCtx) return;

    try {
      this.audioCtx = new AudioCtx();
      const source = this.audioCtx.createMediaElementSource(this.audio);

      this.filterLow = this.audioCtx.createBiquadFilter();
      this.filterLow.type = 'lowshelf';
      this.filterLow.frequency.value = 320;

      this.filterMid = this.audioCtx.createBiquadFilter();
      this.filterMid.type = 'peaking';
      this.filterMid.frequency.value = 1000;
      this.filterMid.Q.value = 0.5;

      this.filterHigh = this.audioCtx.createBiquadFilter();
      this.filterHigh.type = 'highshelf';
      this.filterHigh.frequency.value = 3200;

      // Combo Filter (HP / LP)
      this.comboFilter = this.audioCtx.createBiquadFilter();
      this.comboFilter.type = 'allpass'; // Default flat

      // Delay FX
      this.dryGain = this.audioCtx.createGain();
      this.wetGain = this.audioCtx.createGain();
      this.wetGain.gain.value = 0; // Default dry

      this.delayNode = this.audioCtx.createDelay(2.0); // 2sec max delay
      this.delayNode.delayTime.value = 0.5; // Default 500ms
      this.delayGain = this.audioCtx.createGain();
      this.delayGain.gain.value = 0.5; // Feedback

      this.analyserNode = this.audioCtx.createAnalyser();
      this.analyserNode.fftSize = 2048;
      this.analyserNode.smoothingTimeConstant = 0.8;

      // Routing: source -> EQ -> ComboFilter -> [Dry / Wet(Delay)] -> Analyser -> Destination
      source.connect(this.filterLow);
      this.filterLow.connect(this.filterMid);
      this.filterMid.connect(this.filterHigh);
      this.filterHigh.connect(this.comboFilter);
      
      // FX Routing
      this.comboFilter.connect(this.dryGain);
      this.comboFilter.connect(this.delayNode);
      this.delayNode.connect(this.delayGain);
      this.delayGain.connect(this.delayNode); // Feedback loop
      this.delayNode.connect(this.wetGain);

      this.dryGain.connect(this.analyserNode);
      this.wetGain.connect(this.analyserNode);

      this.analyserNode.connect(this.audioCtx.destination);
    } catch (e) {
      console.error('[DeckPlayer ' + this.deckId + '] Web Audio API setup failed — EQ will not work:', e);
      this.audioCtx = null;
    }
  }

  // ── Load ──────────────────────────────────────────────────────────────────

  async load(track: MusicMetadataDto, pathId: number) {
    this.stopAll();
    this.patch({ currentTrack: track, pathId, currentTime: 0, playing: false, duration: 0, loading: true, error: null });

    if (track.source === 'youtube') {
      this.activeSource = 'youtube';
      await this.loadYoutube(track.path);
    } else if (track.source === 'local') {
      this.activeSource = 'local';
      this.loadLocal(track);
    } else {
      this.activeSource = 'nas';
      this.loadNas(track, pathId);
    }
  }

  private loadLocal(track: MusicMetadataDto) {
    // Para local, track.path contiene directamente la URL de blob: generada
    this.audio.src = track.path;
    this.audio.load();
    this.patch({ loading: false });
  }

  private loadNas(track: MusicMetadataDto, pathId: number) {
    const url = this.musicService.getStreamUrl(pathId, track.path);
    this.audio.src = url;
    this.audio.load();
    this.patch({ loading: false });
  }

  private async loadYoutube(videoId: string) {
    await ensureYouTubeAPI();
    this.ensureYtContainer();

    if (this.ytPlayer && this.ytReady) {
      this.ytPlayer.loadVideoById(videoId);
      this.patch({ loading: false });
    } else {
      // Destroy old broken player if exists
      if (this.ytPlayer) {
        try { this.ytPlayer.destroy(); } catch (_) {}
        this.ytPlayer = null;
        this.ytReady = false;
        // Re-create container since destroy removes the DOM element
        const old = document.getElementById(this.ytContainerId);
        if (old) old.remove();
        this.ensureYtContainer();
      }

      this.ytPlayer = new (window as any).YT.Player(this.ytContainerId, {
        height: '1', width: '1',
        videoId: videoId,
        playerVars: { autoplay: 0, controls: 0, disablekb: 1, fs: 0, modestbranding: 1 },
        events: {
          onReady: () => {
            this.ytReady = true;
            this.ytPlayer.setVolume(this.state.volume * 100);
            this.patch({ loading: false, duration: this.ytPlayer.getDuration() || 0 });
          },
          onStateChange: (event: any) => this.onYtStateChange(event),
          onError: (event: any) => {
            const codes: Record<number, string> = {
              2: 'ID de video invalido', 5: 'Error del reproductor HTML5',
              100: 'Video no encontrado', 101: 'Reproduccion restringida', 150: 'Reproduccion restringida'
            };
            this.patch({ playing: false, loading: false, error: codes[event.data] || 'Error de YouTube (' + event.data + ')' });
          }
        }
      });
    }

    this.startYtPolling();
  }

  private ensureYtContainer() {
    if (!document.getElementById(this.ytContainerId)) {
      const div = document.createElement('div');
      div.id = this.ytContainerId;
      div.style.position = 'absolute';
      div.style.left = '-9999px';
      div.style.width = '1px';
      div.style.height = '1px';
      document.body.appendChild(div);
    }
  }

  private onYtStateChange(event: any) {
    const YT = (window as any).YT?.PlayerState;
    if (!YT) return;
    switch (event.data) {
      case YT.PLAYING:
        this.patch({ playing: true, error: null, loading: false, duration: this.ytPlayer.getDuration() || 0 });
        break;
      case YT.PAUSED:
        this.patch({ playing: false });
        break;
      case YT.ENDED:
        this.patch({ playing: false, currentTime: 0 });
        if (this.onTrackEnded) this.onTrackEnded();
        break;
      case YT.BUFFERING:
        this.patch({ loading: true });
        break;
      case YT.CUED:
        this.patch({ loading: false });
        break;
    }
  }

  private startYtPolling() {
    this.stopYtPolling();
    this.ytInterval = setInterval(() => {
      if (this.ytPlayer && this.ytReady && this.activeSource === 'youtube') {
        const t = this.ytPlayer.getCurrentTime?.() || 0;
        const d = this.ytPlayer.getDuration?.() || 0;
        this.patch({ currentTime: t, duration: d });
        this.checkLoop();
      }
    }, 250);
  }

  private stopYtPolling() {
    if (this.ytInterval) { clearInterval(this.ytInterval); this.ytInterval = null; }
  }

  // ── Controls ──────────────────────────────────────────────────────────────

  play() {
    const doPlay = () => {
      if (this.activeSource === 'youtube') {
        if (this.ytPlayer && this.ytReady) this.ytPlayer.playVideo();
      } else {
        if (!this.audio.src) return;
        this.audio.play().catch(err => {
          this.patch({ playing: false, error: 'No se pudo reproducir: ' + (err.message || err) });
        });
      }
    };

    if (this.audioCtx && this.audioCtx.state === 'suspended') {
      this.audioCtx.resume().then(doPlay).catch(() => doPlay());
    } else {
      doPlay();
    }
  }

  pause() {
    if (this.activeSource === 'youtube') {
      if (this.ytPlayer && this.ytReady) this.ytPlayer.pauseVideo();
    } else {
      this.audio.pause();
    }
  }

  togglePlay() { this.state.playing ? this.pause() : this.play(); }

  seek(time: number) {
    if (this.activeSource === 'youtube') {
      if (this.ytPlayer && this.ytReady) this.ytPlayer.seekTo(time, true);
    } else {
      this.audio.currentTime = time;
    }
  }

  setVolume(vol: number) {
    vol = Math.max(0, Math.min(1, vol));
    if (this.activeSource === 'youtube') {
      if (this.ytPlayer && this.ytReady) this.ytPlayer.setVolume(vol * 100);
    } else {
      this.audio.volume = vol;
    }
    this.patch({ volume: vol });
  }

  cue() {
    this.seek(0);
    if (this.state.playing) this.pause();
  }

  getFrequencyData(): Uint8Array | null {
    if (!this.analyserNode) return null;
    if (!this.analyserData) {
      this.analyserData = new Uint8Array(this.analyserNode.frequencyBinCount);
    }
    this.analyserNode.getByteFrequencyData(this.analyserData);
    return this.analyserData;
  }

  getTimeDomainData(): Uint8Array | null {
    if (!this.analyserNode) return null;
    const buf = new Uint8Array(this.analyserNode.fftSize);
    this.analyserNode.getByteTimeDomainData(buf);
    return buf;
  }

  setEq(band: 'low' | 'mid' | 'high', dB: number) {
    if (!this.audioCtx) return;
    if (band === 'low' && this.filterLow) this.filterLow.gain.value = dB;
    if (band === 'mid' && this.filterMid) this.filterMid.gain.value = dB;
    if (band === 'high' && this.filterHigh) this.filterHigh.gain.value = dB;
  }

  setPlaybackRate(rate: number) {
    rate = Math.max(0.1, Math.min(4, rate));
    if (this.activeSource === 'youtube') {
      if (this.ytPlayer && this.ytReady) {
        // YouTube supports fixed rates: 0.25, 0.5, 1, 1.5, 2
        // We find the closest supported one
        const supported = this.ytPlayer.getAvailablePlaybackRates?.() || [0.25, 0.5, 1, 1.5, 2];
        const closest = supported.reduce((prev: number, curr: number) => 
          Math.abs(curr - rate) < Math.abs(prev - rate) ? curr : prev
        );
        this.ytPlayer.setPlaybackRate(closest);
        this.patch({ playbackRate: closest });
      }
    } else {
      this.audio.playbackRate = rate;
      this.patch({ playbackRate: rate });
    }
  }

  // ── Advanced FX ───────────────────────────────────────────────────────────

  setComboFilter(value: number) {
    // value: -100 (LowPass max) to 0 (flat) to +100 (HighPass max)
    if (!this.comboFilter) return;

    if (Math.abs(value) < 1) {
      this.comboFilter.type = 'allpass';
    } else if (value < 0) {
      // LOW PASS: decrease freq as value goes to -100
      this.comboFilter.type = 'lowpass';
      // Map -1 to -100 into 20000 to 200
      const freq = 20000 * Math.pow(10, (value / 50)); 
      this.comboFilter.frequency.setTargetAtTime(Math.max(200, freq), this.audioCtx!.currentTime, 0.05);
    } else {
      // HIGH PASS: increase freq as value goes to +100
      this.comboFilter.type = 'highpass';
      // Map 1 to 100 into 20 to 5000
      const freq = 20 * Math.pow(10, (value / 40));
      this.comboFilter.frequency.setTargetAtTime(Math.min(10000, freq), this.audioCtx!.currentTime, 0.05);
    }
  }

  setDelayFX(level: number, feedback: number, time: number) {
    // level: 0 to 1, feedback: 0 to 0.9, time: 0.1 to 2.0
    if (!this.wetGain || !this.dryGain || !this.delayNode || !this.delayGain) return;
    this.wetGain.gain.setTargetAtTime(level, this.audioCtx!.currentTime, 0.05);
    this.dryGain.gain.setTargetAtTime(1 - (level * 0.5), this.audioCtx!.currentTime, 0.05);
    this.delayGain.gain.value = Math.min(0.9, feedback);
    this.delayNode.delayTime.setTargetAtTime(Math.max(0.01, time), this.audioCtx!.currentTime, 0.1);
  }

  // ── Looping ───────────────────────────────────────────────────────────────

  setLoop(start: number, end: number, active: boolean) {
    this.loopStart = start;
    this.loopEnd = end;
    this.loopActive = active;
    this.patch({ loopActive: active, loopStart: start, loopEnd: end });
    
    // If we are currently outside the loop, seek to start
    if (active && (this.state.currentTime < start || this.state.currentTime > end)) {
      this.seek(start);
    }
  }

  disableLoop() {
    this.loopActive = false;
    this.patch({ loopActive: false });
  }

  private checkLoop() {
    if (!this.loopActive) return;
    const t = this.state.currentTime;
    if (t >= this.loopEnd) {
      this.seek(this.loopStart);
    }
  }

  private stopAll() {
    this.audio.pause();
    this.audio.src = '';
    this.stopYtPolling();
    if (this.ytPlayer && this.ytReady) {
      try { this.ytPlayer.stopVideo(); } catch (_) {}
    }
  }
}

// ── MusicService ──────────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class MusicService {

  readonly BASE: string = (() => {
    const host = typeof window !== 'undefined' ? window.location.hostname : '';
    const port = typeof window !== 'undefined' ? window.location.port : '';
    // Use absolute URL only when running the Angular dev server (port 4200).
    // In Docker/Caddy (https://localhost) or any other host, use relative URLs
    // so the browser stays same-origin and CORS / mixed-content issues are avoided.
    return (host === 'localhost' || host === '127.0.0.1') && port === '4200'
      ? 'http://localhost:8080'
      : '';
  })();

  private readonly api = `${this.BASE}/api/music`;

  // Library main player
  public mainPlayer: DeckPlayer;
  // Deck players (two simultaneous, NAS + YouTube)
  public deckAPlayer: DeckPlayer;
  public deckBPlayer: DeckPlayer;

  // Library queue
  private queueSubj = new BehaviorSubject<{ tracks: MusicMetadataDto[]; pathId: number; index: number }>({
    tracks: [], pathId: 0, index: -1
  });
  public queue$ = this.queueSubj.asObservable();

  // Shuffle & Repeat
  private _shuffle = false;
  private _repeat: 'none' | 'one' | 'all' = 'none';
  private shuffleOrder: number[] = [];
  private shuffleSubj  = new BehaviorSubject<boolean>(false);
  private repeatSubj   = new BehaviorSubject<'none' | 'one' | 'all'>('none');
  public shuffle$ = this.shuffleSubj.asObservable();
  public repeat$  = this.repeatSubj.asObservable();
  get shuffle() { return this._shuffle; }
  get repeat()  { return this._repeat; }

  coverOverrideMap = new Map<string, string>();
  /** Emite el trackPath cada vez que se guarda una portada nueva (iTunes o manual) */
  readonly coverReady$ = new Subject<string>();
  /** Términos ya buscados esta sesión (evita duplicados en memoria) */
  private itunesFetchedTerms = new Set<string>();
  /** Paths de canciones que iTunes confirmó que no tiene (persistido en localStorage) */
  private itunesNotFoundPaths = new Set<string>();
  private folderCoverBust = new Map<string, number>();
  private static readonly COVER_CACHE_KEY = 'ev_covers_v2';
  private static readonly COVER_NOT_FOUND_KEY = 'ev_covers_nf_v1';

  constructor(private http: HttpClient, private auth: AuthService) {
    this.mainPlayer  = new DeckPlayer(this, 'main');
    this.deckAPlayer = new DeckPlayer(this, 'deckA');
    this.deckBPlayer = new DeckPlayer(this, 'deckB');

    // Cargar caché de portadas y "no encontradas" de localStorage
    try {
      const saved = JSON.parse(localStorage.getItem(MusicService.COVER_CACHE_KEY) || '{}');
      Object.entries(saved).forEach(([path, url]) => this.coverOverrideMap.set(path, url as string));
    } catch {}
    try {
      const nf = JSON.parse(localStorage.getItem(MusicService.COVER_NOT_FOUND_KEY) || '[]');
      (nf as string[]).forEach(p => this.itunesNotFoundPaths.add(p));
    } catch {}

    // Auto-advance queue when main player track ends naturally
    this.mainPlayer.onTrackEnded = () => {
      // Capture the finished track BEFORE advancing to the next one
      const finishedTrack = this.mainPlayer.state.currentTrack;
      const finishedPathId = this.mainPlayer.state.pathId;
      this.recordHistory(finishedTrack, finishedPathId);
      this.playNextMain();
    };

    this.loadPersistedState();
    this.setupMediaSession();
    this.setupNowPlayingNotifications();
  }

  private setupMediaSession(): void {
    if (typeof navigator === 'undefined' || !('mediaSession' in navigator)) return;

    navigator.mediaSession.setActionHandler('play',          () => this.mainPlayer.play());
    navigator.mediaSession.setActionHandler('pause',         () => this.mainPlayer.pause());
    navigator.mediaSession.setActionHandler('nexttrack',     () => this.playNextMain());
    navigator.mediaSession.setActionHandler('previoustrack', () => this.playPrevMain());
    navigator.mediaSession.setActionHandler('seekto', (details) => {
      if (details.seekTime != null) this.mainPlayer.seek(details.seekTime);
    });

    this.mainPlayer.state$.subscribe(state => {
      navigator.mediaSession.playbackState = state.playing ? 'playing' : 'paused';

      if (state.currentTrack) {
        const track = state.currentTrack;
        const pathId = state.pathId ?? 0;
        const artworkUrl = this.getAbsoluteCoverUrl(pathId, track);
        const artwork: MediaImage[] = artworkUrl ? [{ src: artworkUrl, sizes: '512x512', type: 'image/jpeg' }] : [];
        navigator.mediaSession.metadata = new MediaMetadata({
          title:  track.title  || track.name,
          artist: track.artist || '',
          album:  track.album  || '',
          artwork,
        });
      }
    });
  }

  private setupNowPlayingNotifications(): void {
    if (typeof window === 'undefined' || !('Notification' in window)) return;

    let lastNotifiedPath: string | null = null;
    let activeNotification: Notification | null = null;

    this.mainPlayer.state$.subscribe(state => {
      if (!state.playing || !state.currentTrack) return;
      if (state.currentTrack.path === lastNotifiedPath) return;

      lastNotifiedPath = state.currentTrack.path;
      this.showNowPlayingNotification(state.currentTrack, state.pathId ?? 0, (n) => {
        activeNotification?.close();
        activeNotification = n;
      });
    });
  }

  private showNowPlayingNotification(
    track: MusicMetadataDto,
    pathId: number,
    onCreated: (n: Notification) => void
  ): void {
    const doShow = () => {
      if (Notification.permission !== 'granted') return;
      const icon = this.getAbsoluteCoverUrl(pathId, track) || undefined;
      const n = new Notification(track.title || track.name, {
        body:  [track.artist, track.album].filter(Boolean).join(' — '),
        icon,
        image: icon,
        silent: true,
        tag:   'now-playing',
      } as NotificationOptions);
      setTimeout(() => n.close(), 5000);
      onCreated(n);
    };

    if (Notification.permission === 'granted') {
      doShow();
    } else if (Notification.permission === 'default') {
      Notification.requestPermission().then(perm => { if (perm === 'granted') doShow(); });
    }
  }

  private getAbsoluteCoverUrl(pathId: number, track: MusicMetadataDto): string {
    const override = this.coverOverrideMap.get(track.path);
    if (override) return override; // iTunes URLs are already absolute

    if (track.source === 'youtube') {
      return `https://img.youtube.com/vi/${track.path}/hqdefault.jpg`;
    }

    if (!track.hasCover) return '';

    const relative = this.getCoverUrl(pathId, track.path, track.source);
    if (relative.startsWith('http')) return relative;
    return window.location.origin + relative;
  }

  private loadPersistedState() {
    try {
      const savedQueue = localStorage.getItem('ev_queue');
      const savedRepeat = localStorage.getItem('ev_repeat');
      const savedShuffle = localStorage.getItem('ev_shuffle');
      if (savedQueue) {
        const q = JSON.parse(savedQueue);
        if (q && q.tracks && q.tracks.length > 0) {
          this.queueSubj.next(q);
        }
      }
      if (savedRepeat) this.repeatSubj.next(savedRepeat as any);
      if (savedShuffle) {
        this._shuffle = savedShuffle === 'true';
        this.shuffleSubj.next(this._shuffle);
        if (this._shuffle) this.buildShuffleOrder();
      }
    } catch (e) {
      console.warn('Could not load player state from localStorage', e);
    }
  }

  private persistState() {
    try {
      localStorage.setItem('ev_queue', JSON.stringify(this.queueSubj.value));
      localStorage.setItem('ev_repeat', this._repeat);
      localStorage.setItem('ev_shuffle', String(this._shuffle));
    } catch (e) {}
  }

  toggleShuffle() {
    this._shuffle = !this._shuffle;
    if (this._shuffle) this.buildShuffleOrder();
    this.shuffleSubj.next(this._shuffle);
    this.persistState();
  }

  toggleRepeat() {
    const modes: ('none' | 'one' | 'all')[] = ['none', 'one', 'all'];
    this._repeat = modes[(modes.indexOf(this._repeat) + 1) % modes.length];
    this.repeatSubj.next(this._repeat);
    this.persistState();
  }

  private buildShuffleOrder() {
    const q = this.queueSubj.value;
    const rest = q.tracks.map((_, i) => i).filter(i => i !== q.index);
    for (let i = rest.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [rest[i], rest[j]] = [rest[j], rest[i]];
    }
    this.shuffleOrder = [q.index, ...rest];
  }

  // ── API calls ─────────────────────────────────────────────────────────────

  getRandomTracks(count = 3): Observable<MusicMetadataDto[]> {
    return this.http.get<MusicMetadataDto[]>(`${this.api}/random?count=${count}`);
  }

  browse(pathId: number, subPath?: string, page = 0, size = 50): Observable<PagedMusicResult> {
    let url = `${this.api}/metadata?pathId=${pathId}&page=${page}&size=${size}`;
    if (subPath) url += `&subPath=${encodeURIComponent(subPath)}`;
    return this.http.get<PagedMusicResult>(url);
  }

  getStreamUrl(pathId: number, trackPath: string): string {
    const token = this.auth.getToken();
    return `${this.api}/stream?pathId=${pathId}&subPath=${encodeURIComponent(trackPath)}&token=${token}`;
  }

  getCoverUrl(pathId: number, trackPath: string, source?: string): string {
    const token = this.auth.getToken();
    if (source === 'youtube') {
      return `https://img.youtube.com/vi/${trackPath}/hqdefault.jpg`;
    }
    return `${this.api}/cover?pathId=${pathId}&subPath=${encodeURIComponent(trackPath)}&token=${token}`;
  }

  getFolderCoverUrl(pathId: number, folderPath: string): string {
    const token = this.auth.getToken();
    const key = `${pathId}:${folderPath}`;
    const bust = this.folderCoverBust.get(key);
    const bustParam = bust ? `&v=${bust}` : '';
    return `${this.api}/folder-cover?pathId=${pathId}&subPath=${encodeURIComponent(folderPath)}&token=${token}${bustParam}`;
  }

  invalidateFolderCover(pathId: number, folderPath: string): void {
    this.folderCoverBust.set(`${pathId}:${folderPath}`, Date.now());
  }

  // ── Cover fetching logic (universal) ──────────────────────────────────────

  getCoverUrlWithCache(pathId: number, trackPath: string, source?: string): string {
    if (this.coverOverrideMap.has(trackPath)) return this.coverOverrideMap.get(trackPath)!;
    return this.getCoverUrl(pathId, trackPath, source);
  }

  hasCoverToShow(track: MusicMetadataDto): boolean {
    return track.hasCover || this.coverOverrideMap.has(track.path);
  }

  fetchCoverIfNeeded(track: MusicMetadataDto): void {
    if (!track || track.hasCover || this.coverOverrideMap.has(track.path)) return;
    // Saltar canciones que iTunes ya confirmó que no tiene
    if (this.itunesNotFoundPaths.has(track.path)) return;

    let artist = (track.artist || '').trim();
    const title  = (track.title  || track.name || '').trim();
    const album  = (track.album  || '').trim();
    if (!title && !artist) return;

    if (!artist && title.includes(' - ')) {
      artist = title.substring(0, title.indexOf(' - ')).trim();
    }

    // Evitar peticiones duplicadas dentro de la misma sesión
    const dedupeKey = `${artist}|${title}`;
    if (this.itunesFetchedTerms.has(dedupeKey)) return;
    this.itunesFetchedTerms.add(dedupeKey);

    const itunesSearch = (term: string, entity: string): Promise<string | null> =>
      fetch(`https://itunes.apple.com/search?term=${encodeURIComponent(term)}&entity=${entity}&limit=3`)
        .then(r => {
          // Si iTunes nos rate-limita, lanzar error especial para no marcar como "no encontrada"
          if (r.status === 429) throw new Error('rate_limited');
          return r.json();
        })
        .then(d => {
          const r = d.results?.[0];
          if (!r) return null;
          const art = r.artworkUrl100 || r.artworkUrl60 || r.artworkUrl30;
          return art ? art.replace(/\d+x\d+bb/, '600x600bb') : null;
        });

    const save = (url: string) => {
      this.coverOverrideMap.set(track.path, url);
      this.coverReady$.next(track.path);
      try {
        const cache = JSON.parse(localStorage.getItem(MusicService.COVER_CACHE_KEY) || '{}');
        cache[track.path] = url;
        localStorage.setItem(MusicService.COVER_CACHE_KEY, JSON.stringify(cache));
      } catch {}
    };

    const saveNotFound = () => {
      this.itunesNotFoundPaths.add(track.path);
      try {
        const nf = JSON.parse(localStorage.getItem(MusicService.COVER_NOT_FOUND_KEY) || '[]') as string[];
        if (!nf.includes(track.path)) {
          nf.push(track.path);
          localStorage.setItem(MusicService.COVER_NOT_FOUND_KEY, JSON.stringify(nf));
        }
      } catch {}
    };

    // Cascada: artista+título → artista+álbum → solo título → solo artista
    const cleanTitle = title.replace(/\b(session|live|set|mix|festival|dj\s*set|\d{4})\b/gi, '').trim();
    const term1 = artist && cleanTitle ? `${artist} ${cleanTitle}` : (cleanTitle || artist);
    itunesSearch(term1, 'song')
      .then(url => {
        if (url) { save(url); return; }
        const fallbacks: Promise<string | null>[] = [];
        if (album) fallbacks.push(itunesSearch(artist ? `${artist} ${album}` : album, 'album'));
        if (artist && cleanTitle && cleanTitle !== term1) fallbacks.push(itunesSearch(cleanTitle, 'song'));
        if (artist) fallbacks.push(itunesSearch(artist, 'album'));
        return fallbacks
          .reduce(
            (chain, next) => chain.then(u => u != null ? u : next),
            Promise.resolve<string | null>(null)
          )
          .then(u => {
            if (u) save(u);
            else saveNotFound(); // toda la cascada agotada sin resultado
          });
      })
      .catch(err => {
        // Rate limit: quitar de itunesFetchedTerms para que se reintente más adelante
        if (err?.message === 'rate_limited') {
          this.itunesFetchedTerms.delete(dedupeKey);
        }
        // Cualquier otro error de red: silencioso, se reintentará la próxima sesión
      });
  }

  // ── AcoustID fingerprinting ───────────────────────────────────────────────

  fingerprintTrack(pathId: number, subPath: string): Observable<any> {
    const params = new URLSearchParams({ pathId: String(pathId), subPath });
    return this.http.post<any>(`${this.BASE}/api/music/fingerprint?${params}`, {});
  }

  // ── NAS yt-dlp async jobs ─────────────────────────────────────────────────

  searchYouTube(query: string, maxResults = 8): Observable<any> {
    const url = `${this.api.replace('/api/music', '/api/youtube').replace('/music', '/youtube')}/search?query=${encodeURIComponent(query)}&maxResults=${maxResults}`;
    return this.http.get<any>(url);
  }

  ytDlpQueue(videoId: string, title: string, nasPathId: number, subPath: string, format: string): Observable<{jobId: string}> {
    const p = new URLSearchParams({ videoId, title, nasPathId: String(nasPathId), subPath, format });
    return this.http.post<{jobId: string}>(`${this.BASE}/api/nas/ytdlp/queue?${p}`, {});
  }

  ytDlpActiveJobs(): Observable<any[]> {
    return this.http.get<any[]>(`${this.BASE}/api/nas/ytdlp/active`);
  }

  // ── Favorites & History API ───────────────────────────────────────────────

  getFavorites(): Observable<any[]> {
    return this.http.get<any[]>(`${this.api.replace('/music', '/library')}/favorites`);
  }

  toggleFavorite(trackPath: string, title: string, artist: string, album: string, nasPathId: number): Observable<any> {
    return this.http.post(`${this.api.replace('/music', '/library')}/favorites/toggle`, {
      trackPath, title, artist, album, nasPathId
    });
  }

  checkFavorite(trackPath: string, nasPathId: number): Observable<any> {
    return this.http.get(`${this.api.replace('/music', '/library')}/favorites/check?trackPath=${encodeURIComponent(trackPath)}&nasPathId=${nasPathId}`);
  }

  getHistory(limit: number = 50): Observable<any[]> {
    return this.http.get<any[]>(`${this.api.replace('/music', '/library')}/history?limit=${limit}`);
  }

  recordHistory(track: MusicMetadataDto | null, pathId: number | null) {
    if (!track || pathId == null || pathId < 0) return;
    this.http.post(`${this.api.replace('/music', '/library')}/history`, {
      trackPath: track.path,
      title: track.title || track.name,
      artist: track.artist || '',
      album: track.album || '',
      nasPathId: pathId,
      durationSeconds: track.duration || 0,
      completed: true
    }).subscribe({ error: () => {} });
  }


  // ── Queue / Library controls ──────────────────────────────────────────────

  setQueue(pathId: number, tracks: MusicMetadataDto[], index: number) {
    this.queueSubj.next({ tracks, pathId, index });
    if (this._shuffle) this.buildShuffleOrder();
    this.persistState();
    if (tracks[index]) {
      this.mainPlayer.load(tracks[index], pathId).then(() => {
        this.mainPlayer.play();
      });
    }
  }

  playNextMain() {
    const q = this.queueSubj.value;
    if (this._repeat === 'one') {
      this.mainPlayer.seek(0);
      this.mainPlayer.play();
      return;
    }
    if (this._shuffle) {
      const pos = this.shuffleOrder.indexOf(q.index);
      const next = pos + 1;
      if (next < this.shuffleOrder.length) {
        this.setQueue(q.pathId, q.tracks, this.shuffleOrder[next]);
      } else if (this._repeat === 'all') {
        this.buildShuffleOrder();
        this.setQueue(q.pathId, q.tracks, this.shuffleOrder[0]);
      }
    } else {
      if (q.index < q.tracks.length - 1) {
        this.setQueue(q.pathId, q.tracks, q.index + 1);
      } else if (this._repeat === 'all') {
        this.setQueue(q.pathId, q.tracks, 0);
      }
    }
  }

  playPrevMain() {
    const q = this.queueSubj.value;
    if (this._shuffle) {
      const pos = this.shuffleOrder.indexOf(q.index);
      if (pos > 0) this.setQueue(q.pathId, q.tracks, this.shuffleOrder[pos - 1]);
      else this.mainPlayer.seek(0);
    } else if (q.index > 0) {
      this.setQueue(q.pathId, q.tracks, q.index - 1);
    } else {
      this.mainPlayer.seek(0);
    }
  }

  // ── Crossfader ────────────────────────────────────────────────────────────

  /**
   * Equal-power crossfade.
   * value: -1 (full Deck A) ... 0 (equal) ... +1 (full Deck B)
   */
  crossfade(value: number) {
    const t = (value + 1) / 2;
    const volA = Math.cos(t * Math.PI / 2);
    const volB = Math.cos((1 - t) * Math.PI / 2);
    this.deckAPlayer.setVolume(volA);
    this.deckBPlayer.setVolume(volB);
  }
}