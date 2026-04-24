import { Component, OnInit, OnDestroy, AfterViewChecked, HostListener, ViewChild, ElementRef } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicMetadataDto, MusicService, PlayerState } from '../../services/music.service';

@Component({
  selector: 'app-now-playing-panel',
  templateUrl: './now-playing-panel.component.html',
  styleUrls: ['./now-playing-panel.component.css']
})
export class NowPlayingPanelComponent implements OnInit, AfterViewChecked, OnDestroy {

  state: PlayerState | null = null;
  shuffle = false;
  repeat: 'none' | 'one' | 'all' = 'none';

  vizMode: 'bars' | 'wave' | 'scope' = 'bars';
  @ViewChild('panelCanvas') panelCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('desktopPanel') desktopPanel?: ElementRef<HTMLElement>;
  private panelRaf?: number;
  private panelPeaks: number[] = [];

  likedItems: any[] = [];
  readonly Math = Math;

  private subs: Subscription[] = [];
  private wasOpen = false;

  constructor(public musicService: MusicService) {}

  ngOnInit(): void {
    this.subs.push(this.musicService.mainPlayer.state$.subscribe(s => { this.state = s; }));
    this.subs.push(this.musicService.shuffle$.subscribe(v => { this.shuffle = v; }));
    this.subs.push(this.musicService.repeat$.subscribe(v => { this.repeat = v; }));
    this.musicService.getFavorites().subscribe({ next: favs => { this.likedItems = favs; } });
  }

  ngAfterViewChecked(): void {
    const open = this.musicService.nowPlayingPanelOpen;
    if (open && !this.wasOpen) {
      this.wasOpen = true;
      requestAnimationFrame(() => this.startViz());
    } else if (!open && this.wasOpen) {
      this.wasOpen = false;
      this.stopViz();
    }
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.stopViz();
  }

  get isOpen(): boolean { return this.musicService.nowPlayingPanelOpen; }
  get isDesktopWmp(): boolean {
    return typeof window !== 'undefined' && window.innerWidth >= 980;
  }
  get isFullscreen(): boolean {
    return typeof document !== 'undefined' && !!document.fullscreenElement;
  }

