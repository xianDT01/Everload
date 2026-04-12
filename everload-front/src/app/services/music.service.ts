import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';
import { BehaviorSubject, Observable } from 'rxjs';

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
      this.analyserNode.fftSize = 128;
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

  private readonly BASE: string = (() => {
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

  constructor(private http: HttpClient, private auth: AuthService) {
    this.mainPlayer  = new DeckPlayer(this, 'main');
    this.deckAPlayer = new DeckPlayer(this, 'deckA');
    this.deckBPlayer = new DeckPlayer(this, 'deckB');

    // Auto-advance queue when main player track ends naturally
    this.mainPlayer.onTrackEnded = () => this.playNextMain();
  }

  toggleShuffle() {
    this._shuffle = !this._shuffle;
    if (this._shuffle) this.buildShuffleOrder();
    this.shuffleSubj.next(this._shuffle);
  }

  toggleRepeat() {
    const modes: ('none' | 'one' | 'all')[] = ['none', 'one', 'all'];
    this._repeat = modes[(modes.indexOf(this._repeat) + 1) % modes.length];
    this.repeatSubj.next(this._repeat);
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

  browse(pathId: number, subPath?: string): Observable<MusicMetadataDto[]> {
    let url = `${this.api}/metadata?pathId=${pathId}`;
    if (subPath) url += `&subPath=${encodeURIComponent(subPath)}`;
    return this.http.get<MusicMetadataDto[]>(url);
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

  // ── Queue / Library controls ──────────────────────────────────────────────

  setQueue(pathId: number, tracks: MusicMetadataDto[], index: number) {
    this.queueSubj.next({ tracks, pathId, index });
    if (this._shuffle) this.buildShuffleOrder();
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
    if (this.mainPlayer.state.currentTime > 3) {
      this.mainPlayer.seek(0);
    } else if (this._shuffle) {
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