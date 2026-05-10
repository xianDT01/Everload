import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicService, PlayerState, MusicMetadataDto } from '../../../services/music.service';
import { ModernStateService } from '../modern-state.service';

@Component({
  selector: 'app-modern-bottombar',
  templateUrl: './modern-bottombar.component.html',
  styleUrls: ['./modern-bottombar.component.css']
})
export class ModernBottombarComponent implements OnInit, OnDestroy {
  state: PlayerState | null = null;
  shuffle = false;
  repeat: 'none' | 'one' | 'all' = 'none';
  showQueue = false;

  // Favorites
  isFav = false;
  private favTrackPath = '';

  // EQ panel
  showEq = false;
  eqBands = [0, 0, 0, 0, 0];
  readonly eqLabels = ['60', '250', '1k', '4k', '16k'];
  crossfade = 0;
  channelMode: 'stereo' | 'mono' | 'left' | 'right' | 'swap' = 'stereo';
  reduceAnimations = false;

  readonly channelModes: ('stereo' | 'mono' | 'left' | 'right' | 'swap')[] = ['stereo', 'mono', 'left', 'right', 'swap'];

  readonly eqPresets: { label: string; bands: number[] }[] = [
    { label: 'Flat',         bands: [0, 0, 0, 0, 0] },
    { label: 'Bass Boost',   bands: [6, 4, 0, 0, 0] },
    { label: 'Treble Boost', bands: [0, 0, 0, 4, 6] },
    { label: 'Vocal Boost',  bands: [-2, 0, 4, 3, -1] },
    { label: 'Loudness',     bands: [5, 2, 0, 2, 5] },
  ];

  private subs: Subscription[] = [];
  private prevVolume = 1;

  constructor(public music: MusicService, public modState: ModernStateService) {}

  ngOnInit() {
    this.crossfade = this.music.crossfadeDuration;
    this.channelMode = (this.music.mainPlayer.channelMode as any) || 'stereo';
    this.reduceAnimations = localStorage.getItem('modern_reduce_animations') === '1';
    this.applyReduceAnimations();
    this.subs.push(
      this.music.mainPlayer.state$.subscribe(s => {
        this.state = s;
        const path = s?.currentTrack?.path;
        if (path && path !== this.favTrackPath) {
          this.favTrackPath = path;
          this.checkFav(s.currentTrack!);
        }
      }),
      this.music.shuffle$.subscribe(v => this.shuffle = v),
      this.music.repeat$.subscribe(v => this.repeat = v),
      this.modState.showQueue$.subscribe(v => this.showQueue = v),
    );
  }

  ngOnDestroy() { this.subs.forEach(s => s.unsubscribe()); }

  get track(): MusicMetadataDto | null { return this.state?.currentTrack ?? null; }
  get playing(): boolean { return this.state?.playing ?? false; }
  get currentTime(): number { return this.state?.currentTime ?? 0; }
  get duration(): number { return this.state?.duration ?? 0; }
  get volume(): number { return this.state?.volume ?? 1; }

  get coverUrl(): string {
    const t = this.track;
    if (!t) return '';
    const pathId = this.state?.pathId ?? t.nasPathId ?? 0;
    return this.music.getCoverUrlWithCache(pathId, t.path, t.source);
  }

  get progress(): number {
    return this.duration > 0 ? (this.currentTime / this.duration) * 100 : 0;
  }

  fmt(s: number): string {
    if (!s || !isFinite(s)) return '0:00';
    return `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;
  }

  onSeek(e: Event) {
    const v = +(e.target as HTMLInputElement).value;
    this.music.mainPlayer.seek((v / 100) * this.duration);
  }

  onVolume(e: Event) {
    const v = +(e.target as HTMLInputElement).value / 100;
    this.prevVolume = v > 0 ? v : this.prevVolume;
    this.music.mainPlayer.setVolume(v);
  }

  onMuteToggle() {
    if (this.volume > 0) {
      this.prevVolume = this.volume;
      this.music.mainPlayer.setVolume(0);
    } else {
      this.music.mainPlayer.setVolume(this.prevVolume || 1);
    }
  }

  toggle() { this.music.mainPlayer.togglePlay(); }
  prev() { this.music.playPrevMain(); }
  next() { this.music.playNextMain(); }
  toggleShuffle() { this.music.toggleShuffle(); }
  toggleRepeat() { this.music.toggleRepeat(); }

  // ── Favorites ────────────────────────────────────────────────────────────

  private checkFav(track: MusicMetadataDto) {
    const pathId = this.state?.pathId ?? track.nasPathId ?? 0;
    if (!track.path || pathId < 0) return;
    this.music.checkFavorite(track.path, pathId).subscribe({
      next: (res: any) => { this.isFav = !!res?.isFavorite; },
      error: () => { this.isFav = false; }
    });
  }

  toggleFav() {
    const t = this.track;
    if (!t) return;
    const pathId = this.state?.pathId ?? t.nasPathId ?? 0;
    this.music.toggleFavorite(t.path, t.title || t.name, t.artist || '', t.album || '', pathId)
      .subscribe({ next: (res: any) => { this.isFav = !!res?.isFavorite; }, error: () => {} });
  }

  // ── EQ ───────────────────────────────────────────────────────────────────

  toggleEq() { this.showEq = !this.showEq; }

  onEqBand(index: number, e: Event) {
    const dB = +(e.target as HTMLInputElement).value;
    this.eqBands[index] = dB;
    this.music.mainPlayer.setEqBand(index, dB);
  }

  resetEq() {
    this.applyPreset({ label: 'Flat', bands: [0, 0, 0, 0, 0] });
  }

  applyPreset(preset: { label: string; bands: number[] }) {
    this.eqBands = [...preset.bands];
    preset.bands.forEach((dB, i) => this.music.mainPlayer.setEqBand(i, dB));
  }

  onCrossfade(e: Event) {
    const v = +(e.target as HTMLInputElement).value;
    this.crossfade = v;
    this.music.crossfadeDuration = v;
  }

  onChannelMode(mode: 'stereo' | 'mono' | 'left' | 'right' | 'swap') {
    this.channelMode = mode;
    this.music.mainPlayer.setChannelMode(mode);
  }

  toggleReduceAnimations() {
    this.reduceAnimations = !this.reduceAnimations;
    localStorage.setItem('modern_reduce_animations', this.reduceAnimations ? '1' : '0');
    this.applyReduceAnimations();
  }

  private applyReduceAnimations() {
    document.documentElement.classList.toggle('reduce-animations', this.reduceAnimations);
  }

  // ── Queue / Fullscreen ────────────────────────────────────────────────────

  openFullscreen() { this.modState.toggleFullscreen(); }
  toggleQueuePanel() { this.modState.toggleQueue(); }
}
