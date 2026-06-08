import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';
import { BehaviorSubject, firstValueFrom, from, mergeMap, Observable, of, Subject, tap, shareReplay } from 'rxjs';
import { ApiBaseService } from './api-base.service';

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
  source?: 'nas' | 'youtube' | 'local' | 'ytmusic';
  localHandle?: any;
  nasPathId?: number; // Set when track comes from favorites/history
  thumbnailUrl?: string;
}

export interface YtMusicTrackDto {
  videoId: string;
  title: string;
  artist: string;
  artists?: string[];
  album?: string;
  albumId?: string;
  durationSeconds?: number;
  thumbnailUrl?: string;
}

export interface YtMusicDiscoverItemDto {
  type: 'SONG' | 'PLAYLIST' | 'ALBUM' | 'ARTIST' | 'MOOD';
  title: string;
  subtitle?: string;
  thumbnailUrl?: string;
  track?: YtMusicTrackDto;
  playlistId?: string;
  browseId?: string;
  channelId?: string;
  moodBrowseId?: string;
}

export interface YtMusicDiscoverShelfDto {
  title: string;
  strapline?: string;
  moreBrowseId?: string;
  items: YtMusicDiscoverItemDto[];
}

export interface YtMusicDiscoverHomeDto {
  shelves: YtMusicDiscoverShelfDto[];
  continuation?: string;
}

export interface YtMusicAlbumDto {
  browseId: string;
  title: string;
  artist?: string;
  year?: string;
  thumbnailUrl?: string;
  tracks: YtMusicTrackDto[];
}

export interface YtMusicArtistDto {
  channelId: string;
  name: string;
  description?: string;
  thumbnailUrl?: string;
  topSongs: YtMusicTrackDto[];
  albums: YtMusicAlbumDto[];
}

export interface YtMusicStreamInfoDto {
  url: string;
  format?: string;
  userAgent?: string;
  contentLength?: number;
  durationSeconds?: number;
  resolvedBy?: string;
}

export interface ArtistProfileDto {
  id: number;
  name: string;
  aliases: string;
  description: string;
  imageUrl: string;
}

export interface LibraryOverviewDto {
  tracks: MusicMetadataDto[];
  indexing: boolean;
}

export interface ArtistImageLookupDto {
  found: boolean;
  imageUrl?: string;
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

interface HlsPrepareResult {
  key: string;
  eligible: boolean;
  status: 'DIRECT' | 'IDLE' | 'RUNNING' | 'READY' | 'FAILED';
  ready: boolean;
  progress: number;
  durationSeconds: number;
  fileSizeBytes: number;
  error?: string;
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
  private activeSource: 'nas' | 'youtube' | 'local' | 'ytmusic' | null = null;
  private loadNonce = 0;
  private hls: any = null;
  private hlsCtorPromise?: Promise<any | null>;
  private hlsPollTimer?: ReturnType<typeof setTimeout>;

  private audioCtx: AudioContext | null = null;
  private analyserNode: AnalyserNode | null = null;
  private analyserData: Uint8Array | null = null;

  // EQ Filters (5-band: 60Hz, 250Hz, 1kHz, 4kHz, 16kHz)
  private filterBands: BiquadFilterNode[] = [];

  // Channel Mode
  private channelSplitter: ChannelSplitterNode | null = null;
  private channelMerger: ChannelMergerNode | null = null;
  private channelModeGains: GainNode[] = [];
  channelMode: 'stereo' | 'mono' | 'left' | 'right' | 'swap' = 'stereo';

  // Master gain — used for crossfade in/out (separate from user volume)
  private masterGain: GainNode | null = null;

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
    this.audio.preload = 'metadata';
    this.audio.crossOrigin = 'anonymous'; // Important for CORS Web Audio
    this.setupAudioNodes();

