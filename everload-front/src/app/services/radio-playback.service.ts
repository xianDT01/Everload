import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import type Hls from 'hls.js';

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
  hls?: number;
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
  private hls: Hls | null = null;
  private streamTimer?: ReturnType<typeof setTimeout>;
  private readonly streamTimeoutMs = 12000;
  private stateSubject = new BehaviorSubject<RadioPlaybackState>(this.initialState);

  readonly state$ = this.stateSubject.asObservable();

  constructor(private translate: TranslateService) {}

  get snapshot(): RadioPlaybackState {
    return this.stateSubject.value;
  }

  playStation(station: RadioStation): void {
    const urls = /\.(m3u|pls)(?:[?#]|$)/i.test(station.url)
      ? [station.url_resolved, station.url] : [station.url, station.url_resolved];
    this.streamCandidates = this.usableUrls([
      ...urls,
      ...(station.fallbackUrls || []),
    ]);
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

    this.playStation(station);
  }

  pause(): void {
    this.releaseAudio();
    this.patchState({ playing: false, buffering: false });
  }

  stop(): void {
    this.releaseAudio();
    this.patchState({ playing: false, buffering: false, error: '' });
  }

  setVolume(volume: number): void {
    if (!Number.isFinite(volume)) return;
    volume = Math.max(0, Math.min(1, volume));
    if (this.audio) this.audio.volume = volume;
    this.patchState({ volume });
  }

  private playStream(url: string): void {
    this.releaseAudio();
    this.patchState({ error: '', buffering: true, playing: false });
    if (!url) {
      this.tryNextStreamCandidate();
      return;
    }
    // Each attempt owns its audio element so late events cannot skip a new stream.
    const audio = this.audio = new Audio();
    audio.preload = 'none';
    audio.volume = this.snapshot.volume;
    const isCurrent = () => this.audio === audio;
    const fail = () => { if (isCurrent()) this.tryNextStreamCandidate(); };
    const armTimeout = () => {
      this.clearStreamTimer();
      this.streamTimer = setTimeout(fail, this.streamTimeoutMs);
    };
    audio.addEventListener('playing', () => {
      if (!isCurrent()) return;
      this.clearStreamTimer();
      this.patchState({ playing: true, buffering: false, error: '' });
    });
    audio.addEventListener('waiting', () => {
      if (!isCurrent()) return;
      this.patchState({ buffering: true });
      if (!this.streamTimer) armTimeout();
    });
    audio.addEventListener('error', fail);
    audio.addEventListener('ended', fail);
    armTimeout();
    void this.startAudio(audio, url).catch(error => {
      if (!isCurrent()) return;
      if (error?.name === 'NotAllowedError') {
        this.releaseAudio();
        this.patchState({ playing: false, buffering: false,
          error: this.translate.instant('RADIO.ERROR_PLAY_PERMISSION') });
      } else fail();
    });
  }

  private async startAudio(audio: HTMLAudioElement, url: string): Promise<void> {
    const isHls = /\.m3u8(?:[?#]|$)/i.test(url) ||
      (this.snapshot.selectedStation?.hls === 1 && !/\.(mp3|aac|ogg)(?:[?#]|$)/i.test(url));
    if (isHls) {
      const HlsCtor = await this.loadHlsConstructor();
      if (this.audio !== audio) return;
      // Some Chromium versions advertise native HLS but cannot decode live AAC playlists.
      if (!HlsCtor.isSupported()) {
        if (!audio.canPlayType('application/vnd.apple.mpegurl')) throw new Error('HLS unsupported');
        audio.src = url;
        audio.load();
        await audio.play();
        return;
      }
      const hls = this.hls = new HlsCtor({ maxBufferLength: 15, maxMaxBufferLength: 30 });
      hls.on(HlsCtor.Events.ERROR, (_, data) => {
        if (data.fatal && this.audio === audio) this.tryNextStreamCandidate();
      });
      await new Promise<void>((resolve, reject) => {
        hls.on(HlsCtor.Events.MANIFEST_PARSED, () => {
          if (this.audio !== audio) { resolve(); return; }
          audio.play().then(resolve, reject);
        });
        hls.on(HlsCtor.Events.DESTROYING, () => resolve());
        hls.attachMedia(audio);
        hls.loadSource(url);
      });
    } else {
      audio.src = url;
      audio.load();
      await audio.play();
    }
  }

  private async loadHlsConstructor(): Promise<typeof Hls> {
    return (await import('hls.js')).default;
  }

  private usableUrls(urls: string[]): string[] {
    const valid: string[] = [];
    for (const value of urls) {
      try {
        const url = new URL(value);
        if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) continue;
        if (location.protocol === 'https:' && url.protocol === 'http:') url.protocol = 'https:';
        valid.push(url.href);
      } catch { /* Invalid catalog entries are not playable streams. */ }
    }
    return [...new Set(valid)];
  }

  private clearStreamTimer(): void {
    if (this.streamTimer !== undefined) clearTimeout(this.streamTimer);
    this.streamTimer = undefined;
  }

  private releaseAudio(): void {
    this.clearStreamTimer();
    const audio = this.audio;
    this.audio = null;
    this.hls?.destroy();
    this.hls = null;
    if (audio) {
      audio.pause();
      audio.removeAttribute('src');
      audio.load();
    }
  }

  private tryNextStreamCandidate(): void {
    if (this.streamCandidateIndex < this.streamCandidates.length - 1) {
      this.streamCandidateIndex += 1;
      this.playStream(this.streamCandidates[this.streamCandidateIndex]);
      return;
    }

    this.releaseAudio();
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
