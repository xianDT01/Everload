import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
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
  private volumeScrollStep = 0.05;

  readonly channelModes: ('stereo' | 'mono' | 'left' | 'right' | 'swap')[] = ['stereo', 'mono', 'left', 'right', 'swap'];

  activeEqPreset = 'Flat';

  readonly eqPresets: { label: string; bands: number[] }[] = [
    { label: 'Flat',       bands: [0, 0, 0, 0, 0] },
    { label: 'Bass',       bands: [6, 4, 1, -1, -2] },
    { label: 'Treble',     bands: [-2, -1, 1, 4, 6] },
    { label: 'Vocal',      bands: [-2, 0, 4, 3, -1] },
    { label: 'Loudness',   bands: [5, 2, 0, 2, 5] },
    { label: 'Pop',        bands: [-1, 2, 4, 2, -1] },
    { label: 'Rock',       bands: [5, 3, -1, 3, 5] },
    { label: 'Dance',      bands: [6, 4, 1, 3, 4] },
    { label: 'Club',       bands: [3, 4, 2, 2, 1] },
    { label: 'Live',       bands: [-1, 0, 3, 4, 2] },
    { label: 'Soft',       bands: [2, 1, 0, 1, 3] },
    { label: 'Techno',     bands: [5, 3, 0, 3, 6] },
    { label: 'Headphones', bands: [3, 2, 1, 2, 4] },
  ];

  private subs: Subscription[] = [];
  private prevVolume = 1;

  constructor(public music: MusicService, public modState: ModernStateService, private router: Router) {}

  ngOnInit() {
    this.crossfade = this.music.crossfadeDuration;
    this.reduceAnimations = localStorage.getItem('mpl_reduce_animations') === 'true';
    this.applyReduceAnimations();
    const step = parseFloat(localStorage.getItem('mpl_vol_scroll_step') ?? '5');
    this.volumeScrollStep = (isFinite(step) && step >= 1 ? step : 5) / 100;
    const savedEq = localStorage.getItem('mpl_eq_bands');
    if (savedEq) {
      try {
        const bands = JSON.parse(savedEq);
        if (Array.isArray(bands) && bands.length === 5) {
          this.eqBands = bands;
          bands.forEach((dB: number, i: number) => this.music.mainPlayer.setEqBand(i, dB));
          this.activeEqPreset = this.resolvePresetLabel(bands);
        }
      } catch {}
    }
    const savedCh = localStorage.getItem('mpl_channel_mode') as any;
    if (savedCh) {
      this.channelMode = savedCh;
      this.music.mainPlayer.setChannelMode(this.channelMode);
    } else {
      this.channelMode = (this.music.mainPlayer.channelMode as any) || 'stereo';
    }
    const savedVol = parseFloat(localStorage.getItem('mpl_volume') ?? '1');
    if (isFinite(savedVol)) {
      this.music.mainPlayer.setVolume(Math.min(1, Math.max(0, savedVol)));
      this.prevVolume = savedVol > 0 ? savedVol : 1;
    }
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
    if (v > 0) this.prevVolume = v;
    this.music.mainPlayer.setVolume(v);
    localStorage.setItem('mpl_volume', String(v));
  }

  onMuteToggle() {
    if (this.volume > 0) {
      this.prevVolume = this.volume;
      this.music.mainPlayer.setVolume(0);
      localStorage.setItem('mpl_volume', '0');
    } else {
      const restore = this.prevVolume || 1;
      this.music.mainPlayer.setVolume(restore);
      localStorage.setItem('mpl_volume', String(restore));
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

  openCurrentArtist() {
    const artist = (this.track?.artist || '').trim();
    if (!artist) return;
    this.modState.selectArtist(artist);
    this.router.navigate(['/modern/artists']);
  }

  // ── EQ ───────────────────────────────────────────────────────────────────

  toggleEq() { this.showEq = !this.showEq; }

  onVolumeWheel(e: WheelEvent) {
    e.preventDefault();
    const dir = e.deltaY < 0 ? 1 : -1;
    const newVol = Math.min(1, Math.max(0, this.volume + dir * this.volumeScrollStep));
    if (newVol > 0) this.prevVolume = newVol;
    this.music.mainPlayer.setVolume(newVol);
    localStorage.setItem('mpl_volume', String(newVol));
  }

  onEqBand(index: number, e: Event) {
    const dB = +(e.target as HTMLInputElement).value;
    this.eqBands[index] = dB;
    this.music.mainPlayer.setEqBand(index, dB);
    localStorage.setItem('mpl_eq_bands', JSON.stringify(this.eqBands));
    this.activeEqPreset = this.resolvePresetLabel(this.eqBands);
  }

  resetEq() {
    this.applyPreset({ label: 'Flat', bands: [0, 0, 0, 0, 0] });
  }

  applyPreset(preset: { label: string; bands: number[] }) {
    this.eqBands = [...preset.bands];
    preset.bands.forEach((dB, i) => this.music.mainPlayer.setEqBand(i, dB));
    localStorage.setItem('mpl_eq_bands', JSON.stringify(this.eqBands));
    this.activeEqPreset = preset.label;
  }

  private resolvePresetLabel(bands: number[]): string {
    const found = this.eqPresets.find(p => p.bands.every((v, i) => Math.abs(v - Number(bands[i] ?? 0)) < 0.01));
    return found?.label ?? 'Custom';
  }

  onCrossfade(e: Event) {
    const v = +(e.target as HTMLInputElement).value;
    this.crossfade = v;
    this.music.crossfadeDuration = v;
    localStorage.setItem('mpl_crossfade_seconds', String(v));
  }

  onChannelMode(mode: 'stereo' | 'mono' | 'left' | 'right' | 'swap') {
    this.channelMode = mode;
    this.music.mainPlayer.setChannelMode(mode);
    localStorage.setItem('mpl_channel_mode', mode);
  }

  toggleReduceAnimations() {
    this.reduceAnimations = !this.reduceAnimations;
    localStorage.setItem('mpl_reduce_animations', String(this.reduceAnimations));
    this.applyReduceAnimations();
  }

  private applyReduceAnimations() {
    document.documentElement.classList.toggle('reduce-animations', this.reduceAnimations);
  }

  // ── Queue / Fullscreen ────────────────────────────────────────────────────

  openFullscreen() { this.modState.toggleFullscreen(); }
  toggleQueuePanel() { this.modState.toggleQueue(); }
}
