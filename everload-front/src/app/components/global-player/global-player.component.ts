import { Component, ElementRef, Input, OnInit, OnDestroy, HostListener, ViewChild, NgZone, ChangeDetectorRef } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import anime from 'animejs/lib/anime.es.js';
import { MusicService, PlayerState, MusicMetadataDto } from '../../services/music.service';

@Component({
  selector: 'app-global-player',
  templateUrl: './global-player.component.html',
  styleUrls: ['./global-player.component.css', './player-island.css']
})
export class GlobalPlayerComponent implements OnInit, OnDestroy {

  private currentMode: 'full' | 'mini' | 'island' | 'hidden' = 'mini';
  @Input() set mode(value: 'full' | 'mini' | 'island' | 'hidden') {
    this.stopIslandDrag();
    this.currentMode = value;
    this.islandExpanded = false;
  }
  get mode(): 'full' | 'mini' | 'island' | 'hidden' { return this.currentMode; }
  islandExpanded = false;
  @Input() headerIsland = true;
  islandCoverFailed = false;
  islandLocked = false;
  islandPosition: { x: number; y: number } | null = null;
  private readonly islandStorageKey = 'everload.island-position.v1';
  private islandElement?: HTMLElement;
  private islandFrame = 0;
  private islandDrag: { id: number; x: number; y: number; left: number; top: number } | null = null;
  private readonly islandPointerMove = (event: PointerEvent) => {
    this.moveIsland(event);
    this.changeDetector.detectChanges();
  };
  private readonly islandPointerEnd = (event: PointerEvent) => this.endIslandDrag(event);

  @ViewChild('island') set islandView(ref: ElementRef<HTMLElement> | undefined) {
    this.islandElement = ref?.nativeElement;
    this.scheduleIslandFit();
  }

  state: PlayerState | null = null;
  shuffle = false;
  repeat: 'none' | 'one' | 'all' = 'none';
  isLiked = false;

  private subs: Subscription[] = [];
  private aquaAmbientAnimation?: any;