    this.audio.addEventListener('timeupdate',     () => {
      this.patch({ currentTime: this.audio.currentTime });
      this.checkLoop();
    });
    this.audio.addEventListener('play',           () => this.patch({ playing: true, loading: false, error: null }));
    this.audio.addEventListener('playing',        () => this.patch({ playing: true, loading: false, error: null }));
    this.audio.addEventListener('pause',          () => this.patch({ playing: false }));
    this.audio.addEventListener('loadedmetadata', () => this.patch({ duration: this.audio.duration, loading: false }));
    this.audio.addEventListener('canplay',        () => this.patch({ loading: false }));
    this.audio.addEventListener('seeked',         () => this.patch({ loading: false, currentTime: this.audio.currentTime }));
    this.audio.addEventListener('seeking',        () => {
      if (this.state.currentTrack) this.patch({ loading: true });
    });
    this.audio.addEventListener('waiting',        () => {
      if (this.state.currentTrack) this.patch({ loading: true });
    });
    this.audio.addEventListener('stalled',        () => {
      if (this.state.currentTrack) this.patch({ loading: true });
    });
    this.audio.addEventListener('volumechange',   () => this.patch({ volume: this.audio.volume }));
    this.audio.addEventListener('ended',          () => {
      this.patch({ playing: false, currentTime: 0 });
      if (this.onTrackEnded) this.onTrackEnded();
    });
    this.audio.addEventListener('error', () => {
      if (!this.audio.currentSrc && !this.audio.src) return;
      const e = this.audio.error;
      const msg = e ? `Audio error (code ${e.code}): ${e.message || 'stream no disponible'}` : 'Error de reproduccion';
      this.patch({ playing: false, loading: false, error: msg });
    });

    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', () => {
        if (!document.hidden && this.audioCtx?.state === 'suspended') {
          this.audioCtx.resume().catch(() => {});
        }
      });
    }

    if (this.audioCtx) {
      this.audioCtx.addEventListener('statechange', () => {
        if (this.audioCtx?.state === 'suspended' && !document.hidden) {
          this.audioCtx.resume().catch(() => {});
        }
      });
    }
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

      // 5-band EQ: 60Hz lowshelf, 250Hz peaking, 1kHz peaking, 4kHz peaking, 16kHz highshelf
      const bandDefs: Array<{ type: BiquadFilterType; freq: number; Q?: number }> = [
        { type: 'lowshelf',  freq: 60 },
        { type: 'peaking',   freq: 250,   Q: 1.0 },
        { type: 'peaking',   freq: 1000,  Q: 1.0 },
        { type: 'peaking',   freq: 4000,  Q: 1.0 },
        { type: 'highshelf', freq: 16000 },
      ];
      this.filterBands = bandDefs.map(def => {
        const f = this.audioCtx!.createBiquadFilter();
        f.type = def.type;
        f.frequency.value = def.freq;
        if (def.Q !== undefined) f.Q.value = def.Q;
        f.gain.value = 0;
        return f;
      });

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

      // Routing: source -> 5-band EQ chain -> ComboFilter -> [Dry / Wet(Delay)] -> Analyser -> Destination
      source.connect(this.filterBands[0]);
      for (let i = 0; i < this.filterBands.length - 1; i++) {
        this.filterBands[i].connect(this.filterBands[i + 1]);
      }
      this.filterBands[this.filterBands.length - 1].connect(this.comboFilter);
      
      // FX Routing
      this.comboFilter.connect(this.dryGain);
      this.comboFilter.connect(this.delayNode);
      this.delayNode.connect(this.delayGain);
      this.delayGain.connect(this.delayNode); // Feedback loop
      this.delayNode.connect(this.wetGain);

      this.dryGain.connect(this.analyserNode);
      this.wetGain.connect(this.analyserNode);

      // Channel mode routing: analyser → splitter → 4 gain crosspoints → merger → destination
      // channelModeGains = [gLL, gRL, gLR, gRR]
      this.channelSplitter = this.audioCtx.createChannelSplitter(2);
      this.channelMerger   = this.audioCtx.createChannelMerger(2);
      this.channelModeGains = [
        this.audioCtx.createGain(), // gLL: splitter[0] → merger[0]
        this.audioCtx.createGain(), // gRL: splitter[1] → merger[0]
        this.audioCtx.createGain(), // gLR: splitter[0] → merger[1]
        this.audioCtx.createGain(), // gRR: splitter[1] → merger[1]
      ];
      this.analyserNode.connect(this.channelSplitter);
      this.channelSplitter.connect(this.channelModeGains[0], 0);
      this.channelSplitter.connect(this.channelModeGains[1], 1);
      this.channelSplitter.connect(this.channelModeGains[2], 0);
      this.channelSplitter.connect(this.channelModeGains[3], 1);
      this.channelModeGains[0].connect(this.channelMerger, 0, 0);
      this.channelModeGains[1].connect(this.channelMerger, 0, 0);
      this.channelModeGains[2].connect(this.channelMerger, 0, 1);
      this.channelModeGains[3].connect(this.channelMerger, 0, 1);
      this.masterGain = this.audioCtx.createGain();
      this.channelMerger.connect(this.masterGain);
      this.masterGain.connect(this.audioCtx.destination);
      this.setChannelMode('stereo');
    } catch (e) {
      console.error('[DeckPlayer ' + this.deckId + '] Web Audio API setup failed — EQ will not work:', e);
      this.audioCtx = null;
    }
  }

  // ── Load ──────────────────────────────────────────────────────────────────

  async load(track: MusicMetadataDto, pathId: number) {
    const loadId = ++this.loadNonce;
    this.stopAll();
    this.resetMasterGain();
    this.patch({ currentTrack: track, pathId, currentTime: 0, playing: false, duration: 0, loading: true, error: null });

    if (track.source === 'youtube') {
      this.activeSource = 'youtube';
      await this.loadYoutube(track.path, loadId);
    } else if (track.source === 'ytmusic') {
      this.activeSource = 'ytmusic';
      await this.loadYtMusic(track, loadId);
    } else if (track.source === 'local') {
      this.activeSource = 'local';
      this.loadLocal(track, loadId);
    } else {
      this.activeSource = 'nas';
      await this.loadNas(track, pathId, loadId);
    }
  }

  private isStaleLoad(loadId: number): boolean {
    return loadId !== this.loadNonce;
  }

  private loadLocal(track: MusicMetadataDto, loadId: number) {
    if (this.isStaleLoad(loadId)) return;
    // Para local, track.path contiene directamente la URL de blob: generada
    this.audio.preload = 'auto';
    this.audio.src = track.path;
    this.audio.load();
  }

  private async loadNas(track: MusicMetadataDto, pathId: number, loadId: number) {
    if (this.isStaleLoad(loadId)) return;
    const url = this.musicService.getStreamUrl(pathId, track.path);
    const preferHls = this.musicService.shouldUseHls(track);

    if (!preferHls || !(await this.canPlayHls())) {
      this.loadDirectAudio(url);
      return;
    }

    try {
      const info = await firstValueFrom(this.musicService.prepareHls(pathId, track.path));
      if (this.isStaleLoad(loadId)) return;

      if (info.ready) {
        await this.loadHlsAudio(this.musicService.getHlsPlaylistUrl(pathId, track.path), loadId, url);
        return;
      }

      this.loadDirectAudio(url);
      this.pollHlsReady(track, pathId, loadId, url);
    } catch (_) {
      if (!this.isStaleLoad(loadId)) this.loadDirectAudio(url);
    }
  }

  private loadDirectAudio(url: string) {
    this.destroyHls();
    this.audio.preload = 'auto';
    this.audio.src = url;
    this.audio.load();
  }

  private async loadYtMusic(track: MusicMetadataDto, loadId: number) {
    if (this.isStaleLoad(loadId)) return;
    const url = this.musicService.getYtMusicAudioUrl(track.path);
    if (!url) {
      this.patch({ playing: false, loading: false, error: 'No se pudo preparar el stream' });
      return;
    }
    this.loadDirectAudio(url);
    if (track.duration) {
      this.patch({ duration: track.duration });
    }
  }

  private async canPlayHls(): Promise<boolean> {
    if (this.audio.canPlayType('application/vnd.apple.mpegurl') !== '') return true;
    const HlsCtor = await this.getHlsCtor();
    return !!HlsCtor?.isSupported?.();
  }

  private getHlsCtor(): Promise<any | null> {
    if (!this.hlsCtorPromise) {
      this.hlsCtorPromise = import('hls.js')
        .then(mod => mod.default)
        .catch(() => null);
    }
    return this.hlsCtorPromise;
  }

  private async loadHlsAudio(
    playlistUrl: string,
    loadId: number,
    fallbackUrl: string,
    resumeAt = 0,
    autoPlay = false
  ): Promise<void> {
    if (this.isStaleLoad(loadId)) return;

    this.destroyHls();
    this.audio.preload = 'auto';

    const ready = () => {
      if (this.isStaleLoad(loadId)) return;
      if (resumeAt > 0 && Number.isFinite(resumeAt)) {
        try { this.audio.currentTime = resumeAt; } catch (_) {}
      }
      this.patch({ loading: false });
      if (autoPlay) this.play();
    };

    if (this.audio.canPlayType('application/vnd.apple.mpegurl')) {
      this.audio.src = playlistUrl;
      this.audio.addEventListener('loadedmetadata', ready, { once: true });
      this.audio.load();
      return;
    }

    const HlsCtor = await this.getHlsCtor();
    if (!HlsCtor?.isSupported?.()) {
      this.loadDirectAudio(fallbackUrl);
      return;
    }

    const hls = new HlsCtor({
      maxBufferLength: 45,
      backBufferLength: 30,
      startPosition: resumeAt > 0 ? resumeAt : -1,
    });
    this.hls = hls;

    hls.on(HlsCtor.Events.MEDIA_ATTACHED, () => hls.loadSource(playlistUrl));
    hls.on(HlsCtor.Events.MANIFEST_PARSED, ready);
    hls.on(HlsCtor.Events.ERROR, (_event: any, data: any) => {
      if (!data?.fatal || this.isStaleLoad(loadId)) return;
      const current = this.audio.currentTime || resumeAt || 0;
      const wasPlaying = this.state.playing;
      this.destroyHls();
      this.loadDirectAudio(fallbackUrl);
      if (current > 0) {
        try { this.audio.currentTime = current; } catch (_) {}
      }
      if (wasPlaying) this.play();
    });

    hls.attachMedia(this.audio);
  }

  private pollHlsReady(
    track: MusicMetadataDto,
    pathId: number,
    loadId: number,
    fallbackUrl: string,
    attempt = 0
  ): void {
    if (this.hlsPollTimer) clearTimeout(this.hlsPollTimer);
    if (this.isStaleLoad(loadId) || attempt > 45) return;

    this.hlsPollTimer = setTimeout(async () => {
      this.hlsPollTimer = undefined;
      if (this.isStaleLoad(loadId) || this.state.currentTrack?.path !== track.path) return;

      try {
        const info = await firstValueFrom(this.musicService.getHlsStatus(pathId, track.path));
        if (this.isStaleLoad(loadId) || this.state.currentTrack?.path !== track.path) return;

        if (info.ready) {
          const current = this.state.currentTime || 0;
          const wasPlaying = this.state.playing;
          this.loadHlsAudio(this.musicService.getHlsPlaylistUrl(pathId, track.path), loadId, fallbackUrl, current, wasPlaying);
          return;
        }
        if (info.status === 'FAILED' || !info.eligible) return;
      } catch (_) {}

      this.pollHlsReady(track, pathId, loadId, fallbackUrl, attempt + 1);
    }, attempt < 3 ? 2500 : 5000);
  }

  private async loadYoutube(videoId: string, loadId: number) {
    await ensureYouTubeAPI();
    if (this.isStaleLoad(loadId)) return;
    this.ensureYtContainer();

    if (this.ytPlayer && this.ytReady) {
      this.ytPlayer.loadVideoById(videoId);
      if (!this.isStaleLoad(loadId)) this.patch({ loading: false });
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
            if (this.isStaleLoad(loadId)) return;
            this.ytReady = true;
            this.ytPlayer.setVolume(this.state.volume * 100);
            this.patch({ loading: false, duration: this.ytPlayer.getDuration() || 0 });
          },
          onStateChange: (event: any) => this.onYtStateChange(event),
          onError: (event: any) => {
            if (this.isStaleLoad(loadId)) return;
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

  resumeAudioContext(): Promise<void> {
    if (!this.audioCtx || this.audioCtx.state !== 'suspended') return Promise.resolve();
    return this.audioCtx.resume().catch(() => {});
  }

  seek(time: number) {
    if (!Number.isFinite(time)) return;
    if (this.activeSource === 'youtube') {
      if (this.ytPlayer && this.ytReady) this.ytPlayer.seekTo(time, true);
    } else {
      const duration = this.state.duration;
      const target = duration > 0 ? Math.max(0, Math.min(time, duration)) : Math.max(0, time);
      try {
        this.patch({ loading: true, currentTime: target });
        this.audio.currentTime = target;
      } catch (err: any) {
        this.patch({ loading: false, error: 'No se pudo saltar en la pista: ' + (err?.message || err) });
      }
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
    this.analyserNode.getByteFrequencyData(this.analyserData as any);
    return this.analyserData;
  }

  getTimeDomainData(): Uint8Array | null {
    if (!this.analyserNode) return null;
    const buf = new Uint8Array(this.analyserNode.fftSize);
    this.analyserNode.getByteTimeDomainData(buf);
    return buf;
  }

  setEqBand(index: number, dB: number) {
    const f = this.filterBands[index];
    if (f) f.gain.value = dB;
  }

  setEq(band: 'low' | 'mid' | 'high', dB: number) {
    const map: Record<string, number> = { low: 0, mid: 2, high: 4 };
    this.setEqBand(map[band], dB);
  }

  // ── Channel Mode ──────────────────────────────────────────────────────────
  // Gain matrix [gLL, gRL, gLR, gRR] — each row is an output channel (L, R)
  // splitter[0]=L, splitter[1]=R → merger[0]=L, merger[1]=R
  setChannelMode(mode: 'stereo' | 'mono' | 'left' | 'right' | 'swap') {
    this.channelMode = mode;
    const g = this.channelModeGains;
    if (!g.length) return;
    const t = this.audioCtx?.currentTime ?? 0;
    const matrices: Record<string, number[]> = {
      stereo: [1, 0, 0, 1],
      mono:   [0.5, 0.5, 0.5, 0.5],
      left:   [1, 0, 1, 0],
      right:  [0, 1, 0, 1],
      swap:   [0, 1, 1, 0],
    };
    const m = matrices[mode] ?? matrices['stereo'];
    m.forEach((v, i) => g[i].gain.setTargetAtTime(v, t, 0.02));
  }

  scheduleFadeOut(durationSec: number) {
    if (!this.masterGain || !this.audioCtx) return;
    const t = this.audioCtx.currentTime;
    this.masterGain.gain.cancelScheduledValues(t);
    this.masterGain.gain.setValueAtTime(this.masterGain.gain.value, t);
    this.masterGain.gain.linearRampToValueAtTime(0, t + Math.max(0.05, durationSec));
  }

  scheduleFadeIn(durationSec: number) {
    if (!this.masterGain || !this.audioCtx) return;
    const t = this.audioCtx.currentTime;
    this.masterGain.gain.cancelScheduledValues(t);
    this.masterGain.gain.setValueAtTime(0, t);
    this.masterGain.gain.linearRampToValueAtTime(1, t + Math.max(0.05, durationSec));
  }

  resetMasterGain() {
    if (!this.masterGain || !this.audioCtx) return;
    const t = this.audioCtx.currentTime;
    this.masterGain.gain.cancelScheduledValues(t);
    this.masterGain.gain.setValueAtTime(1, t);
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
    if (this.hlsPollTimer) {
      clearTimeout(this.hlsPollTimer);
      this.hlsPollTimer = undefined;
    }
    this.destroyHls();
    this.audio.pause();
    this.audio.removeAttribute('src');
    this.audio.load();
    this.stopYtPolling();
    if (this.ytPlayer && this.ytReady) {
      try { this.ytPlayer.stopVideo(); } catch (_) {}
    }
  }

  private destroyHls(): void {
    if (!this.hls) return;
    try { this.hls.destroy(); } catch (_) {}
    this.hls = null;
  }
}

// ── MusicService ──────────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class MusicService {

  get BASE(): string {
    return this.apiBase.backendUrl;
  }

  private get api(): string {
    return `${this.BASE}/api/music`;
  }

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
  get queueSnapshot(): { tracks: MusicMetadataDto[]; pathId: number; index: number } {
    return this.queueSubj.value;
  }

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

  nowPlayingPanelOpen = false;
  globalPlayerHidden = false;

  coverOverrideMap = new Map<string, string>();
  private artistImageCache = new Map<string, string | null>();
  /** Emite el trackPath cada vez que se guarda una portada nueva (iTunes o manual) */
  readonly coverReady$ = new Subject<string>();
  /** Términos ya buscados esta sesión (evita duplicados en memoria) */
  private itunesFetchedTerms = new Set<string>();
  /** Paths de canciones que iTunes confirmó que no tiene (persistido en localStorage) */
  private itunesNotFoundPaths = new Set<string>();
  private folderCoverBust = new Map<string, number>();
  private static readonly COVER_CACHE_KEY = 'ev_covers_v2';
  private static readonly COVER_NOT_FOUND_KEY = 'ev_covers_nf_v1';
  private static readonly ARTIST_IMAGE_CACHE_KEY = 'ev_artist_images_v1';

  crossfadeDuration = 0;
  backBehavior: 'rewind-then-prev' | 'always-prev' = 'rewind-then-prev';
  private crossfadeTriggeredForPath: string | null = null;

  private preloadAudio: HTMLAudioElement | null = null;
  private preloadedPath: string | null = null;
  private preloadTriggeredForPath: string | null = null;
  private preloadTimer?: ReturnType<typeof setTimeout>;
  private nativeAudioListenerReady = false;
  private nativeAudioLastSync = 0;
  private nativeAudioLastKey = '';
  private browseCache = new Map<string, { result: PagedMusicResult; timestamp: number }>();
  private readonly browseCacheTtlMs = 15_000;

  constructor(private http: HttpClient, private auth: AuthService, private apiBase: ApiBaseService) {
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
    try {
      const artistImages = JSON.parse(localStorage.getItem(MusicService.ARTIST_IMAGE_CACHE_KEY) || '{}');
      Object.entries(artistImages).forEach(([artist, url]) => {
        // Only restore successful lookups — null entries are retried on next load
        if (typeof url === 'string' && url) this.artistImageCache.set(artist, url);
      });
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
    this.setupNativeAudioSession();
    this.setupNowPlayingNotifications();
    this.setupPreloading();
  }

  private setupMediaSession(): void {
    if (typeof navigator === 'undefined' || !('mediaSession' in navigator)) return;

    navigator.mediaSession.setActionHandler('play', () => {
      this.mainPlayer.resumeAudioContext().then(() => this.mainPlayer.play());
    });
    navigator.mediaSession.setActionHandler('pause',         () => this.mainPlayer.pause());
    navigator.mediaSession.setActionHandler('nexttrack', () => {
      this.mainPlayer.resumeAudioContext().then(() => this.playNextMain());
    });
    navigator.mediaSession.setActionHandler('previoustrack', () => {
      this.mainPlayer.resumeAudioContext().then(() => this.playPrevMain());
    });
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

  private setupNativeAudioSession(): void {
    const plugin = this.getNativeAudioPlugin();
    if (!plugin || this.nativeAudioListenerReady) return;

    this.nativeAudioListenerReady = true;
    plugin.requestNotificationPermission?.().catch?.(() => {});
    plugin.addListener?.('mediaAction', (event: { action?: string }) => {
      const action = event?.action || '';
      if (action === 'play') {
        this.mainPlayer.resumeAudioContext().then(() => this.mainPlayer.play());
      } else if (action === 'pause') {
        this.mainPlayer.pause();
      } else if (action === 'next') {
        this.mainPlayer.resumeAudioContext().then(() => this.playNextMain());
      } else if (action === 'previous') {
        this.mainPlayer.resumeAudioContext().then(() => this.playPrevMain());
      } else if (action.startsWith('seek:')) {
        const ms = Number(action.slice(5));
        if (Number.isFinite(ms)) this.mainPlayer.seek(ms / 1000);
      }
    });

    this.mainPlayer.state$.subscribe(state => this.syncNativeAudioSession(state));
  }

  private syncNativeAudioSession(state: PlayerState): void {
    const plugin = this.getNativeAudioPlugin();
    if (!plugin) return;

    if (!state.currentTrack) {
      plugin.stop?.().catch?.(() => {});
      return;
    }

    const track = state.currentTrack;
    const now = Date.now();
    const syncKey = [
      state.playing ? '1' : '0',
      track.path,
      track.title || track.name,
      track.artist || '',
      track.album || '',
      this.getAbsoluteCoverUrl(state.pathId ?? 0, track),
      Math.floor(state.currentTime),
      Math.floor(state.duration || 0)
    ].join('|');

    if (syncKey === this.nativeAudioLastKey) return;
    if (now - this.nativeAudioLastSync < 1000 && syncKey.split('|').slice(0, 5).join('|') === this.nativeAudioLastKey.split('|').slice(0, 5).join('|')) {
      return;
    }

    this.nativeAudioLastKey = syncKey;
    this.nativeAudioLastSync = now;
    plugin.update?.({
      title: track.title || track.name || 'EverLoad',
      artist: track.artist || '',
      album: track.album || '',
      artworkUrl: this.getAbsoluteCoverUrl(state.pathId ?? 0, track),
      playing: state.playing,
      duration: Number.isFinite(state.duration) ? state.duration : 0,
      position: Number.isFinite(state.currentTime) ? state.currentTime : 0,
    }).catch?.(() => {});
  }

  private getNativeAudioPlugin(): any {
    const capacitor = typeof window !== 'undefined' ? (window as any).Capacitor : null;
    return capacitor?.Plugins?.EverLoadAudio || null;
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

    if (track.source === 'ytmusic') {
      return track.thumbnailUrl || override || '';
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
    const key = `${pathId}|${subPath || ''}|${page}|${size}`;
    const cached = this.browseCache.get(key);
    if (cached && Date.now() - cached.timestamp <= this.browseCacheTtlMs) {
      return of(cached.result);
    }
    return this.http.get<PagedMusicResult>(url).pipe(
      tap(result => {
        if (this.browseCache.size > 250) this.browseCache.clear();
        this.browseCache.set(key, { result, timestamp: Date.now() });
      })
    );
  }

  invalidateBrowseCache(pathId?: number, subPath?: string): void {
    if (pathId == null) {
      this.browseCache.clear();
      return;
    }

    const prefix = `${pathId}|${subPath || ''}`;
    Array.from(this.browseCache.keys())
      .filter(key => key.startsWith(prefix))
      .forEach(key => this.browseCache.delete(key));
  }

  static readonly QUALITY_OPTIONS = [
    { value: 'low',      label: '96kbps  — Ahorro datos',   kbps: 96  },
    { value: 'normal',   label: '128kbps — Normal',         kbps: 128 },
    { value: 'high',     label: '192kbps — Alta calidad',   kbps: 192 },
    { value: 'original', label: 'Original (sin cambios)',   kbps: 0   },
  ];

  getStreamQuality(): string {
    return localStorage.getItem('streamQuality') || 'original';
  }

  setStreamQuality(q: string) {
    localStorage.setItem('streamQuality', q);
  }

  getStreamUrl(pathId: number, trackPath: string): string {
    const token = this.auth.getToken();
    const quality = this.getStreamQuality();
    return `${this.api}/stream?pathId=${pathId}&subPath=${encodeURIComponent(trackPath)}&token=${token}&quality=${quality}`;
  }

  shouldUseHls(track: MusicMetadataDto): boolean {
    if (!track || track.source === 'youtube' || track.source === 'ytmusic' || track.source === 'local') return false;
    return (track.duration || 0) >= 1200 || (track.size || 0) >= 80 * 1024 * 1024;
  }

  prepareHls(pathId: number, trackPath: string): Observable<HlsPrepareResult> {
    const params = new URLSearchParams({ pathId: String(pathId), subPath: trackPath });
    return this.http.post<HlsPrepareResult>(`${this.api}/hls/prepare?${params}`, {});
  }

  getHlsStatus(pathId: number, trackPath: string): Observable<HlsPrepareResult> {
    const params = new URLSearchParams({ pathId: String(pathId), subPath: trackPath });
    return this.http.get<HlsPrepareResult>(`${this.api}/hls/status?${params}`);
  }

  getHlsPlaylistUrl(pathId: number, trackPath: string): string {
    const token = this.auth.getToken();
    const params = new URLSearchParams({ pathId: String(pathId), subPath: trackPath, token: token || '' });
    return `${this.api}/hls/playlist?${params}`;
  }

  getCoverUrl(pathId: number, trackPath: string, source?: string): string {
    const token = this.auth.getToken();
    if (source === 'youtube') {
      return `https://img.youtube.com/vi/${trackPath}/hqdefault.jpg`;
    }
    if (source === 'ytmusic') {
      return this.coverOverrideMap.get(trackPath) || '';
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

    const tryMusicBrainz = (): Promise<void> => {
      if (!album) { saveNotFound(); return Promise.resolve(); }
      const params = new URLSearchParams({ album });
      if (artist) params.set('artist', artist);
      return fetch(`${this.api}/album-cover?${params}`)
        .then(r => r.json())
        .then((d: any) => {
          if (d?.found && d?.imageUrl) {
            save(d.imageUrl.startsWith('http') ? d.imageUrl : `${this.BASE}${d.imageUrl}`);
          } else {
            saveNotFound();
          }
        })
        .catch(() => saveNotFound());
    };

    // Cascada: artista+título → artista+álbum → solo título → solo artista → MusicBrainz
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
            else tryMusicBrainz(); // cascada iTunes agotada → intentar MusicBrainz
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

  // ── YouTube metadata lookup ───────────────────────────────────────────────

  fetchYoutubeMetadata(query: string): Observable<{ found: boolean; title?: string; artist?: string; album?: string; videoId?: string; channelName?: string; rawTitle?: string }> {
    return this.http.get<any>(`${this.api}/youtube-metadata?query=${encodeURIComponent(query)}`);
  }

  fillYoutubeMetadataBulk(pathId: number, subPath = '', limit = 50, onlyMissing = true): Observable<any> {
    const params = new URLSearchParams({
      pathId: String(pathId),
      subPath,
      limit: String(limit),
      onlyMissing: String(onlyMissing)
    });
    return this.http.post<any>(`${this.api}/youtube-metadata/bulk?${params}`, {});
  }

  // ── NAS yt-dlp async jobs ─────────────────────────────────────────────────

  search(pathId: number, subPath: string | undefined, query: string, limit = 200): Observable<MusicMetadataDto[]> {
    let url = `${this.api}/search?pathId=${pathId}&query=${encodeURIComponent(query)}&limit=${limit}`;
    if (subPath) url += `&subPath=${encodeURIComponent(subPath)}`;
    return this.http.get<MusicMetadataDto[]>(url);
  }

  getLibraryOverview(pathId: number, limit = 5000): Observable<LibraryOverviewDto> {
    return this.http.get<LibraryOverviewDto>(`${this.api}/library-overview?pathId=${pathId}&limit=${limit}`);
  }

  startLibraryIndex(pathId: number): Observable<any> {
    return this.http.post<any>(`${this.api}/library-index?pathId=${pathId}`, {});
  }

  getArtistTracks(pathId: number, artist: string, aliases: string[] = [], limit = 500): Observable<MusicMetadataDto[]> {
    const params = new URLSearchParams({ pathId: String(pathId), artist, limit: String(limit) });
    aliases.filter(Boolean).forEach(alias => params.append('aliases', alias));
    return this.http.get<MusicMetadataDto[]>(`${this.api}/artist-tracks?${params}`);
  }

  /**
   * Resolves auto images for a list of artist objects sequentially (3 concurrent).
   * Skips artists that already have imageUrl or are in cache.
   * Updates autoImageUrl on each object as results arrive.
   */
  resolveArtistImages<T extends { artist: string; imageUrl?: string; autoImageUrl?: string }>(
    artists: T[]
  ): void {
    const pending = artists.filter(a => !a.imageUrl && !this.artistImageCache.has(this.artistImageKey(a.artist)));
    if (!pending.length) {
      // Still apply cached values for artists with null cached (already tried, not found)
      artists.forEach(a => {
        if (!a.imageUrl) {
          const key = this.artistImageKey(a.artist);
          const cached = this.artistImageCache.get(key);
          if (cached) a.autoImageUrl = cached;
        }
      });
      return;
    }

    // Apply already-cached values immediately
    artists.forEach(a => {
      if (!a.imageUrl) {
        const key = this.artistImageKey(a.artist);
        if (this.artistImageCache.has(key)) {
          const cached = this.artistImageCache.get(key);
          if (cached) a.autoImageUrl = cached;
        }
      }
    });

    // Fetch uncached artists 3 at a time to leave HTTP connections free for data requests
    from(pending).pipe(
      mergeMap(a => this.getArtistImage(a.artist).pipe(
        tap(result => { if (result.found && result.imageUrl) a.autoImageUrl = result.imageUrl; })
      ), 3)
    ).subscribe({ error: () => {} });
  }

  getArtistImage(artist: string): Observable<ArtistImageLookupDto> {
    const key = this.artistImageKey(artist);
    if (!key) return of({ found: false });
    if (this.artistImageCache.has(key)) {
      const imageUrl = this.artistImageCache.get(key);
      return of(imageUrl ? { found: true, imageUrl } : { found: false });
    }
    return this.http.get<ArtistImageLookupDto>(`${this.api}/artist-image?artist=${encodeURIComponent(artist)}`).pipe(
      tap(result => {
        if (result.found && result.imageUrl) {
          this.artistImageCache.set(key, result.imageUrl);
          this.persistArtistImageCache();
        }
        // Don't cache failures — they'll be retried automatically
      })
    );
  }

  private artistImageKey(value: string): string {
    return (value || '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private persistArtistImageCache(): void {
    try {
      const obj: Record<string, string> = {};
      this.artistImageCache.forEach((url, artist) => {
        if (url) obj[artist] = url;
      });
      localStorage.setItem(MusicService.ARTIST_IMAGE_CACHE_KEY, JSON.stringify(obj));
    } catch {}
  }

  /** Removes not-found entries from the in-session artist image cache so they can be retried. */
  clearArtistImageCacheFailed(): void {
    [...this.artistImageCache.entries()]
      .filter(([, v]) => !v)
      .forEach(([k]) => this.artistImageCache.delete(k));
  }

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

  ytDlpQueueUrl(url: string, title: string, nasPathId: number, subPath: string): Observable<{jobId: string}> {
    const p = new URLSearchParams({ url, title, nasPathId: String(nasPathId), subPath });
    return this.http.post<{jobId: string}>(`${this.BASE}/api/nas/ytdlp/queue-url?${p}`, {});
  }

  ytDlpJobStatus(jobId: string): Observable<any> {
    return this.http.get<any>(`${this.BASE}/api/nas/ytdlp/status/${jobId}`);
  }

  // YouTube Music anonymous catalogue
  private get ytMusicApi(): string { return `${this.BASE}/api/ytmusic`; }

  searchYtMusic(query: string): Observable<{ items: YtMusicTrackDto[] }> {
    return this.http.get<{ items: YtMusicTrackDto[] }>(
      `${this.ytMusicApi}/search?query=${encodeURIComponent(query)}`
    );
  }

  resolveYtMusicArtist(name: string): Observable<{ channelId: string }> {
    return this.http.get<{ channelId: string }>(
      `${this.ytMusicApi}/artist/resolve?name=${encodeURIComponent(name)}`
    );
  }

  discoverYtMusicHome(): Observable<YtMusicDiscoverHomeDto> {
    return this.http.get<YtMusicDiscoverHomeDto>(`${this.ytMusicApi}/discover/home`);
  }

  discoverYtMusicContinuation(token: string): Observable<YtMusicDiscoverHomeDto> {
    return this.http.get<YtMusicDiscoverHomeDto>(
      `${this.ytMusicApi}/discover/continuation?token=${encodeURIComponent(token)}`
    );
  }

  getYtMusicAlbum(browseId: string): Observable<YtMusicAlbumDto> {
    return this.http.get<YtMusicAlbumDto>(`${this.ytMusicApi}/album/${encodeURIComponent(browseId)}`);
  }

  getYtMusicArtist(channelId: string): Observable<YtMusicArtistDto> {
    return this.http.get<YtMusicArtistDto>(`${this.ytMusicApi}/artist/${encodeURIComponent(channelId)}`);
  }

  getYtMusicPlaylist(playlistId: string): Observable<{ playlistId: string; title: string; thumbnailUrl?: string; tracks: YtMusicTrackDto[] }> {
    return this.http.get<{ playlistId: string; title: string; thumbnailUrl?: string; tracks: YtMusicTrackDto[] }>(
      `${this.ytMusicApi}/playlist/${encodeURIComponent(playlistId)}`
    );
  }

  startYtMusicMix(videoId: string): Observable<{ items: YtMusicTrackDto[] }> {
    return this.http.get<{ items: YtMusicTrackDto[] }>(`${this.ytMusicApi}/mix/${encodeURIComponent(videoId)}`);
  }

  getYtMusicStream(videoId: string): Observable<YtMusicStreamInfoDto> {
    return this.http.get<YtMusicStreamInfoDto>(`${this.ytMusicApi}/stream/${encodeURIComponent(videoId)}`);
  }

  getYtMusicAudioUrl(videoId: string): string {
    const token = this.auth.getToken();
    return `${this.ytMusicApi}/stream/${encodeURIComponent(videoId)}/audio?token=${encodeURIComponent(token || '')}`;
  }

  /** Warms the backend's resolved-stream cache so playback of `videoId` can start instantly when it comes up next. */
  private prefetchYtMusicStream(videoId: string): void {
    if (!videoId) return;
    this.getYtMusicStream(videoId).subscribe({ error: () => {} });
  }

  toYtMusicTrack(track: YtMusicTrackDto): MusicMetadataDto {
    const videoId = track.videoId || '';
    const title = track.title || videoId;
    const artist = track.artist || (track.artists || []).join(', ');
    if (videoId && track.thumbnailUrl) {
      this.coverOverrideMap.set(videoId, track.thumbnailUrl);
    }
    return {
      name: title,
      path: videoId,
      directory: false,
      size: 0,
      lastModified: '',
      title,
      artist,
      album: track.album || '',
      duration: track.durationSeconds || 0,
      format: 'm4a',
      hasCover: !!track.thumbnailUrl,
      bpm: 0,
      source: 'ytmusic',
      thumbnailUrl: track.thumbnailUrl,
    };
  }

  toYtMusicQueue(tracks: YtMusicTrackDto[]): MusicMetadataDto[] {
    return (tracks || []).filter(t => !!t.videoId).map(t => this.toYtMusicTrack(t));
  }

  getYtMusicHistory(limit = 40): MusicMetadataDto[] {
    try {
      const parsed = JSON.parse(localStorage.getItem('ev_ytmusic_history') || '[]');
      const tracks: MusicMetadataDto[] = Array.isArray(parsed) ? parsed.slice(0, limit) : [];
      // El override map vive en memoria — repoblarlo aquí evita perder las
      // miniaturas del historial tras recargar la página.
      tracks.forEach(t => {
        if (t.path && t.thumbnailUrl) this.coverOverrideMap.set(t.path, t.thumbnailUrl);
      });
      return tracks;
    } catch {
      return [];
    }
  }

  clearYtMusicHistory(): void {
    localStorage.removeItem('ev_ytmusic_history');
  }

  private addYtMusicHistory(track: MusicMetadataDto): void {
    const current = this.getYtMusicHistory(80).filter(t => t.path !== track.path);
    localStorage.setItem('ev_ytmusic_history', JSON.stringify([track, ...current].slice(0, 80)));
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

  getListeningStats(topLimit = 10): Observable<{ totalPlays: number; topTracks: any[] }> {
    return this.http.get<{ totalPlays: number; topTracks: any[] }>(
      `${this.api.replace('/music', '/library')}/stats?topLimit=${topLimit}`
    );
  }

  private topArtistsCache$: Observable<{ artist: string; playCount: number }[]> | null = null;
  private topArtistsCacheTs = 0;

  getTopArtists(limit = 20): Observable<{ artist: string; playCount: number }[]> {
    const now = Date.now();
    if (!this.topArtistsCache$ || now - this.topArtistsCacheTs > 300_000) {
      this.topArtistsCacheTs = now;
      this.topArtistsCache$ = this.http.get<{ artist: string; playCount: number }[]>(
        `${this.api.replace('/music', '/library')}/top-artists?limit=${limit}`
      ).pipe(shareReplay(1));
    }
    return this.topArtistsCache$;
  }

  invalidateTopArtistsCache() { this.topArtistsCache$ = null; }

  // ── Playlists ─────────────────────────────────────────────────────────────

  private get playlistApi(): string { return `${this.BASE}/api/playlists`; }

  getPlaylists(): Observable<any[]> {
    return this.http.get<any[]>(this.playlistApi);
  }

  createPlaylist(name: string): Observable<any> {
    return this.http.post<any>(this.playlistApi, { name });
  }

  renamePlaylist(id: number, name: string): Observable<any> {
    return this.http.put<any>(`${this.playlistApi}/${id}`, { name });
  }

  deletePlaylist(id: number): Observable<any> {
    return this.http.delete<any>(`${this.playlistApi}/${id}`);
  }

  addTrackToPlaylist(playlistId: number, track: MusicMetadataDto, nasPathId: number): Observable<any> {
    return this.http.post<any>(`${this.playlistApi}/${playlistId}/tracks`, {
      trackPath: track.path,
      title: track.title || track.name,
      artist: track.artist || '',
      album: track.album || '',
      nasPathId,
      durationSeconds: track.duration || 0,
    });
  }

  removeTrackFromPlaylist(playlistId: number, trackId: number): Observable<any> {
    return this.http.delete<any>(`${this.playlistApi}/${playlistId}/tracks/${trackId}`);
  }

  getPublicPlaylists(): Observable<any[]> {
    return this.http.get<any[]>(`${this.playlistApi}/public`);
  }

  setPlaylistVisibility(id: number, isPublic: boolean): Observable<any> {
    return this.http.patch<any>(`${this.playlistApi}/${id}/visibility`, { isPublic });
  }

  // ── Manual artist profiles ────────────────────────────────────────────────

  private get artistApi(): string { return `${this.BASE}/api/artists`; }

  private artistProfilesCache$: Observable<ArtistProfileDto[]> | null = null;
  private artistProfilesCacheTs = 0;

  getArtistProfiles(): Observable<ArtistProfileDto[]> {
    const now = Date.now();
    if (!this.artistProfilesCache$ || now - this.artistProfilesCacheTs > 300_000) {
      this.artistProfilesCacheTs = now;
      this.artistProfilesCache$ = this.http.get<ArtistProfileDto[]>(this.artistApi).pipe(shareReplay(1));
    }
    return this.artistProfilesCache$;
  }

  invalidateArtistProfilesCache() { this.artistProfilesCache$ = null; }

  createArtistProfile(name: string, aliases = '', description = ''): Observable<ArtistProfileDto> {
    return this.http.post<ArtistProfileDto>(this.artistApi, { name, aliases, description });
  }

  updateArtistProfile(id: number, name: string, aliases = '', description = ''): Observable<ArtistProfileDto> {
    return this.http.put<ArtistProfileDto>(`${this.artistApi}/${id}`, { name, aliases, description });
  }

  deleteArtistProfile(id: number): Observable<any> {
    return this.http.delete<any>(`${this.artistApi}/${id}`);
  }

  uploadArtistImage(id: number, image: File): Observable<ArtistProfileDto> {
    const form = new FormData();
    form.append('image', image);
    return this.http.post<ArtistProfileDto>(`${this.artistApi}/${id}/image`, form);
  }

  uploadArtistImageFromUrl(id: number, imageUrl: string): Observable<ArtistProfileDto> {
    return this.http.post<ArtistProfileDto>(`${this.artistApi}/${id}/image-url`, { imageUrl });
  }

  removeArtistImage(id: number): Observable<ArtistProfileDto> {
    return this.http.delete<ArtistProfileDto>(`${this.artistApi}/${id}/image`);
  }

  getLyrics(pathId: number, subPath: string, title?: string, artist?: string, duration?: number): Observable<{ source: string; lrc?: string; plain?: string }> {
    let url = `${this.api}/lyrics?pathId=${pathId}&subPath=${encodeURIComponent(subPath)}`;
    if (title)    url += `&title=${encodeURIComponent(title)}`;
    if (artist)   url += `&artist=${encodeURIComponent(artist)}`;
    if (duration) url += `&duration=${Math.round(duration)}`;
    return this.http.get<{ source: string; lrc?: string; plain?: string }>(url);
  }

  recordHistory(track: MusicMetadataDto | null, pathId: number | null) {
    if (!track) return;
    if (track.source === 'ytmusic') {
      this.addYtMusicHistory(track);
      return;
    }
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


  // ── Preloading ────────────────────────────────────────────────────────────

  private setupPreloading(): void {
    this.mainPlayer.state$.subscribe(state => {
      if (!state.currentTrack || !state.duration || state.duration < 1) return;
      if (state.currentTrack.source === 'youtube' || state.currentTrack.source === 'ytmusic') return;

      // Reset triggers when track changes
      if (state.currentTrack.path !== this.preloadTriggeredForPath &&
          state.currentTrack.path !== this.preloadedPath) {
        this.preloadTriggeredForPath = null;
      }
      if (state.currentTrack.path !== this.crossfadeTriggeredForPath) {
        // New track started — apply fade-in if crossfade is on
        if (this.crossfadeDuration > 0 && state.currentTrack.path !== this.crossfadeTriggeredForPath) {
          this.crossfadeTriggeredForPath = state.currentTrack.path;
          this.mainPlayer.scheduleFadeIn(Math.min(this.crossfadeDuration, 5));
        }
      }

      const timeLeft = state.duration - state.currentTime;
      if (timeLeft <= 0) return;

      // Auto-crossfade: schedule fade-out when we enter the crossfade window
      if (this.crossfadeDuration > 0 && state.playing &&
          timeLeft <= this.crossfadeDuration + 0.1 &&
          timeLeft > this.crossfadeDuration - 0.5) {
        this.mainPlayer.scheduleFadeOut(timeLeft);
      }

      // Preload next track within 30 s
      if (timeLeft > 30) return;
      if (this.preloadTriggeredForPath === state.currentTrack.path) return;

      this.preloadTriggeredForPath = state.currentTrack.path;
      const next = this.peekNextTrack();
      if (next && next.track.path !== state.currentTrack.path) {
        if (next.track.source === 'ytmusic') {
          // No descargamos el audio entero por adelantado (sería tráfico
          // desperdiciado si el usuario salta de pista) — basta con dejar
          // la resolución del stream ya cacheada en el backend para que el
          // siguiente <audio>.src arranque sin esperar al resolver chain.
          this.prefetchYtMusicStream(next.track.path);
        } else if (next.track.source !== 'youtube') {
          this.doPreload(next.track, next.pathId);
        }
      }
    });
  }

  private peekNextTrack(): { track: MusicMetadataDto; pathId: number } | null {
    const q = this.queueSubj.value;
    if (!q.tracks.length) return null;
    const currentIndex = this.resolveQueueIndex();

    if (this._repeat === 'one') return { track: q.tracks[currentIndex], pathId: q.pathId };

    if (this._shuffle) {
      const pos = this.shuffleOrder.indexOf(currentIndex);
      const nextPos = pos + 1;
      if (nextPos < this.shuffleOrder.length) return { track: q.tracks[this.shuffleOrder[nextPos]], pathId: q.pathId };
      if (this._repeat === 'all' && this.shuffleOrder.length > 0) return { track: q.tracks[this.shuffleOrder[0]], pathId: q.pathId };
    } else {
      if (currentIndex < q.tracks.length - 1) return { track: q.tracks[currentIndex + 1], pathId: q.pathId };
      if (this._repeat === 'all') return { track: q.tracks[0], pathId: q.pathId };
    }
    return null;
  }

  private doPreload(track: MusicMetadataDto, pathId: number): void {
    this.releasePreloadAudio();
    const el = new Audio();
    el.preload = 'auto';
    el.crossOrigin = 'anonymous';
    el.src = this.getStreamUrl(pathId, track.path);
    el.load();
    this.preloadAudio = el;
    this.preloadedPath = track.path;
  }

  private releasePreloadAudio(resetTrigger = false): void {
    if (this.preloadTimer) {
      clearTimeout(this.preloadTimer);
      this.preloadTimer = undefined;
    }
    if (this.preloadAudio) {
      this.preloadAudio.pause();
      this.preloadAudio.removeAttribute('src');
      this.preloadAudio.load();
      this.preloadAudio = null;
    }
    this.preloadedPath = null;
    if (resetTrigger) this.preloadTriggeredForPath = null;
  }

  private scheduleNextPreload(delayMs = 1500): void {
    if (this.preloadTimer) clearTimeout(this.preloadTimer);
    const expectedPath = this.mainPlayer.state.currentTrack?.path;
    this.preloadTimer = setTimeout(() => {
      this.preloadTimer = undefined;
      if (!expectedPath || this.mainPlayer.state.currentTrack?.path !== expectedPath) return;
      const next = this.peekNextTrack();
      if (next && next.track.source !== 'youtube' && next.track.source !== 'ytmusic' && next.track.path !== expectedPath) {
        this.preloadTriggeredForPath = expectedPath;
        this.doPreload(next.track, next.pathId);
      }
    }, delayMs);
  }

  // ── Queue / Library controls ──────────────────────────────────────────────

  setQueue(pathId: number, tracks: MusicMetadataDto[], index: number) {
    this.releasePreloadAudio(true);
    this.queueSubj.next({ tracks, pathId, index });
    if (this._shuffle) this.buildShuffleOrder();
    this.persistState();
    if (tracks[index]) {
      this.mainPlayer.load(tracks[index], pathId).then(() => {
        this.mainPlayer.play();
        this.scheduleNextPreload();
      });
    }
  }

  updateQueue(pathId: number, tracks: MusicMetadataDto[], index: number) {
    const safeIndex = tracks.length ? Math.min(Math.max(index, 0), tracks.length - 1) : -1;
    this.queueSubj.next({ tracks, pathId, index: safeIndex });
    if (this._shuffle) this.buildShuffleOrder();
    this.persistState();
  }

  // Advances to a track within the current queue without rebuilding shuffleOrder,
  // so the full shuffle sequence is preserved across playback.
  private advanceToTrack(pathId: number, tracks: MusicMetadataDto[], index: number) {
    this.releasePreloadAudio(true);
    this.queueSubj.next({ tracks, pathId, index });
    this.persistState();
    if (tracks[index]) {
      this.mainPlayer.load(tracks[index], pathId).then(() => {
        this.mainPlayer.play();
        this.scheduleNextPreload();
      });
    }
  }

  private resolveQueueIndex(): number {
    const q = this.queueSubj.value;
    const currentTrackPath = this.mainPlayer.state.currentTrack?.path;

    if (q.index >= 0 && q.index < q.tracks.length) {
      const indexedTrack = q.tracks[q.index];
      if (!currentTrackPath || indexedTrack?.path === currentTrackPath) return q.index;
    }

    if (currentTrackPath) {
      const actualIndex = q.tracks.findIndex(track => track.path === currentTrackPath);
      if (actualIndex >= 0) return actualIndex;
    }

    return q.index;
  }

  playNextMain() {
    const q = this.queueSubj.value;
    const currentIndex = this.resolveQueueIndex();
    if (this._repeat === 'one') {
      this.mainPlayer.seek(0);
      this.mainPlayer.play();
      return;
    }
    if (this._shuffle) {
      const pos = this.shuffleOrder.indexOf(currentIndex);
      const next = pos + 1;
      if (next < this.shuffleOrder.length) {
        this.advanceToTrack(q.pathId, q.tracks, this.shuffleOrder[next]);
      } else if (this._repeat === 'all') {
        this.buildShuffleOrder();
        this.advanceToTrack(q.pathId, q.tracks, this.shuffleOrder[0]);
      }
    } else {
      if (currentIndex < q.tracks.length - 1) {
        this.setQueue(q.pathId, q.tracks, currentIndex + 1);
      } else if (this._repeat === 'all') {
        this.setQueue(q.pathId, q.tracks, 0);
      }
    }
  }

  playPrevMain() {
    const q = this.queueSubj.value;
    const currentIndex = this.resolveQueueIndex();
    const currentTime = this.mainPlayer.state.currentTime ?? 0;
    const shouldRewind = this.backBehavior === 'rewind-then-prev' && currentTime > 3;
    if (shouldRewind) {
      this.mainPlayer.seek(0);
      return;
    }
    if (this._shuffle) {
      const pos = this.shuffleOrder.indexOf(currentIndex);
      if (pos > 0) this.advanceToTrack(q.pathId, q.tracks, this.shuffleOrder[pos - 1]);
      else this.mainPlayer.seek(0);
    } else if (currentIndex > 0) {
      this.setQueue(q.pathId, q.tracks, currentIndex - 1);
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