  close(): void {
    this.musicService.nowPlayingPanelOpen = false;
    this.stopViz();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void { if (this.isOpen) this.close(); }

  async toggleFullscreen(): Promise<void> {
    if (typeof document === 'undefined') return;
    if (document.fullscreenElement) {
      await document.exitFullscreen().catch(() => {});
      return;
    }
    const target = this.desktopPanel?.nativeElement;
    if (target?.requestFullscreen) {
      await target.requestFullscreen().catch(() => {});
    }
  }

  onSeek(e: MouseEvent): void {
    const bar = e.currentTarget as HTMLElement;
    const pct = e.offsetX / bar.offsetWidth;
    const duration = this.state?.duration ?? 0;
    if (duration > 0) this.musicService.mainPlayer.seek(pct * duration);
  }

  playerHasCover(): boolean {
    const t = this.state?.currentTrack;
    return !!t && this.musicService.hasCoverToShow(t);
  }

  playerCoverUrl(): string {
    const t   = this.state?.currentTrack;
    const pid = this.state?.pathId;
    if (!t || !pid) return '';
    return this.musicService.getCoverUrlWithCache(pid, t.path);
  }

  isLiked(track: MusicMetadataDto): boolean {
    const pid = track.nasPathId ?? this.state?.pathId;
    if (pid === null || pid === undefined) return false;
    return this.likedItems.some(f => f.trackPath === track.path && Number(f.nasPathId) === Number(pid));
  }

  toggleLike(e: Event, track: MusicMetadataDto): void {
    e.stopPropagation();
    const pid = track.nasPathId ?? this.state?.pathId;
    if (pid === null || pid === undefined) return;
    const wasLiked = this.isLiked(track);
    if (wasLiked) {
      this.likedItems = this.likedItems.filter(f => !(f.trackPath === track.path && f.nasPathId === pid));
    } else {
      this.likedItems = [...this.likedItems, { trackPath: track.path, nasPathId: pid }];
    }
    this.musicService.toggleFavorite(track.path, track.title || track.name, track.artist || '', track.album || '', pid as number).subscribe({
      next: (res: any) => {
        const nowLiked = this.isLiked(track);
        if (res.isFavorite && !nowLiked) {
          this.likedItems = [...this.likedItems, { trackPath: track.path, nasPathId: pid }];
        } else if (!res.isFavorite && nowLiked) {
          this.likedItems = this.likedItems.filter(f => !(f.trackPath === track.path && f.nasPathId === pid));
        }
      },
      error: () => {
        if (wasLiked) {
          this.likedItems = [...this.likedItems, { trackPath: track.path, nasPathId: pid }];
        } else {
          this.likedItems = this.likedItems.filter(f => !(f.trackPath === track.path && f.nasPathId === pid));
        }
      }
    });
  }

  fmt(seconds: number): string {
    if (!seconds || seconds <= 0) return '—';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  }

  startViz(): void {
    this.stopViz();
    this.drawViz();
  }

  private stopViz(): void {
    if (this.panelRaf) { cancelAnimationFrame(this.panelRaf); this.panelRaf = undefined; }
  }

  private drawViz(): void {
    if (!this.isOpen) return;
    const canvas = this.panelCanvas?.nativeElement;
    if (!canvas) { this.panelRaf = requestAnimationFrame(() => this.drawViz()); return; }

    const dpr = window.devicePixelRatio || 1;
    const cssW = canvas.offsetWidth;
    const cssH = canvas.offsetHeight;
    if (canvas.width !== Math.round(cssW * dpr) || canvas.height !== Math.round(cssH * dpr)) {
      canvas.width  = Math.round(cssW * dpr);
      canvas.height = Math.round(cssH * dpr);
    }
    const ctx = canvas.getContext('2d')!;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const W = cssW;
    const H = cssH;
    ctx.clearRect(0, 0, W, H);

    if      (this.vizMode === 'bars') this.drawBars(ctx, W, H);
    else if (this.vizMode === 'wave') this.drawWave(ctx, W, H);
    else                              this.drawScope(ctx, W, H);

    ctx.setTransform(1, 0, 0, 1, 0, 0);
    this.panelRaf = requestAnimationFrame(() => this.drawViz());
  }

  private drawBars(ctx: CanvasRenderingContext2D, W: number, H: number): void {
    const data = this.musicService.mainPlayer.getFrequencyData();
    const BAR_COUNT = 80;
    const gap  = 2;
    const barW = (W - gap * (BAR_COUNT - 1)) / BAR_COUNT;

    if (this.panelPeaks.length !== BAR_COUNT) this.panelPeaks = new Array(BAR_COUNT).fill(0);

    const baseline = H * 0.72;
    const maxBarH  = baseline * 0.95;
    const reflZone = H - baseline;
    const hueStep  = 240 / BAR_COUNT;

    for (let i = 0; i < BAR_COUNT; i++) {
      let value = 0;
      if (data) {
        const di = Math.floor((i / BAR_COUNT) * data.length * 0.75);
        value = Math.pow(data[di] / 255, 0.75);
      }
      const barH = Math.max(2, value * maxBarH);
      const x    = i * (barW + gap);
      const hue  = i * hueStep;

      const grad = ctx.createLinearGradient(0, baseline, 0, baseline - barH);
      grad.addColorStop(0,   `hsla(${hue},      100%,50%,0.9)`);
      grad.addColorStop(0.6, `hsla(${hue + 30}, 100%,62%,0.9)`);
      grad.addColorStop(1,   `hsla(${hue + 60}, 100%,78%,1)`);
      ctx.fillStyle  = grad;
      ctx.shadowColor = `hsla(${hue},100%,65%,0.7)`;
      ctx.shadowBlur  = 10;
      ctx.fillRect(x, baseline - barH, barW, barH);

      const reflH = Math.min(barH * 0.35, reflZone);
      ctx.shadowBlur  = 0;
      ctx.globalAlpha = 0.22;
      const rg = ctx.createLinearGradient(0, baseline, 0, baseline + reflH);
      rg.addColorStop(0, `hsla(${hue},100%,50%,0.7)`);
      rg.addColorStop(1, `hsla(${hue},100%,50%,0)`);
      ctx.fillStyle = rg;
      ctx.fillRect(x, baseline, barW, reflH);
      ctx.globalAlpha = 1;

      if (barH > this.panelPeaks[i]) this.panelPeaks[i] = barH;
      else this.panelPeaks[i] = Math.max(0, this.panelPeaks[i] - 1.2);
      if (this.panelPeaks[i] > 3) {
        ctx.shadowColor = '#fff'; ctx.shadowBlur = 6; ctx.fillStyle = '#fff';
        ctx.fillRect(x, baseline - this.panelPeaks[i] - 2, barW, 2);
      }
    }
    ctx.shadowBlur  = 0;
    ctx.strokeStyle = 'rgba(255,255,255,0.08)';
    ctx.lineWidth   = 1;
    ctx.beginPath(); ctx.moveTo(0, baseline); ctx.lineTo(W, baseline); ctx.stroke();
  }

  private drawWave(ctx: CanvasRenderingContext2D, W: number, H: number): void {
    const data    = this.musicService.mainPlayer.getTimeDomainData();
    const cy      = H / 2;
    const samples = data ? Math.min(data.length, W * 2) : 0;

    const drawLine = (alpha: number, blur: number, color: string, width: number, flip: boolean) => {
      ctx.shadowBlur  = blur;
      ctx.shadowColor = color;
      ctx.strokeStyle = color;
      ctx.lineWidth   = width;
      ctx.globalAlpha = alpha;
      ctx.beginPath();
      for (let i = 0; i < samples; i++) {
        const x = (i / samples) * W;
        const v = (data![i] / 128.0) - 1;
        const y = cy + (flip ? -v : v) * cy * 0.85;
        i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
      }
      if (!data) { ctx.moveTo(0, cy); ctx.lineTo(W, cy); }
      ctx.stroke();
      ctx.globalAlpha = 1;
    };

    drawLine(1, 18, '#00e5ff', 2.5, false);
    drawLine(1, 6,  '#e0f7fa', 1.2, false);
    drawLine(0.3, 8, '#00e5ff', 1.5, true);
    ctx.shadowBlur = 0;
  }

  private drawScope(ctx: CanvasRenderingContext2D, W: number, H: number): void {
    const data    = this.musicService.mainPlayer.getTimeDomainData();
    const cy      = H / 2;
    const samples = data ? Math.min(data.length, W * 2) : 0;

    const areaGrad = ctx.createLinearGradient(0, 0, 0, H);
    areaGrad.addColorStop(0,   'rgba(29,185,84,0)');
    areaGrad.addColorStop(0.4, 'rgba(29,185,84,0.55)');
    areaGrad.addColorStop(0.5, 'rgba(29,185,84,0.8)');
    areaGrad.addColorStop(0.6, 'rgba(29,185,84,0.55)');
    areaGrad.addColorStop(1,   'rgba(29,185,84,0)');

    ctx.beginPath();
    ctx.moveTo(0, cy);
    for (let i = 0; i < samples; i++) {
      const x = (i / samples) * W;
      const v = (data![i] / 128.0) - 1;
      ctx.lineTo(x, cy + v * cy * 0.85);
    }
    if (!data || samples === 0) ctx.lineTo(W, cy);
    ctx.lineTo(W, cy); ctx.closePath();
    ctx.fillStyle = areaGrad; ctx.fill();

    ctx.shadowBlur = 14; ctx.shadowColor = '#1db954';
    ctx.strokeStyle = '#1db954'; ctx.lineWidth = 2;
    ctx.beginPath();
    for (let i = 0; i < samples; i++) {
      const x = (i / samples) * W;
      const v = (data![i] / 128.0) - 1;
      i === 0 ? ctx.moveTo(x, cy + v * cy * 0.85) : ctx.lineTo(x, cy + v * cy * 0.85);
    }
    if (!data || samples === 0) { ctx.moveTo(0, cy); ctx.lineTo(W, cy); }
    ctx.stroke();

    ctx.shadowBlur = 0; ctx.strokeStyle = 'rgba(29,185,84,0.3)'; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(0, cy); ctx.lineTo(W, cy); ctx.stroke();
  }
}
