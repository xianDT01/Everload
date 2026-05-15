import { Component, ElementRef, Input, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import anime from 'animejs/lib/anime.es.js';
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
  private aquaAmbientAnimation?: any;

  constructor(
    public musicService: MusicService,
    private router: Router,
    private host: ElementRef<HTMLElement>
  ) {}

  ngOnInit(): void {
    // Player state subscription
    this.subs.push(this.musicService.mainPlayer.state$.subscribe(s => {
      const prevTrack = this.state?.currentTrack?.path;
      const wasPlaying = this.state?.playing;
      this.state = s;
      
      // If track changed, update liked status and fetch cover
      if (s.currentTrack && s.currentTrack.path !== prevTrack) {
        this.checkLiked(s.currentTrack);
        this.musicService.fetchCoverIfNeeded(s.currentTrack);
        this.animateTrackChange();
      } else if (s.playing !== wasPlaying) {
        this.animatePlayState(s.playing);
      }
    }));

    // Shuffle/Repeat subscriptions
    this.subs.push(this.musicService.shuffle$.subscribe(v => this.shuffle = v));
    this.subs.push(this.musicService.repeat$.subscribe(v => this.repeat = v));
  }

  ngOnDestroy(): void {
    this.aquaAmbientAnimation?.pause();
    this.subs.forEach(s => s.unsubscribe());
  }

  // ── Controls ──────────────────────────────────────────────────────────────

  togglePlay()  { this.musicService.mainPlayer.togglePlay(); this.animateControlTap('.pb-play, .mini-play'); }
  next()        { this.musicService.playNextMain(); this.animateSkipTap(1); }
  prev()        { this.musicService.playPrevMain(); this.animateSkipTap(0); }
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

  private get prefersReducedMotion(): boolean {
    return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  private isAquaSkinActive(): boolean {
    return typeof document !== 'undefined' && document.body.classList.contains('nas-library-skin-aqua');
  }

  private playerRoot(): HTMLElement | null {
    return this.host.nativeElement.querySelector('.player-bar, .mini-bar');
  }

  private animateTrackChange(): void {
    if (this.prefersReducedMotion) return;
    setTimeout(() => {
      const root = this.playerRoot();
      if (!root) return;

      anime.remove(root);
      anime({
        targets: root,
        translateY: [18, 0],
        opacity: [0.55, 1],
        scale: [0.985, 1],
        duration: 560,
        easing: 'easeOutExpo'
      });

      const art = root.querySelector('.pb-art, .mini-art');
      const text = root.querySelectorAll('.pb-title, .pb-artist, .mini-title, .mini-artist');
      const trackTargets = [art, ...Array.from(text)].filter(Boolean);
      anime.remove(trackTargets);
      anime({
        targets: art,
        scale: [0.88, 1],
        rotate: ['-3deg', '0deg'],
        duration: 720,
        easing: 'easeOutElastic(1, .7)'
      });
      anime({
        targets: text,
        translateX: [-10, 0],
        opacity: [0, 1],
        delay: anime.stagger(45),
        duration: 460,
        easing: 'easeOutCubic'
      });

      this.startAquaAmbientGlow(root);
    });
  }

  private animatePlayState(playing: boolean): void {
    if (this.prefersReducedMotion) return;
    setTimeout(() => {
      const root = this.playerRoot();
      if (!root) return;
      const play = root.querySelector('.pb-play, .mini-play');
      anime.remove(play);
      anime({
        targets: play,
        scale: playing ? [1, 1.16, 1] : [1, 0.92, 1],
        duration: 430,
        easing: 'easeOutBack'
      });
      this.startAquaAmbientGlow(root);
    });
  }

  private animateControlTap(selector: string): void {
    if (this.prefersReducedMotion) return;
    const target = this.host.nativeElement.querySelector(selector);
    if (!target) return;
    anime.remove(target);
    anime({
      targets: target,
      scale: [1, 0.88, 1],
      duration: 260,
      easing: 'easeOutQuad'
    });
  }

  private animateSkipTap(index: 0 | 1): void {
    if (this.prefersReducedMotion) return;
    const target = this.host.nativeElement.querySelectorAll('.pb-skip')[index];
    if (!target) return;
    anime.remove(target);
    anime({
      targets: target,
      scale: [1, 0.88, 1],
      duration: 260,
      easing: 'easeOutQuad'
    });
  }

  private startAquaAmbientGlow(root: HTMLElement): void {
    if (!this.isAquaSkinActive() || this.prefersReducedMotion) return;
    this.aquaAmbientAnimation?.pause();
    const fill = root.querySelectorAll('.pb-fill, .vol-fill, .mini-progress-fill');
    const play = root.querySelector('.pb-play, .mini-play');
    const targets = [play, ...Array.from(fill)].filter(Boolean);
    if (!targets.length) return;
    this.aquaAmbientAnimation = anime({
      targets,
      filter: ['brightness(1)', 'brightness(1.24)', 'brightness(1)'],
      duration: 2200,
      easing: 'easeInOutSine',
      loop: true
    });
  }
}