  constructor(
    public musicService: MusicService,
    private router: Router,
    private host: ElementRef<HTMLElement>,
    private zone: NgZone,
    private changeDetector: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    try {
      const saved = JSON.parse(localStorage.getItem(this.islandStorageKey) || 'null');
      if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)) {
        this.islandPosition = { x: saved.x, y: saved.y };
        this.islandLocked = saved.locked === true;
      }
    } catch { /* Storage may be unavailable or contain an old value. */ }
    // Player state subscription
    this.subs.push(this.musicService.mainPlayer.state$.subscribe(s => {
      const prevTrack = this.state?.currentTrack?.path;
      const prevPathId = this.state?.pathId;
      const wasPlaying = this.state?.playing;
      this.state = s;
      
      // If track changed, update liked status and fetch cover
      if (s.currentTrack && (s.currentTrack.path !== prevTrack || s.pathId !== prevPathId)) {
        this.islandCoverFailed = false;
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
    cancelAnimationFrame(this.islandFrame);
    this.stopIslandDrag();
    this.aquaAmbientAnimation?.pause();
    this.subs.forEach(s => s.unsubscribe());
  }

  // ── Controls ──────────────────────────────────────────────────────────────

  toggleIsland(): void {
    this.islandExpanded = !this.islandExpanded;
    this.changeDetector.detectChanges();
    this.scheduleIslandFit();
  }

  startIslandDrag(event: PointerEvent): void {
    if (this.islandLocked || this.islandDrag || event.button !== 0 || !this.islandElement) return;
    event.preventDefault();
    const rect = this.islandElement.getBoundingClientRect();
    this.islandDrag = { id: event.pointerId, x: event.clientX, y: event.clientY, left: rect.left, top: rect.top };
    (event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);
    this.zone.runOutsideAngular(() => {
      document.addEventListener('pointermove', this.islandPointerMove);
      document.addEventListener('pointerup', this.islandPointerEnd);
      document.addEventListener('pointercancel', this.islandPointerEnd);
    });
  }

  moveIsland(event: PointerEvent): void {
    const drag = this.islandDrag;
    if (!drag || event.pointerId !== drag.id) return;
    this.islandPosition = { x: drag.left + event.clientX - drag.x, y: drag.top + event.clientY - drag.y };
    this.fitIsland();
  }

  endIslandDrag(event: PointerEvent): void {
    if (this.islandDrag?.id !== event.pointerId) return;
    this.stopIslandDrag();
    this.saveIslandPosition();
  }

  private stopIslandDrag(): void {
    this.islandDrag = null;
    document.removeEventListener('pointermove', this.islandPointerMove);
    document.removeEventListener('pointerup', this.islandPointerEnd);
    document.removeEventListener('pointercancel', this.islandPointerEnd);
  }

  moveIslandWithKeyboard(event: KeyboardEvent): void {
    if (this.islandLocked || !this.islandElement) return;
    const step = event.shiftKey ? 40 : 10;
    const directions: Record<string, [number, number]> = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
    const delta = directions[event.key];
    if (!delta) return;
    event.preventDefault();
    const rect = this.islandElement.getBoundingClientRect();
    this.islandPosition = { x: rect.left + delta[0], y: rect.top + delta[1] };
    this.fitIsland();
    this.saveIslandPosition();
    this.changeDetector.detectChanges();
  }

  toggleIslandLock(): void {
    if (!this.islandPosition && this.islandElement) {
      const rect = this.islandElement.getBoundingClientRect();
      this.islandPosition = { x: rect.left, y: rect.top };
    }
    this.islandLocked = !this.islandLocked;
    this.stopIslandDrag();
    this.saveIslandPosition();
    this.changeDetector.detectChanges();
  }

  resetIslandPosition(): void {
    this.stopIslandDrag();
    this.islandPosition = null;
    this.islandLocked = false;
    try { localStorage.removeItem(this.islandStorageKey); } catch { /* Optional browser preference. */ }
    this.changeDetector.detectChanges();
  }

  @HostListener('window:resize')
  onIslandViewportResize(): void {
    this.fitIsland();
    this.changeDetector.detectChanges();
  }

  fitIsland(): void {
    if (!this.islandPosition || !this.islandElement) return;
    const rect = this.islandElement.getBoundingClientRect();
    const width = Math.min(this.islandExpanded ? 460 : 350, window.innerWidth - 32);
    this.islandPosition = {
      x: Math.max(12, Math.min(this.islandPosition.x, window.innerWidth - width - 12)),
      y: Math.max(12, Math.min(this.islandPosition.y, window.innerHeight - rect.height - 12))
    };
  }

  private scheduleIslandFit(): void {
    cancelAnimationFrame(this.islandFrame);
    this.islandFrame = requestAnimationFrame(() => {
      this.fitIsland();
      this.changeDetector.detectChanges();
    });
  }

  private saveIslandPosition(): void {
    if (!this.islandPosition) return;
    try { localStorage.setItem(this.islandStorageKey, JSON.stringify({ ...this.islandPosition, locked: this.islandLocked })); }
    catch { /* Playback remains usable when storage is disabled. */ }
  }

  @HostListener('document:click', ['$event'])
  closeIslandOutside(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) this.islandExpanded = false;
  }

  @HostListener('document:keydown.escape')
  collapseIsland(): void {
    if (!this.islandExpanded) return;
    this.islandExpanded = false;
    this.host.nativeElement.querySelector<HTMLButtonElement>('.island-summary')?.focus();
  }

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
    // Lleva al reproductor moderno (no al antiguo EverWave).
    this.router.navigate(['/modern']);
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
    if (!track || this.state?.pathId == null) {
      this.isLiked = false;
      return;
    }
    const pathId = this.state.pathId;
    this.isLiked = false;
    this.musicService.checkFavorite(track.path, pathId).subscribe({
      next: res => {
        if (this.state?.currentTrack?.path === track.path && this.state?.pathId === pathId) this.isLiked = !!res.isFavorite;
      },
      error: () => {}
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
