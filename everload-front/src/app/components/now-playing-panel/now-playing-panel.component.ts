import { Component, OnInit, OnDestroy, AfterViewChecked, HostListener, ViewChild, ElementRef } from '@angular/core';
import { HttpEventType } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { MusicMetadataDto, MusicService, PlayerState } from '../../services/music.service';
import { NasPath, NasService } from '../../services/nas.service';
import { ChatMessageDto, ChatService } from '../../services/chat.service';
import { AuthService } from '../../services/auth.service';

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
  taskbarClock = '';

  desktopStartOpen = false;
  desktopExplorerOpen = true;
  messengerOpen = false;
  messengerBuzzing = false;
  playerMinimized = false;
  explorerMinimized = false;
  messengerMinimized = false;
  explorerMaximized = false;
  messengerMaximized = false;
  activeDesktopWindow: 'player' | 'explorer' | 'messenger' = 'player';
  desktopPanelPosition = { x: 0, y: 0 };
  desktopPanelSize = { width: 1060, height: 690 };
  explorerWindowPosition = { x: 132, y: 84 };
  explorerWindowSize = { width: 560, height: 420 };
  private explorerRestoreWindow = {
    position: { x: 132, y: 84 },
    size: { width: 560, height: 420 }
  };
  messengerWindowPosition = { x: 220, y: 112 };
  messengerWindowSize = { width: 760, height: 560 };
  private messengerRestoreWindow = {
    position: { x: 220, y: 112 },
    size: { width: 760, height: 560 }
  };
  explorerPaths: NasPath[] = [];
  explorerPathId: number | null = null;
  explorerSubPath = '';
  explorerItems: MusicMetadataDto[] = [];
  explorerSelectedPath: string | null = null;
  explorerLoading = false;
  explorerError = '';
  private clockTimer?: number;
  private dragState: { target: 'player' | 'explorer' | 'messenger'; offsetX: number; offsetY: number } | null = null;
  private resizeState: {
    target: 'player' | 'explorer' | 'messenger';
    startX: number;
    startY: number;
    startWidth: number;
    startHeight: number;
    startLeft: number;
    startTop: number;
    edgeX: 'left' | 'right';
    edgeY: 'top' | 'bottom';
  } | null = null;

  private subs: Subscription[] = [];
  private wasOpen = false;

  constructor(
    public musicService: MusicService,
    private nasService: NasService,
    private chatService: ChatService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.subs.push(this.musicService.mainPlayer.state$.subscribe(s => { this.state = s; }));
    this.subs.push(this.musicService.shuffle$.subscribe(v => { this.shuffle = v; }));
    this.subs.push(this.musicService.repeat$.subscribe(v => { this.repeat = v; }));
    this.musicService.getFavorites().subscribe({ next: favs => { this.likedItems = favs; } });
    this.loadExplorerPaths();
    this.updateTaskbarClock();
    this.resetDesktopWindowPositions();
    this.clockTimer = window.setInterval(() => this.updateTaskbarClock(), 30000);
    this.subs.push(this.chatService.newMessageAlert$.subscribe(alert => {
      if (!this.canPlayWindowsChatSound()) return;
      if (alert.content === 'Zumbido') {
        this.showIncomingMessengerBuzz();
        return;
      }
      this.playMsnMessageReceived();
    }));
  }

  ngAfterViewChecked(): void {
    const open = this.musicService.nowPlayingPanelOpen;
    if (open && !this.wasOpen) {
      this.wasOpen = true;
      this.playSound('assets/Windows%20songs/Voicy_Windows%20XP%20Startup.mp3', 0.7);
      requestAnimationFrame(() => this.startViz());
    } else if (!open && this.wasOpen) {
      this.wasOpen = false;
      this.stopViz();
    }
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.stopViz();
    if (this.clockTimer) window.clearInterval(this.clockTimer);
  }

  get isOpen(): boolean { return this.musicService.nowPlayingPanelOpen; }
  get isDesktopWmp(): boolean {
    return typeof window !== 'undefined' && window.innerWidth >= 980;
  }
  get isFullscreen(): boolean {
    return typeof document !== 'undefined' && !!document.fullscreenElement;
  }
  get desktopPanelStyle(): Record<string, string> {
    if (!this.isDesktopWmp || this.isFullscreen) return {};
    return {
      left: `${this.desktopPanelPosition.x}px`,
      top: `${this.desktopPanelPosition.y}px`,
      width: `${this.desktopPanelSize.width}px`,
      height: `${this.desktopPanelSize.height}px`,
      zIndex: this.activeDesktopWindow === 'player' ? '9501' : '9498',
      transform: 'none'
    };
  }
  get explorerWindowStyle(): Record<string, string> {
    if (this.isFullscreen) return {};
    return {
      left: `${this.explorerWindowPosition.x}px`,
      top: `${this.explorerWindowPosition.y}px`,
      width: `${this.explorerWindowSize.width}px`,
      height: `${this.explorerWindowSize.height}px`,
      zIndex: this.activeDesktopWindow === 'explorer' ? '9502' : '9497'
    };
  }
  get messengerWindowStyle(): Record<string, string> {
    if (this.isFullscreen) return {};
    return {
      left: `${this.messengerWindowPosition.x}px`,
      top: `${this.messengerWindowPosition.y}px`,
      width: `${this.messengerWindowSize.width}px`,
      height: `${this.messengerWindowSize.height}px`,
      zIndex: this.activeDesktopWindow === 'messenger' ? '9503' : '9496'
    };
  }
  get canManageNas(): boolean {
    return this.authService.canManageNas();
  }

  close(): void {
    this.playSound('assets/Windows%20songs/Voicy_Windows%20XP%20Shutdown.mp3', 0.7);
    this.musicService.nowPlayingPanelOpen = false;
    this.desktopStartOpen = false;
    this.playerMinimized = false;
    this.explorerMinimized = false;
    this.messengerMinimized = false;
    this.stopViz();
  }

  onDesktopBackdropClick(): void {
    this.desktopStartOpen = false;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void { if (this.isOpen) this.close(); }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.clampAllWindows();
  }

  @HostListener('document:mousemove', ['$event'])
  onDocumentMouseMove(event: MouseEvent): void {
    if (this.isFullscreen) return;
    if (this.resizeState) {
      this.handleWindowResize(event);
      return;
    }
    if (this.dragState) {
    const nextX = event.clientX - this.dragState.offsetX;
    const nextY = event.clientY - this.dragState.offsetY;
    if (this.dragState.target === 'player') {
      this.desktopPanelPosition = this.clampWindowPosition(nextX, nextY, this.desktopPanelSize);
    } else if (this.dragState.target === 'explorer') {
      this.explorerWindowPosition = this.clampWindowPosition(nextX, nextY, this.explorerWindowSize);
    } else {
      this.messengerWindowPosition = this.clampWindowPosition(nextX, nextY, this.messengerWindowSize);
    }
  }
  }

  @HostListener('document:mouseup')
  onDocumentMouseUp(): void {
    this.dragState = null;
    this.resizeState = null;
  }

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

  toggleStartMenu(): void {
    this.desktopStartOpen = !this.desktopStartOpen;
  }

  focusWindow(target: 'player' | 'explorer' | 'messenger'): void {
    this.activeDesktopWindow = target;
    this.desktopStartOpen = false;
  }

  beginWindowDrag(event: MouseEvent, target: 'player' | 'explorer' | 'messenger'): void {
    if (this.isFullscreen) return;
    if (target === 'explorer' && this.explorerMaximized) return;
    if (target === 'messenger' && this.messengerMaximized) return;
    this.focusWindow(target);
    const position = target === 'player'
      ? this.desktopPanelPosition
      : target === 'explorer'
        ? this.explorerWindowPosition
        : this.messengerWindowPosition;
    this.dragState = {
      target,
      offsetX: event.clientX - position.x,
      offsetY: event.clientY - position.y
    };
    event.preventDefault();
  }

  beginWindowResize(
    event: MouseEvent,
    target: 'player' | 'explorer' | 'messenger',
    edgeX: 'left' | 'right',
    edgeY: 'top' | 'bottom'
  ): void {
    if (this.isFullscreen) return;
    if (target === 'explorer' && this.explorerMaximized) return;
    if (target === 'messenger' && this.messengerMaximized) return;
    this.focusWindow(target);
    const position = target === 'player'
      ? this.desktopPanelPosition
      : target === 'explorer'
        ? this.explorerWindowPosition
        : this.messengerWindowPosition;
    const size = target === 'player'
      ? this.desktopPanelSize
      : target === 'explorer'
        ? this.explorerWindowSize
        : this.messengerWindowSize;
    this.resizeState = {
      target,
      startX: event.clientX,
      startY: event.clientY,
      startWidth: size.width,
      startHeight: size.height,
      startLeft: position.x,
      startTop: position.y,
      edgeX,
      edgeY
    };
    event.preventDefault();
    event.stopPropagation();
  }

  openExplorerWindow(): void {
    this.desktopExplorerOpen = true;
    this.explorerMinimized = false;
    this.activeDesktopWindow = 'explorer';
    this.desktopStartOpen = false;
    this.ensureExplorerContext();
  }

  closeExplorerWindow(): void {
    this.desktopExplorerOpen = false;
    this.explorerMinimized = false;
    this.explorerMaximized = false;
    this.desktopStartOpen = false;
  }

  closePlayerWindow(): void {
    this.playerMinimized = true;
    if (this.activeDesktopWindow === 'player') {
      if (this.desktopExplorerOpen && !this.explorerMinimized) this.activeDesktopWindow = 'explorer';
      else if (this.messengerOpen && !this.messengerMinimized) this.activeDesktopWindow = 'messenger';
    }
  }

  toggleExplorerMaximize(): void {
    if (typeof window === 'undefined') return;
    this.focusWindow('explorer');
    if (this.explorerMaximized) {
      this.explorerWindowSize = this.clampWindowSize(
        'explorer',
        this.explorerRestoreWindow.size.width,
        this.explorerRestoreWindow.size.height
      );
      this.explorerWindowPosition = this.clampWindowPosition(
        this.explorerRestoreWindow.position.x,
        this.explorerRestoreWindow.position.y,
        this.explorerWindowSize
      );
      this.explorerMaximized = false;
      return;
    }

    this.explorerRestoreWindow = {
      position: { ...this.explorerWindowPosition },
      size: { ...this.explorerWindowSize }
    };
    this.explorerMaximized = true;
    this.applyExplorerMaximizedBounds();
  }

  openMessengerWindow(): void {
    const wasOpen = this.messengerOpen;
    this.messengerOpen = true;
    this.messengerMinimized = false;
    this.activeDesktopWindow = 'messenger';
    this.desktopStartOpen = false;
    if (!wasOpen) this.playMsnOnline();
  }

  closeMessengerWindow(): void {
    this.messengerOpen = false;
    this.messengerMinimized = false;
    this.messengerMaximized = false;
    if (this.activeDesktopWindow === 'messenger') {
      this.activeDesktopWindow = this.desktopExplorerOpen ? 'explorer' : 'player';
    }
  }

  toggleMessengerMaximize(): void {
    if (typeof window === 'undefined') return;
    this.focusWindow('messenger');
    if (this.messengerMaximized) {
      this.messengerWindowSize = this.clampWindowSize(
        'messenger',
        this.messengerRestoreWindow.size.width,
        this.messengerRestoreWindow.size.height
      );
      this.messengerWindowPosition = this.clampWindowPosition(
        this.messengerRestoreWindow.position.x,
        this.messengerRestoreWindow.position.y,
        this.messengerWindowSize
      );
      this.messengerMaximized = false;
      return;
    }

    this.messengerRestoreWindow = {
      position: { ...this.messengerWindowPosition },
      size: { ...this.messengerWindowSize }
    };
    this.messengerMaximized = true;
    this.applyMessengerMaximizedBounds();
  }

  minimizeWindow(target: 'player' | 'explorer' | 'messenger'): void {
    if (target === 'player') this.playerMinimized = true;
    if (target === 'explorer') this.explorerMinimized = true;
    if (target === 'messenger') this.messengerMinimized = true;
    if (this.activeDesktopWindow === target) {
      if (target !== 'player' && !this.playerMinimized) this.activeDesktopWindow = 'player';
      else if (target !== 'explorer' && this.desktopExplorerOpen && !this.explorerMinimized) this.activeDesktopWindow = 'explorer';
      else if (target !== 'messenger' && this.messengerOpen && !this.messengerMinimized) this.activeDesktopWindow = 'messenger';
    }
  }

  toggleTaskWindow(target: 'player' | 'explorer' | 'messenger'): void {
    if (target === 'player') {
      this.playerMinimized = !this.playerMinimized;
      if (!this.playerMinimized) this.focusWindow('player');
      return;
    }
    if (target === 'explorer') {
      if (!this.desktopExplorerOpen) {
        this.openExplorerWindow();
        return;
      }
      this.explorerMinimized = !this.explorerMinimized;
      if (!this.explorerMinimized) this.focusWindow('explorer');
      return;
    }
    if (!this.messengerOpen) {
      this.openMessengerWindow();
      return;
    }
    this.messengerMinimized = !this.messengerMinimized;
    if (!this.messengerMinimized) this.focusWindow('messenger');
  }

  triggerMessengerBuzz(): void {
    this.focusWindow('messenger');
    this.runMessengerBuzz();
    const groupId = this.chatService.currentPollGroupId;
    if (groupId !== null) {
      this.chatService.sendBuzz(groupId).subscribe({ error: () => {} });
    }
  }

  onMessengerBuzzReceived(_message: ChatMessageDto): void {
    if (!this.canPlayWindowsChatSound()) return;
    this.showIncomingMessengerBuzz();
  }

  private showIncomingMessengerBuzz(): void {
    if (!this.messengerOpen) this.openMessengerWindow();
    this.messengerMinimized = false;
    this.focusWindow('messenger');
    this.runMessengerBuzz();
  }

  private runMessengerBuzz(): void {
    this.messengerBuzzing = true;
    window.setTimeout(() => {
      this.messengerBuzzing = false;
    }, 560);
    this.playMessengerBuzzInspired();
  }

  openCurrentTrackFolder(): void {
    this.desktopStartOpen = false;
    this.desktopExplorerOpen = true;
    this.activeDesktopWindow = 'explorer';
    this.ensureExplorerContext(true);
  }

  selectExplorerPath(pathId: number): void {
    if (this.explorerPathId === pathId && !this.explorerSubPath && this.explorerItems.length) return;
    this.explorerPathId = pathId;
    this.explorerSubPath = '';
    this.loadExplorerItems();
  }

  openExplorerFolder(item: MusicMetadataDto): void {
    if (!item.directory) return;
    this.explorerSubPath = item.path;
    this.explorerSelectedPath = null;
    this.loadExplorerItems();
  }

  goExplorerUp(): void {
    if (!this.explorerSubPath) return;
    const parts = this.explorerSubPath.split(/[/\\]/).filter(Boolean);
    parts.pop();
    this.explorerSubPath = parts.join('/');
    this.explorerSelectedPath = null;
    this.loadExplorerItems();
  }

  playExplorerTrack(track: MusicMetadataDto): void {
    if (!this.explorerPathId || track.directory) return;
    const tracks = this.explorerItems.filter(item => !item.directory);
    const index = tracks.findIndex(item => item.path === track.path);
    this.musicService.setQueue(this.explorerPathId, tracks, Math.max(0, index));
  }

  selectExplorerItem(item: MusicMetadataDto): void {
    this.explorerSelectedPath = item.path;
  }

  isExplorerItemSelected(item: MusicMetadataDto): boolean {
    return this.explorerSelectedPath === item.path;
  }

  get explorerSelectedItem(): MusicMetadataDto | null {
    return this.explorerItems.find(item => item.path === this.explorerSelectedPath) || null;
  }

  get currentVolumePercent(): number {
    return Math.round((this.state?.volume ?? 1) * 100);
  }

  setDesktopVolume(value: number | string): void {
    const numeric = typeof value === 'string' ? Number(value) : value;
    this.musicService.mainPlayer.setVolume(Math.max(0, Math.min(1, numeric / 100)));
  }

  nudgeDesktopVolume(delta: number): void {
    this.setDesktopVolume(this.currentVolumePercent + delta);
  }

  createExplorerFolder(): void {
    if (!this.canManageNas) return;
    if (this.explorerPathId == null) return;
    const folderName = window.prompt('Nombre de la nueva carpeta');
    if (!folderName?.trim()) return;
    this.explorerLoading = true;
    this.nasService.mkdir(this.explorerPathId, folderName.trim(), this.explorerSubPath || undefined).subscribe({
      next: () => this.loadExplorerItems(),
      error: (err) => {
        this.explorerLoading = false;
        this.explorerError = err.error?.error || 'No se pudo crear la carpeta.';
      }
    });
  }

  renameExplorerItem(): void {
    if (!this.canManageNas) return;
    const item = this.explorerSelectedItem;
    if (!item || this.explorerPathId == null) return;
    const nextName = window.prompt('Nuevo nombre', item.name);
    if (!nextName?.trim() || nextName.trim() === item.name) return;
    this.explorerLoading = true;
    this.nasService.rename(this.explorerPathId, item.path, nextName.trim()).subscribe({
      next: () => this.loadExplorerItems(),
      error: (err) => {
        this.explorerLoading = false;
        this.explorerError = err.error?.error || 'No se pudo renombrar.';
      }
    });
  }

  moveExplorerItem(): void {
    if (!this.canManageNas) return;
    const item = this.explorerSelectedItem;
    if (!item || this.explorerPathId == null) return;
    const destination = window.prompt('Mover a carpeta', this.explorerSubPath || '');
    if (destination === null) return;
    this.explorerLoading = true;
    this.nasService.move(this.explorerPathId, item.path, destination.trim()).subscribe({
      next: () => this.loadExplorerItems(),
      error: (err) => {
        this.explorerLoading = false;
        this.explorerError = err.error?.error || 'No se pudo mover.';
      }
    });
  }

  deleteExplorerItem(): void {
    if (!this.canManageNas) return;
    const item = this.explorerSelectedItem;
    if (!item || this.explorerPathId == null) return;
    const ok = window.confirm(`Eliminar ${item.directory ? 'la carpeta' : 'el archivo'} "${item.name}"?`);
    if (!ok) return;
    this.explorerLoading = true;
    this.nasService.deleteFile(this.explorerPathId, item.path).subscribe({
      next: () => {
        this.explorerSelectedPath = null;
        this.loadExplorerItems();
      },
      error: (err) => {
        this.explorerLoading = false;
        this.explorerError = err.error?.error || 'No se pudo eliminar.';
      }
    });
  }

  onExplorerFilesSelected(event: Event): void {
    if (!this.canManageNas) return;
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files || []);
    input.value = '';
    if (!files.length || this.explorerPathId == null) return;
    this.uploadExplorerFiles(files);
  }

  onExplorerFolderSelected(event: Event): void {
    if (!this.canManageNas) return;
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files || []);
    input.value = '';
    if (!files.length || this.explorerPathId == null) return;
    const relativePaths = files.map(file => (file as any).webkitRelativePath || file.name);
    this.uploadExplorerFiles(files, relativePaths);
  }

  get explorerFolders(): MusicMetadataDto[] {
    return this.explorerItems.filter(item => item.directory);
  }

  get explorerTracks(): MusicMetadataDto[] {
    return this.explorerItems.filter(item => !item.directory);
  }

  get explorerPathName(): string {
    return this.explorerPaths.find(path => path.id === this.explorerPathId)?.name || 'NAS';
  }

  get explorerCrumbs(): string[] {
    return this.explorerSubPath.split(/[/\\]/).filter(Boolean);
  }

  private loadExplorerPaths(): void {
    this.nasService.getPaths().subscribe({
      next: (paths) => {
        this.explorerPaths = paths;
        this.ensureExplorerContext();
      },
      error: () => {
        this.explorerError = 'No se pudo cargar el NAS Explorer.';
      }
    });
  }

  private ensureExplorerContext(forceCurrentTrackFolder = false): void {
    if (!this.explorerPaths.length) return;

    const playerPathId = this.state?.pathId ?? null;
    if ((forceCurrentTrackFolder || this.explorerPathId == null) && playerPathId != null) {
      this.explorerPathId = playerPathId;
      if (forceCurrentTrackFolder && this.state?.currentTrack?.path) {
        const parts = this.state.currentTrack.path.split(/[/\\]/).filter(Boolean);
        parts.pop();
        this.explorerSubPath = parts.join('/');
      }
      this.loadExplorerItems();
      return;
    }

    if (this.explorerPathId == null) {
      this.explorerPathId = this.explorerPaths[0].id;
      this.loadExplorerItems();
    }
  }

  private loadExplorerItems(): void {
    if (this.explorerPathId == null) return;
    this.explorerLoading = true;
    this.explorerError = '';
    this.musicService.browse(this.explorerPathId, this.explorerSubPath, 0, 250).subscribe({
      next: (result) => {
        this.explorerItems = result.items;
        if (this.explorerSelectedPath && !this.explorerItems.some(item => item.path === this.explorerSelectedPath)) {
          this.explorerSelectedPath = null;
        }
        this.explorerLoading = false;
      },
      error: () => {
        this.explorerItems = [];
        this.explorerLoading = false;
        this.explorerError = 'No se pudo abrir esta carpeta del NAS.';
      }
    });
  }

  private updateTaskbarClock(): void {
    const now = new Date();
    this.taskbarClock = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  private resetDesktopWindowPositions(): void {
    if (typeof window === 'undefined') return;
    const player = this.getDefaultPlayerWindowSize();
    const explorer = this.getDefaultExplorerWindowSize();
    const messenger = this.getDefaultMessengerWindowSize();
    this.desktopPanelSize = player;
    this.desktopPanelPosition = {
      x: Math.max(24, Math.round((window.innerWidth - player.width) / 2)),
      y: Math.max(24, Math.round((window.innerHeight - player.height) / 2) - 12)
    };
    this.explorerWindowSize = explorer;
    this.explorerWindowPosition = this.clampWindowPosition(132, 84, explorer);
    this.messengerWindowSize = messenger;
    this.messengerWindowPosition = this.clampWindowPosition(220, 112, messenger);
  }

  private clampAllWindows(): void {
    if (typeof window === 'undefined' || !this.isDesktopWmp) return;
    this.desktopPanelPosition = this.clampWindowPosition(
      this.desktopPanelPosition.x,
      this.desktopPanelPosition.y,
      this.desktopPanelSize
    );
    if (this.explorerMaximized) {
      this.applyExplorerMaximizedBounds();
    } else {
      this.explorerWindowPosition = this.clampWindowPosition(
        this.explorerWindowPosition.x,
        this.explorerWindowPosition.y,
        this.explorerWindowSize
      );
    }
    if (this.messengerMaximized) {
      this.applyMessengerMaximizedBounds();
    } else {
      this.messengerWindowPosition = this.clampWindowPosition(
        this.messengerWindowPosition.x,
        this.messengerWindowPosition.y,
        this.messengerWindowSize
      );
    }
    this.desktopPanelSize = this.clampWindowSize('player', this.desktopPanelSize.width, this.desktopPanelSize.height);
    if (!this.explorerMaximized) {
      this.explorerWindowSize = this.clampWindowSize('explorer', this.explorerWindowSize.width, this.explorerWindowSize.height);
    }
    if (!this.messengerMaximized) {
      this.messengerWindowSize = this.clampWindowSize('messenger', this.messengerWindowSize.width, this.messengerWindowSize.height);
    }
  }

  private applyExplorerMaximizedBounds(): void {
    if (typeof window === 'undefined') return;
    const taskbarHeight = this.isFullscreen ? 0 : 40;
    this.explorerWindowPosition = { x: 0, y: 0 };
    this.explorerWindowSize = {
      width: window.innerWidth,
      height: Math.max(300, window.innerHeight - taskbarHeight)
    };
  }

  private applyMessengerMaximizedBounds(): void {
    if (typeof window === 'undefined') return;
    const taskbarHeight = this.isFullscreen ? 0 : 40;
    this.messengerWindowPosition = { x: 0, y: 0 };
    this.messengerWindowSize = {
      width: window.innerWidth,
      height: Math.max(420, window.innerHeight - taskbarHeight)
    };
  }

  private clampWindowPosition(x: number, y: number, size: { width: number; height: number }): { x: number; y: number } {
    if (typeof window === 'undefined') return { x, y };
    const taskbarHeight = this.isFullscreen ? 0 : 44;
    const maxX = Math.max(8, window.innerWidth - size.width - 8);
    const maxY = Math.max(8, window.innerHeight - size.height - taskbarHeight - 8);
    return {
      x: Math.min(Math.max(8, x), maxX),
      y: Math.min(Math.max(8, y), maxY)
    };
  }

  private getDefaultPlayerWindowSize(): { width: number; height: number } {
    if (typeof window === 'undefined') return { width: 1060, height: 690 };
    return {
      width: Math.min(1060, window.innerWidth - 64),
      height: Math.min(690, window.innerHeight - 70)
    };
  }

  private getDefaultExplorerWindowSize(): { width: number; height: number } {
    if (typeof window === 'undefined') return { width: 560, height: 420 };
    return {
      width: Math.min(560, window.innerWidth - 200),
      height: Math.min(420, window.innerHeight - 180)
    };
  }

  private getDefaultMessengerWindowSize(): { width: number; height: number } {
    if (typeof window === 'undefined') return { width: 760, height: 560 };
    return {
      width: Math.min(760, window.innerWidth - 120),
      height: Math.min(560, window.innerHeight - 120)
    };
  }

  private clampWindowSize(target: 'player' | 'explorer' | 'messenger', width: number, height: number): { width: number; height: number } {
    if (typeof window === 'undefined') return { width, height };
    const taskbarHeight = this.isFullscreen ? 0 : 44;
    const minWidth = target === 'player' ? 760 : target === 'messenger' ? 560 : 420;
    const minHeight = target === 'player' ? 500 : target === 'messenger' ? 420 : 300;
    const maxWidth = Math.max(minWidth, window.innerWidth - 16);
    const maxHeight = Math.max(minHeight, window.innerHeight - taskbarHeight - 16);
    return {
      width: Math.min(Math.max(minWidth, width), maxWidth),
      height: Math.min(Math.max(minHeight, height), maxHeight)
    };
  }

  private handleWindowResize(event: MouseEvent): void {
    if (!this.resizeState) return;
    const state = this.resizeState;
    const deltaX = event.clientX - state.startX;
    const deltaY = event.clientY - state.startY;

    let nextWidth = state.startWidth + (state.edgeX === 'right' ? deltaX : -deltaX);
    let nextHeight = state.startHeight + (state.edgeY === 'bottom' ? deltaY : -deltaY);
    const clampedSize = this.clampWindowSize(state.target, nextWidth, nextHeight);
    nextWidth = clampedSize.width;
    nextHeight = clampedSize.height;

    let nextLeft = state.startLeft;
    let nextTop = state.startTop;
    if (state.edgeX === 'left') {
      nextLeft = state.startLeft + (state.startWidth - nextWidth);
    }
    if (state.edgeY === 'top') {
      nextTop = state.startTop + (state.startHeight - nextHeight);
    }

    const clampedPosition = this.clampWindowPosition(nextLeft, nextTop, { width: nextWidth, height: nextHeight });
    if (state.target === 'player') {
      this.desktopPanelSize = { width: nextWidth, height: nextHeight };
      this.desktopPanelPosition = clampedPosition;
    } else if (state.target === 'explorer') {
      this.explorerWindowSize = { width: nextWidth, height: nextHeight };
      this.explorerWindowPosition = clampedPosition;
    } else {
      this.messengerWindowSize = { width: nextWidth, height: nextHeight };
      this.messengerWindowPosition = clampedPosition;
    }
  }

  private uploadExplorerFiles(files: File[], relativePaths?: string[]): void {
    if (!this.canManageNas) return;
    if (this.explorerPathId == null) return;
    this.explorerLoading = true;
    this.explorerError = '';
    this.nasService.uploadFiles(this.explorerPathId, this.explorerSubPath || undefined, files, relativePaths).subscribe({
      next: (event: any) => {
        if (event.type === HttpEventType.Response) {
          this.loadExplorerItems();
        }
      },
      error: (err) => {
        this.explorerLoading = false;
        this.explorerError = err.error?.error || 'No se pudo subir el contenido.';
      }
    });
  }

  private playSound(url: string, volume = 1): void {
    if (typeof window === 'undefined') return;
    try {
      const audio = new Audio(url);
      audio.volume = Math.max(0, Math.min(1, volume));
      audio.play().catch(() => {});
    } catch {}
  }

  private canPlayWindowsChatSound(): boolean {
    return this.isOpen && this.isDesktopWmp;
  }

  private playMsnMessageReceived(): void {
    if (!this.canPlayWindowsChatSound()) return;
    this.playSound('assets/Windows%20songs/messenger-tono-mensaje-.mp3', 0.8);
  }

  playMsnMessageSent(): void {
    if (!this.canPlayWindowsChatSound()) return;
    this.playSound('assets/Windows%20songs/messenger-tono-mensaje-.mp3', 0.5);
  }

  private playMsnOnline(): void {
    if (!this.canPlayWindowsChatSound()) return;
    this.playSound('assets/Windows%20songs/Voicy_Windows%20XP%20Logon.mp3', 0.7);
  }

  private playMessengerBuzzInspired(): void {
    if (!this.canPlayWindowsChatSound()) return;
    this.playSound('assets/Windows%20songs/Zumbido%20messenger.mp3', 0.9);
  }
}
