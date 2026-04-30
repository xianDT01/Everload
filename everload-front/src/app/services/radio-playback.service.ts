import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';

export interface RadioStation {
  stationuuid: string;
  name: string;
  url: string;
  url_resolved: string;
  homepage: string;
  favicon: string;
  tags: string;
  country: string;
  countrycode: string;
  state: string;
  language: string;
  codec: string;
  bitrate: number;
  votes: number;
  clickcount: number;
  lastcheckok: number;
  fallbackUrls?: string[];
}

export interface RadioPlaybackState {
  selectedStation: RadioStation | null;
  playing: boolean;
  buffering: boolean;
  error: string;
  volume: number;
}

@Injectable({ providedIn: 'root' })
export class RadioPlaybackService {
  private readonly initialState: RadioPlaybackState = {
    selectedStation: null,
    playing: false,
    buffering: false,
    error: '',
    volume: 0.78,
  };

  private audio: HTMLAudioElement | null = null;
  private streamCandidates: string[] = [];
  private streamCandidateIndex = 0;
  private stateSubject = new BehaviorSubject<RadioPlaybackState>(this.initialState);

  readonly state$ = this.stateSubject.asObservable();

  constructor(private translate: TranslateService) {}

  get snapshot(): RadioPlaybackState {
    return this.stateSubject.value;
  }

  playStation(station: RadioStation): void {
    this.streamCandidates = [
      station.url_resolved || station.url,
      ...(station.fallbackUrls || []),
    ].filter(Boolean);
    this.streamCandidateIndex = 0;
    this.patchState({ selectedStation: station });
    this.playStream(this.streamCandidates[0]);
  }

  playDirect(url: string): void {
    const station: RadioStation = {
      stationuuid: 'direct',
      name: 'Radio personalizada',
      url,
      url_resolved: url,
      homepage: '',
      favicon: '',
      tags: 'direct stream',
      country: 'Personal',
      countrycode: '',
      state: '',
      language: '',
      codec: '',
      bitrate: 0,
      votes: 0,
      clickcount: 0,
      lastcheckok: 1,
    };

    this.streamCandidates = [url];
    this.streamCandidateIndex = 0;
    this.patchState({ selectedStation: station });
    this.playStream(url);
  }

  pause(): void {
    this.audio?.pause();
    this.patchState({ playing: false, buffering: false });
  }

  stop(): void {
    if (!this.audio) return;
    this.audio.pause();
    this.audio.src = '';
    this.audio.load();
    this.patchState({ playing: false, buffering: false });
  }

  setVolume(volume: number): void {
    if (this.audio) this.audio.volume = volume;
    this.patchState({ volume });
  }

  private playStream(url: string): void {
    this.patchState({ error: '', buffering: true, playing: false });

    const audio = this.ensureAudio();
    audio.pause();
    audio.src = url;
    audio.volume = this.snapshot.volume;
    audio.load();
    audio.play().catch(() => {
      this.tryNextStreamCandidate();
    });
  }

  private ensureAudio(): HTMLAudioElement {
    if (this.audio) return this.audio;

    this.audio = new Audio();
    this.audio.preload = 'none';
    this.audio.addEventListener('playing', () => {
      this.patchState({ playing: true, buffering: false, error: '' });
    });
    this.audio.addEventListener('waiting', () => {
      if (this.snapshot.selectedStation) this.patchState({ buffering: true });
    });
    this.audio.addEventListener('pause', () => {
      this.patchState({ playing: false, buffering: false });
    });
    this.audio.addEventListener('error', () => {
      this.tryNextStreamCandidate();
    });

    return this.audio;
  }

  private tryNextStreamCandidate(): void {
    if (this.streamCandidateIndex < this.streamCandidates.length - 1) {
      this.streamCandidateIndex += 1;
      this.playStream(this.streamCandidates[this.streamCandidateIndex]);
      return;
    }

    this.patchState({
      playing: false,
      buffering: false,
      error: this.translate.instant('RADIO.ERROR_STREAM_FAILED'),
    });
  }

  private patchState(patch: Partial<RadioPlaybackState>): void {
    this.stateSubject.next({ ...this.stateSubject.value, ...patch });
  }
}
