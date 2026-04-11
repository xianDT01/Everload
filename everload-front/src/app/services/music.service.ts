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
}

export interface PlayerState {
  playing: boolean;
  currentTime: number;
  duration: number;
  volume: number;
  currentTrack: MusicMetadataDto | null;
  pathId: number | null;
}

// ── AudioPlayer ───────────────────────────────────────────────────────────────

export class AudioPlayer {
  private audio: HTMLAudioElement;
  private stateSubj = new BehaviorSubject<PlayerState>({
    playing: false, currentTime: 0, duration: 0,
    volume: 1, currentTrack: null, pathId: null
  });

  public state$ = this.stateSubj.asObservable();
  /** Called when the track finishes naturally (not on explicit stop). */
  public onTrackEnded?: () => void;

  constructor(private musicService: MusicService) {
    this.audio = new Audio();
    this.audio.addEventListener('timeupdate',    () => this.patch({ currentTime: this.audio.currentTime }));
    this.audio.addEventListener('play',          () => this.patch({ playing: true }));
    this.audio.addEventListener('pause',         () => this.patch({ playing: false }));
    this.audio.addEventListener('loadedmetadata',() => this.patch({ duration: this.audio.duration }));
    this.audio.addEventListener('volumechange',  () => this.patch({ volume: this.audio.volume }));
    this.audio.addEventListener('ended',         () => {
      this.patch({ playing: false, currentTime: 0 });
      if (this.onTrackEnded) this.onTrackEnded();
    });
  }

  private patch(partial: Partial<PlayerState>) {
    this.stateSubj.next({ ...this.stateSubj.value, ...partial });
  }

  get state(): PlayerState { return this.stateSubj.value; }

  load(track: MusicMetadataDto, pathId: number) {
    this.audio.src = this.musicService.getStreamUrl(pathId, track.path);
    this.audio.load();
    this.patch({ currentTrack: track, pathId, currentTime: 0, playing: false, duration: 0 });
  }

  play()  { if (this.audio.src) this.audio.play(); }
  pause() { this.audio.pause(); }
  togglePlay() { this.state.playing ? this.pause() : this.play(); }

  seek(time: number) { this.audio.currentTime = time; }

  setVolume(vol: number) {
    this.audio.volume = Math.max(0, Math.min(1, vol));
    this.patch({ volume: this.audio.volume });
  }

  cue() {
    this.audio.currentTime = 0;
    if (this.state.playing) this.pause();
  }
}

// ── MusicService ──────────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class MusicService {

  private readonly BASE: string = (() => {
    const host = typeof window !== 'undefined' ? window.location.hostname : '';
    return (host === 'localhost' || host === '127.0.0.1') ? 'http://localhost:8080' : '';
  })();

  private readonly api = `${this.BASE}/api/music`;

  // Library main player (one track at a time)
  public mainPlayer: AudioPlayer;
  // Deck players (two simultaneous)
  public deckAPlayer: AudioPlayer;
  public deckBPlayer: AudioPlayer;

  // Library queue
  private queueSubj = new BehaviorSubject<{ tracks: MusicMetadataDto[]; pathId: number; index: number }>({
    tracks: [], pathId: 0, index: -1
  });
  public queue$ = this.queueSubj.asObservable();

  constructor(private http: HttpClient, private auth: AuthService) {
    this.mainPlayer  = new AudioPlayer(this);
    this.deckAPlayer = new AudioPlayer(this);
    this.deckBPlayer = new AudioPlayer(this);

    // Auto-advance queue when main player track ends naturally
    this.mainPlayer.onTrackEnded = () => this.playNextMain();
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

  getCoverUrl(pathId: number, trackPath: string): string {
    const token = this.auth.getToken();
    return `${this.api}/cover?pathId=${pathId}&subPath=${encodeURIComponent(trackPath)}&token=${token}`;
  }

  // ── Queue / Library controls ──────────────────────────────────────────────

  setQueue(pathId: number, tracks: MusicMetadataDto[], index: number) {
    this.queueSubj.next({ tracks, pathId, index });
    if (tracks[index]) {
      this.mainPlayer.load(tracks[index], pathId);
      this.mainPlayer.play();
    }
  }

  playNextMain() {
    const q = this.queueSubj.value;
    if (q.index < q.tracks.length - 1) {
      this.setQueue(q.pathId, q.tracks, q.index + 1);
    }
  }

  playPrevMain() {
    const q = this.queueSubj.value;
    // If more than 3s in: restart current track; otherwise go to previous
    if (this.mainPlayer.state.currentTime > 3) {
      this.mainPlayer.seek(0);
    } else if (q.index > 0) {
      this.setQueue(q.pathId, q.tracks, q.index - 1);
    }
  }

  // ── Crossfader ────────────────────────────────────────────────────────────

  /**
   * Equal-power crossfade.
   * value: -1 (full Deck A) … 0 (equal) … +1 (full Deck B)
   */
  crossfade(value: number) {
    const t = (value + 1) / 2; // normalise to [0, 1]
    const volA = Math.cos(t * Math.PI / 2);
    const volB = Math.cos((1 - t) * Math.PI / 2);
    this.deckAPlayer.setVolume(volA);
    this.deckBPlayer.setVolume(volB);
  }
}