import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { MusicService, PlayerState, MusicMetadataDto } from '../../services/music.service';

@Component({
  selector: 'app-global-player',
  templateUrl: './global-player.component.html',
  styleUrls: ['./global-player.component.css']
})
export class GlobalPlayerComponent implements OnInit, OnDestroy {

  @Input() mode: 'full' | 'mini' | 'hidden' = 'mini';

  state: PlayerState | null = null;
  shuffle = false;
  repeat: 'none' | 'one' | 'all' = 'none';
  isLiked = false;

  private subs: Subscription[] = [];

  constructor(
    public musicService: MusicService,
    private router: Router
  ) {}

  ngOnInit(): void {
    // Player state subscription
    this.subs.push(this.musicService.mainPlayer.state$.subscribe(s => {
      const prevTrack = this.state?.currentTrack?.path;
      this.state = s;
      
      // If track changed, update liked status and fetch cover
      if (s.currentTrack && s.currentTrack.path !== prevTrack) {
        this.checkLiked(s.currentTrack);
        this.musicService.fetchCoverIfNeeded(s.currentTrack);
      }
    }));

    // Shuffle/Repeat subscriptions
    this.subs.push(this.musicService.shuffle$.subscribe(v => this.shuffle = v));
    this.subs.push(this.musicService.repeat$.subscribe(v => this.repeat = v));
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
  }

  // ── Controls ──────────────────────────────────────────────────────────────

  togglePlay()  { this.musicService.mainPlayer.togglePlay(); }
  next()        { this.musicService.playNextMain(); }
  prev()        { this.musicService.playPrevMain(); }
  toggleShuffle() { this.musicService.toggleShuffle(); }
  toggleRepeat()  { this.musicService.toggleRepeat(); }

  onSeek(e: Event) {
    this.musicService.mainPlayer.seek(+(e.target as HTMLInputElement).value);
  }

  onVolume(e: Event) {
    this.musicService.mainPlayer.setVolume(+(e.target as HTMLInputElement).value);
  }

  onSeekClick(e: MouseEvent) {
    const bar = (e.currentTarget as HTMLElement);
    const rect = bar.getBoundingClientRect();
    const pct = (e.clientX - rect.left) / rect.width;
    const duration = this.state?.duration ?? 0;
    if (duration > 0) this.musicService.mainPlayer.seek(pct * duration);
  }

  // ── Formatting & Helpers ──────────────────────────────────────────────────

  fmt(seconds: number): string {
    if (!seconds || seconds <= 0) return '—';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  }

  progressPct(): number {
    if (!this.state?.duration) return 0;
    return (this.state.currentTime / this.state.duration) * 100;
  }

  playerCoverUrl(): string {
    const t = this.state?.currentTrack;
    if (!t) return '';
    return this.musicService.getCoverUrlWithCache(this.state?.pathId ?? 0, t.path);
  }

  playerHasCover(): boolean {
    const t = this.state?.currentTrack;
    return !!t && this.musicService.hasCoverToShow(t);
  }

  // ── Navigation ────────────────────────────────────────────────────────────

  get isNasTrack(): boolean {
    return this.state?.pathId != null;
  }

  goToLibrary(): void {
    if (this.isNasTrack) this.router.navigate(['/nas-music']);
  }

  onMiniPlayerClick(e: Event): void {
    if (this.mode === 'mini') {
      this.goToLibrary();
    }
  }

  get sourceLabel(): string {
    const track = this.state?.currentTrack;
    if (!track) return '';
    if (track.source === 'youtube') return 'YT';
    if (track.source === 'local') return 'LOCAL';
    return 'NAS';
  }

  // ── Favorites ─────────────────────────────────────────────────────────────

  private checkLiked(track: MusicMetadataDto) {
    if (!track || !this.state?.pathId) {
      this.isLiked = false;
      return;
    }
    this.musicService.checkFavorite(track.path, this.state.pathId).subscribe(res => {
      this.isLiked = !!res.isFavorite;
    });
  }

  toggleLike(e: Event) {
    e.stopPropagation();
    const track = this.state?.currentTrack;
    const pid = this.state?.pathId;
    if (!track || pid == null) return;

    this.musicService.toggleFavorite(track.path, track.title || track.name, track.artist || '', track.album || '', pid)
      .subscribe((res: any) => {
        this.isLiked = !!res.isFavorite;
      });
  }
}
