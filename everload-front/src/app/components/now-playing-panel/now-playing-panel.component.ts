import { Component, OnInit, OnDestroy, AfterViewChecked, HostListener, ViewChild, ElementRef, ChangeDetectorRef } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { forkJoin, Subscription } from 'rxjs';
import { MusicMetadataDto, MusicService, PlayerState } from '../../services/music.service';
import { NasPath, NasService } from '../../services/nas.service';
import { ChatMessageDto, ChatService } from '../../services/chat.service';
import { AuthService } from '../../services/auth.service';
import { ApiBaseService } from '../../services/api-base.service';

type DesktopWindowTarget = 'player' | 'explorer' | 'manager' | 'messenger' | 'youtube' | 'browser' | 'minesweeper' | 'calculator' | 'notepad' | 'equalizer' | 'snake';
type DesktopIconId = 'explorer' | 'music' | 'manager' | 'player' | 'calculator' | 'notepad' | 'equalizer' | 'snake' | 'xp' | 'messenger' | 'youtube' | 'browser' | 'minesweeper' | 'wallpaper';

interface WindowsYoutubeDownload {
  id: string;
  videoId: string;
  type: 'video' | 'music';
  status: 'downloading' | 'completed' | 'failed' | 'cancelled';
  progress: number;
  filename?: string;
  error?: string;
}

interface MinesweeperCell {
  row: number;
  col: number;
  mine: boolean;
  revealed: boolean;
  flagged: boolean;
  adjacent: number;
}

interface NotepadFile {
  id: string;
  name: string;
  content: string;
  updatedAt: number;
}

interface BrowserFavorite {
  id: string;
  title: string;
  url: string;
}

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
  @ViewChild('snakeCanvas') snakeCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('desktopPanel') desktopPanel?: ElementRef<HTMLElement>;
  private panelRaf?: number;
  private panelPeaks: number[] = [];

  likedItems: any[] = [];
  readonly Math = Math;
  taskbarClock = '';
  desktopSelectedIcons = new Set<DesktopIconId>();
  desktopIconPositions: Record<DesktopIconId, { x: number; y: number }> = {
    explorer: { x: 22, y: 26 },
    music: { x: 22, y: 104 },
    manager: { x: 22, y: 182 },
    player: { x: 22, y: 260 },
    calculator: { x: 22, y: 338 },
    notepad: { x: 22, y: 416 },
    equalizer: { x: 22, y: 494 },
    snake: { x: 22, y: 572 },
    xp: { x: 118, y: 26 },
    messenger: { x: 118, y: 104 },
    youtube: { x: 118, y: 182 },
    browser: { x: 118, y: 260 },
    minesweeper: { x: 118, y: 338 },
    wallpaper: { x: 118, y: 416 },
  };
  desktopSelection: { startX: number; startY: number; currentX: number; currentY: number } | null = null;

  desktopStartOpen = false;
  desktopExplorerOpen = true;
  musicManagerOpen = false;
  messengerOpen = false;
  youtubeDownloaderOpen = false;
  browserOpen = false;
  minesweeperOpen = false;
  wallpaperSettingsOpen = false;
  messengerBuzzing = false;
  playerMinimized = false;
  explorerMinimized = false;
  musicManagerMinimized = false;
  messengerMinimized = false;
  youtubeDownloaderMinimized = false;
  browserMinimized = false;
  minesweeperMinimized = false;
  explorerMaximized = false;
  messengerMaximized = false;
  youtubeDownloaderMaximized = false;
  browserMaximized = false;
  activeDesktopWindow: DesktopWindowTarget = 'player';
  desktopPanelPosition = { x: 0, y: 0 };
  desktopPanelSize = { width: 1060, height: 690 };
  explorerWindowPosition = { x: 132, y: 84 };
  explorerWindowSize = { width: 560, height: 420 };
  musicManagerWindowPosition = { x: 190, y: 96 };
  musicManagerWindowSize = { width: 760, height: 500 };
  musicManagerTab: 'properties' | 'queue' | 'history' = 'properties';
  musicManagerStatus = '';
  historyItems: any[] = [];
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
  youtubeWindowPosition = { x: 292, y: 132 };
  youtubeWindowSize = { width: 620, height: 430 };
  private youtubeRestoreWindow = {
    position: { x: 292, y: 132 },
    size: { width: 620, height: 430 }
  };
  browserWindowPosition = { x: 250, y: 86 };
  browserWindowSize = { width: 820, height: 560 };
  private browserRestoreWindow = {
    position: { x: 250, y: 86 },
    size: { width: 820, height: 560 }
  };
  browserAddress = 'https://example.com';
  browserCurrentUrl = 'https://example.com';
  browserFrameUrl: SafeResourceUrl | null = null;
  browserHistory: string[] = ['https://example.com'];
  browserHistoryIndex = 0;
  browserLoading = false;
  browserFavorites: BrowserFavorite[] = [];
  private readonly browserFavoritesPrefix = 'everload.windows.browserFavorites';
  minesweeperWindowPosition = { x: 360, y: 96 };
  minesweeperWindowSize = { width: 288, height: 386 };

  // ── Calculator ─────────────────────────────────────────────────────────────
  calculatorOpen = false;
  calculatorMinimized = false;
  calculatorMaximized = false;
  calculatorWindowPosition = { x: 430, y: 80 };
  calculatorWindowSize = { width: 272, height: 378 };
  private calculatorRestoreWindow = { position: { x: 430, y: 80 }, size: { width: 272, height: 378 } };
  calcDisplay = '0';
  private calcPendingOp = '';
  private calcPendingVal = 0;
  private calcNewInput = true;

  // ── Notepad ───────────────────────────────────────────────────────────────
  notepadOpen = false;
  notepadMinimized = false;
  notepadMaximized = false;
  notepadWindowPosition = { x: 200, y: 100 };
  notepadWindowSize = { width: 500, height: 360 };
  private notepadRestoreWindow = { position: { x: 200, y: 100 }, size: { width: 500, height: 360 } };
  notepadText = '';
  notepadFiles: NotepadFile[] = [];
  activeNotepadFileId = '';
  notepadFileName = 'notas.txt';
  notepadSaveStatus = '';
  private readonly notepadStorageKey = 'everload.windows.notepad';
  private readonly notepadDiskPrefix = 'everload.windows.disk';
  private notepadOwnerKey = '';
  private notepadSaveTimer?: number;

  // ── Equalizer ─────────────────────────────────────────────────────────────
  equalizerOpen = false;
  equalizerMinimized = false;
  equalizerMaximized = false;
  equalizerWindowPosition = { x: 310, y: 200 };
  equalizerWindowSize = { width: 360, height: 200 };
  private equalizerRestoreWindow = { position: { x: 310, y: 200 }, size: { width: 360, height: 200 } };
  eqLow = 0;
  eqMid = 0;
  eqHigh = 0;

  // ── Winamp 10-band EQ + playlist ─────────────────────────────────────────
  waEqBands: number[] = new Array(10).fill(0);
  readonly waEqBandNames = ['60','170','310','600','1K','3K','6K','12K','14K','16K'];
  waEqOn = true;
  waEqVisible = true;
  waPlVisible = true;
  winampQueue: { tracks: MusicMetadataDto[]; pathId: number; index: number } = { tracks: [], pathId: 0, index: -1 };

  // ── Snake ──────────────────────────────────────────────────────────────────
  snakeOpen = false;
  snakeMinimized = false;
  snakeWindowPosition = { x: 250, y: 60 };
  snakeWindowSize = { width: 340, height: 390 };
  readonly SNAKE_COLS = 20;
  readonly SNAKE_ROWS = 18;
  readonly SNAKE_CELL = 14;
  snakeBody: { x: number; y: number }[] = [];
  snakeFood: { x: number; y: number } = { x: 10, y: 9 };
  snakeDir: 'up' | 'down' | 'left' | 'right' = 'right';
  private snakePendingDir: 'up' | 'down' | 'left' | 'right' = 'right';
  snakeScore = 0;
  snakeStatus: 'ready' | 'playing' | 'dead' = 'ready';
  private snakeRaf?: number;
  private snakeLastTime = 0;
  private readonly snakeSpeed = 150;
  snakeShowLeaderboard = false;
  snakeLeaderboard: { rank: number; username: string; avatarUrl: string; score: number }[] = [];
  snakeLeaderboardLoading = false;

  // ── Screensaver ───────────────────────────────────────────────────────────
  screensaverActive = false;
  dvdX = 120;
  dvdY = 80;
  dvdVX = 1.8;
  dvdVY = 1.4;
  dvdColor = '#ff6040';
  private screensaverRaf?: number;
  private inactivityTimer?: number;
  private readonly screensaverDelay = 90000;

  // ── BSOD ──────────────────────────────────────────────────────────────────
  bsodActive = false;
  private bsodKeySeq = '';
  private readonly bsodCode = 'bsod';

  // ── XP Notifications ──────────────────────────────────────────────────────
  xpNotifs: Array<{ id: number; title: string; body: string }> = [];
  private xpNotifCounter = 0;
  private prevTrackPath = '';

  // ── Context menu (desktop wallpaper) ─────────────────────────────────────
  ctxMenuOpen = false;
  ctxMenuX = 0;
  ctxMenuY = 0;

  // ── Explorer context menu & clipboard ─────────────────────────────────────
  explorerCtxOpen = false;
  explorerCtxX = 0;
  explorerCtxY = 0;
  explorerCtxTarget: MusicMetadataDto | null = null;
  explorerClipboard: { items: MusicMetadataDto[]; pathId: number; mode: 'copy' | 'cut' } | null = null;

  explorerPaths: NasPath[] = [];
  explorerPathId: number | null = null;
  explorerSubPath = '';
  explorerItems: MusicMetadataDto[] = [];
  explorerSelectedPath: string | null = null;
  explorerSelectedPaths = new Set<string>();
  private explorerSelectionAnchorPath: string | null = null;
  explorerLoading = false;
  explorerError = '';
  wallpaperUploadError = '';
  youtubeUrl = '';
  youtubeResolution = '720';
  youtubeDownloadType: 'video' | 'music' = 'music';
  youtubeDownloads: WindowsYoutubeDownload[] = [];
  youtubeError = '';
  private youtubeDownloadSub?: Subscription;
  private get backendUrl(): string {
    return `${this.apiBase.backendUrl || ''}/api`;
  }
  playerSkin: 'wmp' | 'winamp' | 'macos' | 'foobar' = 'wmp';
  readonly skinOptions: Array<{ id: 'wmp' | 'winamp' | 'macos' | 'foobar'; name: string }> = [
    { id: 'wmp',    name: 'Windows Media Player' },
    { id: 'winamp', name: 'Winamp 2.x' },
    { id: 'macos',  name: 'macOS Music' },
    { id: 'foobar', name: 'foobar2000' }
  ];
  private readonly skinStorageKey = 'everload.windows.playerSkin';

  selectedWallpaper = 'xp';
  customWallpaperUrl = '';
  readonly wallpaperOptions = [
    { id: 'xp', name: 'Pradera XP', previewClass: 'xp' },
    { id: 'midnight', name: 'Azul nocturno', previewClass: 'midnight' },
    { id: 'sunset', name: 'Atardecer', previewClass: 'sunset' },
    { id: 'forest', name: 'Bosque', previewClass: 'forest' }
  ];
  minesBoard: MinesweeperCell[][] = [];
  minesStatus: 'ready' | 'playing' | 'won' | 'lost' = 'ready';
  minesRows = 9;
  minesCols = 9;
  minesCount = 10;
  minesFlagsLeft = 10;
  minesElapsed = 0;
  private minesTimer?: number;
  private minesGenerated = false;
  private clockTimer?: number;
  private dragState: { target: DesktopWindowTarget; offsetX: number; offsetY: number } | null = null;
  private resizeState: {
    target: DesktopWindowTarget;
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
  private readonly desktopTaskbarHeight = 40;
  private readonly wallpaperStorageKey = 'everload.windows.wallpaper';
  private readonly customWallpaperStorageKey = 'everload.windows.customWallpaper';
  private desktopIconDragState: {
    ids: DesktopIconId[];
    startX: number;
    startY: number;
    origins: Record<string, { x: number; y: number }>;
    moved: boolean;
  } | null = null;
  private suppressNextDesktopIconClick = false;
  private suppressNextDesktopWallpaperClick = false;

  constructor(
    public musicService: MusicService,
    private nasService: NasService,
    private chatService: ChatService,
    private authService: AuthService,
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private apiBase: ApiBaseService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.subs.push(this.musicService.mainPlayer.state$.subscribe(s => {
      this.state = s;
      if (s.currentTrack && s.currentTrack.path !== this.prevTrackPath) {
        const wasEmpty = !this.prevTrackPath;
        this.prevTrackPath = s.currentTrack.path;
        if (!wasEmpty) {
          this.showXpNotif(
            s.currentTrack.title || s.currentTrack.name || 'Desconocido',
            s.currentTrack.artist || 'Artista desconocido'
          );
        }
      }
    }));
    this.subs.push(this.musicService.shuffle$.subscribe(v => { this.shuffle = v; }));
    this.subs.push(this.musicService.repeat$.subscribe(v => { this.repeat = v; }));
    this.subs.push(this.musicService.queue$.subscribe(q => { this.winampQueue = q; }));
    this.musicService.getFavorites().subscribe({ next: favs => { this.likedItems = favs; } });
    this.loadExplorerPaths();
    this.loadWallpaperPreference();
    this.loadSkinPreference();
    this.loadNotepad();
    this.loadBrowserFavorites();
    this.subs.push(this.authService.currentUser$.subscribe(() => {
      this.loadNotepad();
      this.loadBrowserFavorites();
    }));
    this.resetMinesweeper();
    this.updateTaskbarClock();
    this.resetDesktopWindowPositions();
    this.clockTimer = window.setInterval(() => this.updateTaskbarClock(), 30000);
    this.inactivityTimer = window.setInterval(() => this.checkInactivity(), 10000);
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
    this.stopMinesTimer();
    if (this.clockTimer) window.clearInterval(this.clockTimer);
    if (this.inactivityTimer) window.clearInterval(this.inactivityTimer);
    if (this.snakeRaf) cancelAnimationFrame(this.snakeRaf);
    if (this.screensaverRaf) cancelAnimationFrame(this.screensaverRaf);
    if (this.notepadSaveTimer) window.clearTimeout(this.notepadSaveTimer);
    this.saveNotepad();
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
  get musicManagerWindowStyle(): Record<string, string> {
    if (this.isFullscreen) return {};
    return {
      left: `${this.musicManagerWindowPosition.x}px`,
      top: `${this.musicManagerWindowPosition.y}px`,
      width: `${this.musicManagerWindowSize.width}px`,
      height: `${this.musicManagerWindowSize.height}px`,
      zIndex: this.activeDesktopWindow === 'manager' ? '9518' : '9498'
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
  get youtubeWindowStyle(): Record<string, string> {
    if (this.isFullscreen) return {};
    return {
      left: `${this.youtubeWindowPosition.x}px`,
      top: `${this.youtubeWindowPosition.y}px`,
      width: `${this.youtubeWindowSize.width}px`,
      height: `${this.youtubeWindowSize.height}px`,
      zIndex: this.activeDesktopWindow === 'youtube' ? '9505' : '9495'
    };
  }
  get browserWindowStyle(): Record<string, string> {
    if (this.isFullscreen) return {};
    return {
      left: `${this.browserWindowPosition.x}px`,
      top: `${this.browserWindowPosition.y}px`,
      width: `${this.browserWindowSize.width}px`,
      height: `${this.browserWindowSize.height}px`,
      zIndex: this.activeDesktopWindow === 'browser' ? '9507' : '9494'
    };
  }
  get youtubeVideoId(): string | null {
    return this.extractYoutubeVideoId(this.youtubeUrl);
  }
  get youtubeThumbUrl(): string {
    return this.youtubeVideoId ? `https://img.youtube.com/vi/${this.youtubeVideoId}/hqdefault.jpg` : '';
  }
  get youtubeDownloading(): boolean {
    return !!this.youtubeDownloadSub;
  }
  get minesweeperWindowStyle(): Record<string, string> {
    if (this.isFullscreen) return {};
    return {
      left: `${this.minesweeperWindowPosition.x}px`,
      top: `${this.minesweeperWindowPosition.y}px`,
      width: `${this.minesweeperWindowSize.width}px`,
      height: `${this.minesweeperWindowSize.height}px`,
      zIndex: this.activeDesktopWindow === 'minesweeper' ? '9506' : '9494'
    };
  }
  get minesFace(): string {
    if (this.minesStatus === 'won') return '😎';
    if (this.minesStatus === 'lost') return '😵';
    return '🙂';
  }
  get canManageNas(): boolean {
    return this.authService.canManageNas();
  }
  get currentSkinName(): string {
    return this.skinOptions.find(s => s.id === this.playerSkin)?.name ?? '';
  }
  get notepadDiskLabel(): string {
    return `${this.getNotepadUserName()} (C:)`;
  }
  get activeNotepadFile(): NotepadFile | undefined {
    return this.notepadFiles.find(file => file.id === this.activeNotepadFileId);
  }
  get desktopWallpaperClass(): string {
    return `np-wallpaper-${this.selectedWallpaper}`;
  }
  get desktopWallpaperStyle(): Record<string, string> {
    if (this.selectedWallpaper !== 'custom' || !this.customWallpaperUrl) return {};
    return {
      backgroundImage: `linear-gradient(180deg, rgba(20, 45, 90, 0.16), rgba(20, 45, 90, 0.08)), url("${this.customWallpaperUrl}")`
    };
  }

  close(): void {
    this.playSound('assets/Windows%20songs/Voicy_Windows%20XP%20Shutdown.mp3', 0.7);
    this.musicService.nowPlayingPanelOpen = false;
    this.desktopStartOpen = false;
    this.playerMinimized = false;
    this.explorerMinimized = false;
    this.musicManagerMinimized = false;
    this.messengerMinimized = false;
    this.youtubeDownloaderMinimized = false;
    this.browserMinimized = false;
    this.stopViz();
  }

  onDesktopBackdropClick(): void {
    this.desktopStartOpen = false;
    this.wallpaperSettingsOpen = false;
    this.desktopSelectedIcons.clear();
    this.desktopSelection = null;
  }

  onDesktopWallpaperClick(event: MouseEvent): void {
    event.stopPropagation();
    if (this.suppressNextDesktopWallpaperClick) {
      this.suppressNextDesktopWallpaperClick = false;
      return;
    }
    this.onDesktopBackdropClick();
    this.closeCtxMenu();
  }

  desktopIconStyle(id: DesktopIconId): Record<string, string> {
    const pos = this.desktopIconPositions[id];
    return {
      left: `${pos.x}px`,
      top: `${pos.y}px`
    };
  }

  isDesktopIconSelected(id: DesktopIconId): boolean {
    return this.desktopSelectedIcons.has(id);
  }

  desktopSelectionStyle(): Record<string, string> {
    if (!this.desktopSelection) return {};
    const left = Math.min(this.desktopSelection.startX, this.desktopSelection.currentX);
    const top = Math.min(this.desktopSelection.startY, this.desktopSelection.currentY);
    return {
      left: `${left}px`,
      top: `${top}px`,
      width: `${Math.abs(this.desktopSelection.currentX - this.desktopSelection.startX)}px`,
      height: `${Math.abs(this.desktopSelection.currentY - this.desktopSelection.startY)}px`
    };
  }

  beginDesktopSelection(event: MouseEvent): void {
    if (event.button !== 0) return;
    const target = event.target as HTMLElement;
    if (target.closest('.np-desktop-icon')) return;
    this.desktopStartOpen = false;
    this.wallpaperSettingsOpen = false;
    this.desktopSelectedIcons.clear();
    this.desktopSelection = {
      startX: event.clientX,
      startY: event.clientY,
      currentX: event.clientX,
      currentY: event.clientY
    };
  }

  beginDesktopIconPointer(event: MouseEvent): void {
    if (event.button !== 0) return;
    const button = (event.target as HTMLElement).closest('.np-desktop-icon') as HTMLElement | null;
    const iconId = button?.dataset['icon'] as DesktopIconId | undefined;
    if (!iconId || !this.desktopIconPositions[iconId]) return;
    event.stopPropagation();
    this.desktopSelection = null;
    if (event.ctrlKey || event.metaKey) {
      if (this.desktopSelectedIcons.has(iconId)) this.desktopSelectedIcons.delete(iconId);
      else this.desktopSelectedIcons.add(iconId);
    } else if (!this.desktopSelectedIcons.has(iconId)) {
      this.desktopSelectedIcons = new Set([iconId]);
    }
    const ids = this.desktopSelectedIcons.has(iconId) ? [...this.desktopSelectedIcons] : [iconId];
    const origins = ids.reduce<Record<string, { x: number; y: number }>>((acc, id) => {
      acc[id] = { ...this.desktopIconPositions[id] };
      return acc;
    }, {});
    this.desktopIconDragState = {
      ids,
      startX: event.clientX,
      startY: event.clientY,
      origins,
      moved: false
    };
  }

  runDesktopIcon(id: DesktopIconId, event: MouseEvent): void {
    event.stopPropagation();
    if (this.suppressNextDesktopIconClick) {
      this.suppressNextDesktopIconClick = false;
      return;
    }
    this.desktopSelectedIcons = new Set([id]);
    switch (id) {
      case 'explorer': this.openExplorerWindow(); break;
      case 'music': this.openCurrentTrackFolder(); break;
      case 'manager': this.openMusicManager(); break;
      case 'player':
        this.desktopStartOpen = false;
        this.playerMinimized = false;
        this.focusWindow('player');
        break;
      case 'calculator': this.openCalculator(); break;
      case 'notepad': this.openNotepad(); break;
      case 'equalizer': this.openEqualizer(); break;
      case 'snake': this.openSnake(); break;
      case 'xp': void this.toggleFullscreen(); break;
      case 'messenger': this.openMessengerWindow(); break;
      case 'youtube': this.openYoutubeDownloader(); break;
      case 'browser': this.openBrowser(); break;
      case 'minesweeper': this.openMinesweeper(); break;
      case 'wallpaper': this.openWallpaperSettings(); break;
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void { if (this.isOpen) this.close(); }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.clampAllWindows();
  }

  @HostListener('document:mousemove', ['$event'])
  onDocumentMouseMove(event: MouseEvent): void {
    this.resetInactivityTimestamp();
    if (this.isFullscreen) return;
    if (this.desktopIconDragState) {
      const dx = event.clientX - this.desktopIconDragState.startX;
      const dy = event.clientY - this.desktopIconDragState.startY;
      if (Math.abs(dx) > 3 || Math.abs(dy) > 3) this.desktopIconDragState.moved = true;
      for (const id of this.desktopIconDragState.ids) {
        const origin = this.desktopIconDragState.origins[id];
        if (origin) {
          this.desktopIconPositions[id] = this.clampDesktopIconPosition(origin.x + dx, origin.y + dy);
        }
      }
      return;
    }
    if (this.desktopSelection) {
      this.desktopSelection.currentX = event.clientX;
      this.desktopSelection.currentY = event.clientY;
      return;
    }
    if (this.resizeState) { this.handleWindowResize(event); return; }
    if (this.dragState) {
      const nextX = event.clientX - this.dragState.offsetX;
      const nextY = event.clientY - this.dragState.offsetY;
      const size = this.getWindowSize(this.dragState.target);
      this.setWindowPosition(this.dragState.target, this.clampWindowPosition(nextX, nextY, size));
    }
  }

  @HostListener('document:mouseup')
  onDocumentMouseUp(): void {
    if (this.desktopIconDragState) {
      if (this.desktopIconDragState.moved) {
        this.suppressNextDesktopIconClick = true;
        window.setTimeout(() => { this.suppressNextDesktopIconClick = false; }, 0);
      }
      this.desktopIconDragState = null;
    }
    if (this.desktopSelection) {
      const moved = Math.abs(this.desktopSelection.currentX - this.desktopSelection.startX) > 3 ||
        Math.abs(this.desktopSelection.currentY - this.desktopSelection.startY) > 3;
      this.selectDesktopIconsInRect();
      if (moved) {
        this.suppressNextDesktopWallpaperClick = true;
        window.setTimeout(() => { this.suppressNextDesktopWallpaperClick = false; }, 0);
      }
      this.desktopSelection = null;
    }
    this.dragState = null;
    this.resizeState = null;
  }

  private clampDesktopIconPosition(x: number, y: number): { x: number; y: number } {
    const iconWidth = 92;
    const iconHeight = 74;
    const maxX = Math.max(0, window.innerWidth - iconWidth - 8);
    const maxY = Math.max(0, window.innerHeight - this.desktopTaskbarHeight - iconHeight - 8);
    return {
      x: Math.max(0, Math.min(maxX, x)),
      y: Math.max(0, Math.min(maxY, y))
    };
  }

  private selectDesktopIconsInRect(): void {
    if (!this.desktopSelection) return;
    const rect = {
      left: Math.min(this.desktopSelection.startX, this.desktopSelection.currentX),
      top: Math.min(this.desktopSelection.startY, this.desktopSelection.currentY),
      right: Math.max(this.desktopSelection.startX, this.desktopSelection.currentX),
      bottom: Math.max(this.desktopSelection.startY, this.desktopSelection.currentY)
    };
    const selected = Object.entries(this.desktopIconPositions)
      .filter(([, pos]) => {
        const iconRect = {
          left: pos.x,
          top: pos.y,
          right: pos.x + 92,
          bottom: pos.y + 74
        };
        return iconRect.left < rect.right &&
          iconRect.right > rect.left &&
          iconRect.top < rect.bottom &&
          iconRect.bottom > rect.top;
      })
      .map(([id]) => id as DesktopIconId);
    this.desktopSelectedIcons = new Set(selected);
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

  get currentTrackSizeLabel(): string {
    return this.formatBytes(this.state?.currentTrack?.size || 0);
  }

  get queueDurationLabel(): string {
    const seconds = this.winampQueue.tracks.reduce((total, track) => total + (track.duration || 0), 0);
    return this.fmt(seconds);
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

  openMusicManager(tab: 'properties' | 'queue' | 'history' = this.musicManagerTab): void {
    this.musicManagerOpen = true;
    this.musicManagerMinimized = false;
    this.musicManagerTab = tab;
    this.activeDesktopWindow = 'manager';
    this.desktopStartOpen = false;
    if (tab === 'history') this.loadMusicHistory();
  }

  closeMusicManager(): void {
    this.musicManagerOpen = false;
    this.musicManagerMinimized = false;
    if (this.activeDesktopWindow === 'manager') this.activeDesktopWindow = 'player';
  }

  loadMusicHistory(): void {
    this.musicService.getHistory(60).subscribe({
      next: items => { this.historyItems = items || []; },
      error: () => { this.historyItems = []; }
    });
  }

  playQueueTrack(index: number): void {
    if (!this.winampQueue.tracks[index]) return;
    this.musicService.setQueue(this.winampQueue.pathId, this.winampQueue.tracks, index);
  }

  moveQueueTrack(index: number, direction: -1 | 1): void {
    const nextIndex = index + direction;
    const tracks = [...this.winampQueue.tracks];
    if (!tracks[index] || !tracks[nextIndex]) return;
    [tracks[index], tracks[nextIndex]] = [tracks[nextIndex], tracks[index]];
    const currentPath = this.state?.currentTrack?.path;
    const activeIndex = currentPath ? tracks.findIndex(track => track.path === currentPath) : this.winampQueue.index;
    this.musicService.updateQueue(this.winampQueue.pathId, tracks, activeIndex);
  }

  removeQueueTrack(index: number): void {
    const tracks = this.winampQueue.tracks.filter((_, i) => i !== index);
    const currentPath = this.state?.currentTrack?.path;
    const activeIndex = currentPath ? tracks.findIndex(track => track.path === currentPath) : Math.min(index, tracks.length - 1);
    this.musicService.updateQueue(this.winampQueue.pathId, tracks, activeIndex);
  }

  clearQueue(): void {
    this.musicService.updateQueue(this.winampQueue.pathId, [], -1);
  }

  playHistoryItem(item: any): void {
    const pathId = Number(item.nasPathId ?? item.pathId ?? this.state?.pathId ?? 0);
    const trackPath = item.trackPath || item.path;
    if (!pathId || !trackPath) return;
    const track: MusicMetadataDto = {
      name: item.title || item.name || trackPath.split(/[\\/]/).pop() || trackPath,
      path: trackPath,
      directory: false,
      size: item.size || 0,
      lastModified: item.playedAt || item.lastModified || '',
      title: item.title || item.name || '',
      artist: item.artist || '',
      album: item.album || '',
      duration: item.durationSeconds || item.duration || 0,
      format: item.format || '',
      bpm: item.bpm || 0,
      hasCover: !!item.hasCover,
      source: 'nas',
      nasPathId: pathId,
    };
    this.musicService.setQueue(pathId, [track], 0);
  }

  downloadCurrentMetadata(format: 'json' | 'csv' = 'json'): void {
    if (!this.state?.currentTrack) return;
    this.downloadMetadata([this.state.currentTrack], `everload-current-track.${format}`, format);
  }

  downloadQueueMetadata(format: 'json' | 'csv' = 'json'): void {
    this.downloadMetadata(this.winampQueue.tracks, `everload-queue-metadata.${format}`, format);
  }

  downloadHistoryMetadata(format: 'json' | 'csv' = 'json'): void {
    const tracks = this.historyItems.map(item => ({
      title: item.title || item.name || '',
      artist: item.artist || '',
      album: item.album || '',
      path: item.trackPath || item.path || '',
      duration: item.durationSeconds || item.duration || 0,
      playedAt: item.playedAt || item.createdAt || '',
      nasPathId: item.nasPathId ?? item.pathId ?? '',
    }));
    this.downloadMetadata(tracks, `everload-history-metadata.${format}`, format);
  }

  refreshCoverForCurrentTrack(): void {
    if (!this.state?.currentTrack) return;
    this.musicService.fetchCoverIfNeeded(this.state.currentTrack);
    this.musicManagerStatus = 'WINDOWS.MANAGER_STATUS_METADATA_REQUESTED';
  }

  private downloadMetadata(items: any[], filename: string, format: 'json' | 'csv'): void {
    if (!items.length || typeof document === 'undefined') return;
    const payload = format === 'json'
      ? JSON.stringify(items, null, 2)
      : this.toCsv(items);
    const blob = new Blob([payload], { type: format === 'json' ? 'application/json;charset=utf-8' : 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
    this.musicManagerStatus = 'WINDOWS.MANAGER_STATUS_METADATA_DOWNLOADED';
  }

  private toCsv(items: any[]): string {
    const keys = Array.from(items.reduce((set: Set<string>, item: any) => {
      Object.keys(item || {}).forEach(key => set.add(key));
      return set;
    }, new Set<string>()));
    const escape = (value: any) => `"${String(value ?? '').replace(/"/g, '""')}"`;
    return [keys.join(','), ...items.map(item => keys.map(key => escape(item[key])).join(','))].join('\n');
  }

  private formatBytes(bytes: number): string {
    if (!bytes || bytes <= 0) return '-';
    const units = ['B', 'KB', 'MB', 'GB'];
    let value = bytes;
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
      value /= 1024;
      unit += 1;
    }
    return `${value.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
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

  focusWindow(target: DesktopWindowTarget): void {
    this.activeDesktopWindow = target;
    this.desktopStartOpen = false;
  }

  private getWindowPosition(target: DesktopWindowTarget): { x: number; y: number } {
    switch (target) {
      case 'player':     return this.desktopPanelPosition;
      case 'explorer':   return this.explorerWindowPosition;
      case 'manager':    return this.musicManagerWindowPosition;
      case 'messenger':  return this.messengerWindowPosition;
      case 'browser':    return this.browserWindowPosition;
      case 'minesweeper':return this.minesweeperWindowPosition;
      case 'calculator': return this.calculatorWindowPosition;
      case 'notepad':    return this.notepadWindowPosition;
      case 'equalizer':  return this.equalizerWindowPosition;
      case 'snake':      return this.snakeWindowPosition;
      default:           return this.youtubeWindowPosition;
    }
  }

  private getWindowSize(target: DesktopWindowTarget): { width: number; height: number } {
    switch (target) {
      case 'player':     return this.desktopPanelSize;
      case 'explorer':   return this.explorerWindowSize;
      case 'manager':    return this.musicManagerWindowSize;
      case 'messenger':  return this.messengerWindowSize;
      case 'browser':    return this.browserWindowSize;
      case 'minesweeper':return this.minesweeperWindowSize;
      case 'calculator': return this.calculatorWindowSize;
      case 'notepad':    return this.notepadWindowSize;
      case 'equalizer':  return this.equalizerWindowSize;
      case 'snake':      return this.snakeWindowSize;
      default:           return this.youtubeWindowSize;
    }
  }

  private setWindowPosition(target: DesktopWindowTarget, pos: { x: number; y: number }): void {
    switch (target) {
      case 'player':     this.desktopPanelPosition = pos; break;
      case 'explorer':   this.explorerWindowPosition = pos; break;
      case 'manager':    this.musicManagerWindowPosition = pos; break;
      case 'messenger':  this.messengerWindowPosition = pos; break;
      case 'browser':    this.browserWindowPosition = pos; break;
      case 'minesweeper':this.minesweeperWindowPosition = pos; break;
      case 'calculator': this.calculatorWindowPosition = pos; break;
      case 'notepad':    this.notepadWindowPosition = pos; break;
      case 'equalizer':  this.equalizerWindowPosition = pos; break;
      case 'snake':      this.snakeWindowPosition = pos; break;
      default:           this.youtubeWindowPosition = pos;
    }
  }

  private setWindowSize(target: DesktopWindowTarget, size: { width: number; height: number }): void {
    switch (target) {
      case 'player':     this.desktopPanelSize = size; break;
      case 'explorer':   this.explorerWindowSize = size; break;
      case 'manager':    this.musicManagerWindowSize = size; break;
      case 'messenger':  this.messengerWindowSize = size; break;
      case 'browser':    this.browserWindowSize = size; break;
      case 'minesweeper':this.minesweeperWindowSize = size; break;
      case 'calculator': this.calculatorWindowSize = size; break;
      case 'notepad':    this.notepadWindowSize = size; break;
      case 'equalizer':  this.equalizerWindowSize = size; break;
      case 'snake':      this.snakeWindowSize = size; break;
      default:           this.youtubeWindowSize = size;
    }
  }

  beginWindowDrag(event: MouseEvent, target: DesktopWindowTarget): void {
    if (this.isFullscreen) return;
    if (target === 'explorer' && this.explorerMaximized) return;
    if (target === 'messenger' && this.messengerMaximized) return;
    if (target === 'youtube' && this.youtubeDownloaderMaximized) return;
    if (target === 'browser' && this.browserMaximized) return;
    if (target === 'calculator' && this.calculatorMaximized) return;
    if (target === 'notepad' && this.notepadMaximized) return;
    if (target === 'equalizer' && this.equalizerMaximized) return;
    this.focusWindow(target);
    const position = this.getWindowPosition(target);
    this.dragState = { target, offsetX: event.clientX - position.x, offsetY: event.clientY - position.y };
    event.preventDefault();
  }

  beginWindowResize(event: MouseEvent, target: DesktopWindowTarget, edgeX: 'left' | 'right', edgeY: 'top' | 'bottom'): void {
    if (this.isFullscreen) return;
    if (target === 'explorer' && this.explorerMaximized) return;
    if (target === 'messenger' && this.messengerMaximized) return;
    if (target === 'youtube' && this.youtubeDownloaderMaximized) return;
    if (target === 'browser' && this.browserMaximized) return;
    if (target === 'calculator' && this.calculatorMaximized) return;
    if (target === 'notepad' && this.notepadMaximized) return;
    if (target === 'equalizer' && this.equalizerMaximized) return;
    this.focusWindow(target);
    const position = this.getWindowPosition(target);
    const size = this.getWindowSize(target);
    this.resizeState = {
      target, startX: event.clientX, startY: event.clientY,
      startWidth: size.width, startHeight: size.height,
      startLeft: position.x, startTop: position.y, edgeX, edgeY
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
      else if (this.musicManagerOpen && !this.musicManagerMinimized) this.activeDesktopWindow = 'manager';
      else if (this.messengerOpen && !this.messengerMinimized) this.activeDesktopWindow = 'messenger';
      else if (this.youtubeDownloaderOpen && !this.youtubeDownloaderMinimized) this.activeDesktopWindow = 'youtube';
      else if (this.browserOpen && !this.browserMinimized) this.activeDesktopWindow = 'browser';
      else if (this.minesweeperOpen && !this.minesweeperMinimized) this.activeDesktopWindow = 'minesweeper';
    }
  }

  openYoutubeDownloader(): void {
    this.youtubeDownloaderOpen = true;
    this.youtubeDownloaderMinimized = false;
    this.activeDesktopWindow = 'youtube';
    this.desktopStartOpen = false;
  }

  closeYoutubeDownloader(): void {
    this.youtubeDownloaderOpen = false;
    this.youtubeDownloaderMinimized = false;
    this.youtubeDownloaderMaximized = false;
    if (this.activeDesktopWindow === 'youtube') {
      this.activeDesktopWindow = this.messengerOpen ? 'messenger' : this.desktopExplorerOpen ? 'explorer' : 'player';
    }
  }

  openBrowser(): void {
    this.browserOpen = true;
    this.browserMinimized = false;
    this.activeDesktopWindow = 'browser';
    this.desktopStartOpen = false;
    if (!this.browserFrameUrl) this.navigateBrowser(this.browserCurrentUrl, false);
  }

  closeBrowser(): void {
    this.browserOpen = false;
    this.browserMinimized = false;
    this.browserMaximized = false;
    if (this.activeDesktopWindow === 'browser') {
      this.activeDesktopWindow = this.youtubeDownloaderOpen ? 'youtube' : this.messengerOpen ? 'messenger' : this.desktopExplorerOpen ? 'explorer' : 'player';
    }
  }

  toggleBrowserMaximize(): void {
    if (typeof window === 'undefined') return;
    this.focusWindow('browser');
    if (this.browserMaximized) {
      this.browserWindowSize = this.clampWindowSize('browser', this.browserRestoreWindow.size.width, this.browserRestoreWindow.size.height);
      this.browserWindowPosition = this.clampWindowPosition(this.browserRestoreWindow.position.x, this.browserRestoreWindow.position.y, this.browserWindowSize);
      this.browserMaximized = false;
      return;
    }
    this.browserRestoreWindow = {
      position: { ...this.browserWindowPosition },
      size: { ...this.browserWindowSize }
    };
    this.browserMaximized = true;
    this.applyBrowserMaximizedBounds();
  }

  openMinesweeper(): void {
    this.minesweeperOpen = true;
    this.minesweeperMinimized = false;
    this.activeDesktopWindow = 'minesweeper';
    this.desktopStartOpen = false;
    if (!this.minesBoard.length) this.resetMinesweeper();
  }

  closeMinesweeper(): void {
    this.minesweeperOpen = false;
    this.minesweeperMinimized = false;
    if (this.activeDesktopWindow === 'minesweeper') {
      this.activeDesktopWindow = this.youtubeDownloaderOpen ? 'youtube' : this.messengerOpen ? 'messenger' : this.desktopExplorerOpen ? 'explorer' : 'player';
    }
  }

  toggleYoutubeMaximize(): void {
    if (typeof window === 'undefined') return;
    this.focusWindow('youtube');
    if (this.youtubeDownloaderMaximized) {
      this.youtubeWindowSize = this.clampWindowSize(
        'youtube',
        this.youtubeRestoreWindow.size.width,
        this.youtubeRestoreWindow.size.height
      );
      this.youtubeWindowPosition = this.clampWindowPosition(
        this.youtubeRestoreWindow.position.x,
        this.youtubeRestoreWindow.position.y,
        this.youtubeWindowSize
      );
      this.youtubeDownloaderMaximized = false;
      return;
    }

    this.youtubeRestoreWindow = {
      position: { ...this.youtubeWindowPosition },
      size: { ...this.youtubeWindowSize }
    };
    this.youtubeDownloaderMaximized = true;
    this.applyYoutubeMaximizedBounds();
  }

  openWallpaperSettings(): void {
    this.wallpaperSettingsOpen = true;
    this.desktopStartOpen = false;
  }

  closeWallpaperSettings(): void {
    this.wallpaperSettingsOpen = false;
    this.wallpaperUploadError = '';
  }

  selectWallpaper(id: string): void {
    if (id === 'custom' && !this.customWallpaperUrl) return;
    this.selectedWallpaper = id;
    this.wallpaperUploadError = '';
    this.saveWallpaperPreference();
  }

  onWallpaperUpload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      this.wallpaperUploadError = 'El archivo tiene que ser una imagen.';
      return;
    }
    if (file.size > 3 * 1024 * 1024) {
      this.wallpaperUploadError = 'La imagen es demasiado grande. Usa una de menos de 3 MB.';
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.customWallpaperUrl = typeof reader.result === 'string' ? reader.result : '';
      if (!this.customWallpaperUrl) {
        this.wallpaperUploadError = 'No se pudo cargar la imagen.';
        return;
      }
      this.selectedWallpaper = 'custom';
      this.wallpaperUploadError = '';
      this.saveWallpaperPreference();
    };
    reader.onerror = () => {
      this.wallpaperUploadError = 'No se pudo cargar la imagen.';
    };
    reader.readAsDataURL(file);
  }

  resetWallpaper(): void {
    this.selectedWallpaper = 'xp';
    this.customWallpaperUrl = '';
    this.wallpaperUploadError = '';
    this.saveWallpaperPreference();
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

  minimizeWindow(target: DesktopWindowTarget): void {
    if (target === 'player')     this.playerMinimized = true;
    if (target === 'explorer')   this.explorerMinimized = true;
    if (target === 'manager')    this.musicManagerMinimized = true;
    if (target === 'messenger')  this.messengerMinimized = true;
    if (target === 'youtube')    this.youtubeDownloaderMinimized = true;
    if (target === 'browser')    this.browserMinimized = true;
    if (target === 'minesweeper')this.minesweeperMinimized = true;
    if (target === 'calculator') this.calculatorMinimized = true;
    if (target === 'notepad')    this.notepadMinimized = true;
    if (target === 'equalizer')  this.equalizerMinimized = true;
    if (target === 'snake')      this.snakeMinimized = true;
    if (this.activeDesktopWindow === target) {
      if (target !== 'player' && !this.playerMinimized) this.activeDesktopWindow = 'player';
      else if (target !== 'explorer' && this.desktopExplorerOpen && !this.explorerMinimized) this.activeDesktopWindow = 'explorer';
      else if (target !== 'manager' && this.musicManagerOpen && !this.musicManagerMinimized) this.activeDesktopWindow = 'manager';
      else if (target !== 'messenger' && this.messengerOpen && !this.messengerMinimized) this.activeDesktopWindow = 'messenger';
      else if (target !== 'youtube' && this.youtubeDownloaderOpen && !this.youtubeDownloaderMinimized) this.activeDesktopWindow = 'youtube';
      else if (target !== 'browser' && this.browserOpen && !this.browserMinimized) this.activeDesktopWindow = 'browser';
      else if (target !== 'minesweeper' && this.minesweeperOpen && !this.minesweeperMinimized) this.activeDesktopWindow = 'minesweeper';
    }
  }

  toggleTaskWindow(target: DesktopWindowTarget): void {
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
    if (target === 'manager') {
      if (!this.musicManagerOpen) {
        this.openMusicManager();
        return;
      }
      this.musicManagerMinimized = !this.musicManagerMinimized;
      if (!this.musicManagerMinimized) this.focusWindow('manager');
      return;
    }
    if (target === 'messenger') {
      if (!this.messengerOpen) {
        this.openMessengerWindow();
        return;
      }
      this.messengerMinimized = !this.messengerMinimized;
      if (!this.messengerMinimized) this.focusWindow('messenger');
      return;
    }
    if (target === 'minesweeper') {
      if (!this.minesweeperOpen) {
        this.openMinesweeper();
        return;
      }
      this.minesweeperMinimized = !this.minesweeperMinimized;
      if (!this.minesweeperMinimized) this.focusWindow('minesweeper');
      return;
    }
    if (target === 'browser') {
      if (!this.browserOpen) {
        this.openBrowser();
        return;
      }
      this.browserMinimized = !this.browserMinimized;
      if (!this.browserMinimized) this.focusWindow('browser');
      return;
    }
    if (target === 'calculator') {
      if (!this.calculatorOpen) { this.openCalculator(); return; }
      this.calculatorMinimized = !this.calculatorMinimized;
      if (!this.calculatorMinimized) this.focusWindow('calculator');
      return;
    }
    if (target === 'notepad') {
      if (!this.notepadOpen) { this.openNotepad(); return; }
      this.notepadMinimized = !this.notepadMinimized;
      if (!this.notepadMinimized) this.focusWindow('notepad');
      return;
    }
    if (target === 'equalizer') {
      if (!this.equalizerOpen) { this.openEqualizer(); return; }
      this.equalizerMinimized = !this.equalizerMinimized;
      if (!this.equalizerMinimized) this.focusWindow('equalizer');
      return;
    }
    if (target === 'snake') {
      if (!this.snakeOpen) { this.openSnake(); return; }
      this.snakeMinimized = !this.snakeMinimized;
      if (!this.snakeMinimized) this.focusWindow('snake');
      return;
    }
    if (!this.youtubeDownloaderOpen) {
      this.openYoutubeDownloader();
      return;
    }
    this.youtubeDownloaderMinimized = !this.youtubeDownloaderMinimized;
    if (!this.youtubeDownloaderMinimized) this.focusWindow('youtube');
  }

  startYoutubeDownload(): void {
    this.youtubeError = '';
    const videoId = this.extractYoutubeVideoId(this.youtubeUrl);
    if (!videoId) {
      this.youtubeError = 'Pega un enlace válido de YouTube.';
      return;
    }

    const item: WindowsYoutubeDownload = {
      id: Math.random().toString(36).slice(2),
      videoId,
      type: this.youtubeDownloadType,
      status: 'downloading',
      progress: 0
    };
    this.youtubeDownloads.unshift(item);

    const endpoint = item.type === 'video' ? 'downloadVideo' : 'downloadMusic';
    const params: Record<string, string> = item.type === 'video'
      ? { videoId, resolution: this.youtubeResolution }
      : { videoId, format: 'mp3' };

    this.youtubeDownloadSub = this.http.get(`${this.backendUrl}/${endpoint}`, {
      params,
      responseType: 'blob',
      observe: 'events',
      reportProgress: true
    }).subscribe({
      next: (event: HttpEvent<Blob>) => {
        if (event.type === HttpEventType.DownloadProgress) {
          item.progress = event.total
            ? Math.round((event.loaded / event.total) * 100)
            : Math.min(item.progress + 6, 92);
        } else if (event.type === HttpEventType.Response) {
          const contentDisposition = event.headers.get('content-disposition');
          const filename = this.filenameFromDisposition(contentDisposition) || `${videoId}.${item.type === 'video' ? 'webm' : 'mp3'}`;
          item.filename = filename;
          item.status = 'completed';
          item.progress = 100;
          this.triggerBlobDownload(event.body!, filename);
          this.youtubeDownloadSub = undefined;
        }
      },
      error: () => {
        item.status = 'failed';
        item.error = 'No se pudo descargar.';
        this.youtubeDownloadSub = undefined;
      }
    });
  }

  cancelYoutubeDownload(): void {
    this.youtubeDownloadSub?.unsubscribe();
    this.youtubeDownloadSub = undefined;
    const active = this.youtubeDownloads.find(item => item.status === 'downloading');
    if (active) {
      active.status = 'cancelled';
      active.progress = 0;
    }
  }

  submitBrowserAddress(): void {
    this.navigateBrowser(this.browserAddress, true);
  }

  browserHome(): void {
    this.navigateBrowser('https://example.com', true);
  }

  browserBack(): void {
    if (this.browserHistoryIndex <= 0) return;
    this.browserHistoryIndex--;
    this.loadBrowserUrl(this.browserHistory[this.browserHistoryIndex]);
  }

  browserForward(): void {
    if (this.browserHistoryIndex >= this.browserHistory.length - 1) return;
    this.browserHistoryIndex++;
    this.loadBrowserUrl(this.browserHistory[this.browserHistoryIndex]);
  }

  browserRefresh(): void {
    this.loadBrowserUrl(this.browserCurrentUrl);
  }

  openBrowserExternal(): void {
    if (!this.browserCurrentUrl) return;
    window.open(this.browserCurrentUrl, '_blank', 'noopener,noreferrer');
  }

  addBrowserFavorite(): void {
    const url = this.normalizeBrowserUrl(this.browserCurrentUrl || this.browserAddress);
    if (this.isBrowserFavorite(url)) return;
    this.browserFavorites = [
      ...this.browserFavorites,
      {
        id: `fav-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        title: this.getBrowserFavoriteTitle(url),
        url
      }
    ];
    this.saveBrowserFavorites();
  }

  removeBrowserFavorite(event: MouseEvent, favorite: BrowserFavorite): void {
    event.stopPropagation();
    this.browserFavorites = this.browserFavorites.filter(item => item.id !== favorite.id);
    this.saveBrowserFavorites();
  }

  isBrowserFavorite(url = this.browserCurrentUrl): boolean {
    const normalized = this.normalizeBrowserUrl(url);
    return this.browserFavorites.some(item => this.normalizeBrowserUrl(item.url) === normalized);
  }

  navigateBrowser(rawUrl: string, pushHistory = true): void {
    const url = this.normalizeBrowserUrl(rawUrl);
    this.loadBrowserUrl(url);
    if (pushHistory) {
      this.browserHistory = this.browserHistory.slice(0, this.browserHistoryIndex + 1);
      if (this.browserHistory[this.browserHistory.length - 1] !== url) {
        this.browserHistory.push(url);
      }
      this.browserHistoryIndex = this.browserHistory.length - 1;
    }
  }

  onBrowserFrameLoad(): void {
    this.browserLoading = false;
  }

  private loadBrowserUrl(url: string): void {
    this.browserCurrentUrl = url;
    this.browserAddress = url;
    this.browserLoading = true;
    this.browserFrameUrl = this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }

  private normalizeBrowserUrl(rawUrl: string): string {
    const trimmed = rawUrl.trim();
    if (!trimmed) return 'https://example.com';
    if (/^https?:\/\//i.test(trimmed)) return trimmed;
    if (trimmed.includes('.') || trimmed.includes('localhost')) return `https://${trimmed}`;
    return `https://www.google.com/search?q=${encodeURIComponent(trimmed)}`;
  }

  private loadBrowserFavorites(): void {
    if (typeof localStorage === 'undefined') return;
    const raw = localStorage.getItem(this.getBrowserFavoritesKey());
    try {
      const stored = raw ? JSON.parse(raw) as BrowserFavorite[] : null;
      this.browserFavorites = Array.isArray(stored) && stored.length
        ? stored.filter(item => item?.url).map(item => ({
            id: item.id || `fav-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            title: item.title || this.getBrowserFavoriteTitle(item.url),
            url: this.normalizeBrowserUrl(item.url)
          }))
        : this.getDefaultBrowserFavorites();
    } catch {
      this.browserFavorites = this.getDefaultBrowserFavorites();
    }
    this.saveBrowserFavorites();
  }

  private saveBrowserFavorites(): void {
    if (typeof localStorage === 'undefined') return;
    localStorage.setItem(this.getBrowserFavoritesKey(), JSON.stringify(this.browserFavorites));
  }

  private getBrowserFavoritesKey(): string {
    return `${this.browserFavoritesPrefix}.${this.getNotepadOwnerKey()}`;
  }

  private getDefaultBrowserFavorites(): BrowserFavorite[] {
    return [
      { id: 'fav-everload', title: 'Everload', url: window.location.origin },
      { id: 'fav-wikipedia', title: 'Wikipedia', url: 'https://www.wikipedia.org' },
      { id: 'fav-archive', title: 'Internet Archive', url: 'https://archive.org' }
    ];
  }

  private getBrowserFavoriteTitle(url: string): string {
    try {
      const parsed = new URL(this.normalizeBrowserUrl(url));
      if (parsed.hostname === window.location.hostname) return 'Everload';
      return parsed.hostname.replace(/^www\./, '');
    } catch {
      return url || 'Favorito';
    }
  }

  private extractYoutubeVideoId(url: string): string | null {
    const trimmed = url.trim();
    if (/^[a-zA-Z0-9_-]{11}$/.test(trimmed)) return trimmed;
    const match = trimmed.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/shorts\/)([a-zA-Z0-9_-]{11})/);
    return match ? match[1] : null;
  }

  private filenameFromDisposition(contentDisposition: string | null): string | null {
    if (!contentDisposition) return null;
    const utfMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
    if (utfMatch) return decodeURIComponent(utfMatch[1]);
    const match = contentDisposition.match(/filename="?([^"]+)"?/i);
    return match ? match[1] : null;
  }

  private triggerBlobDownload(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  }

  saveYoutubeAudioToNas(): void {
    if (!this.canManageNas) return;
    const videoId = this.extractYoutubeVideoId(this.youtubeUrl);
    if (!videoId || !this.explorerPathId) {
      this.youtubeError = 'Abre una ruta del NAS Explorer y pega un enlace válido.';
      return;
    }
    this.musicService.ytDlpQueue(videoId, videoId, this.explorerPathId, this.explorerSubPath || '', 'mp3').subscribe({
      next: () => {
        this.youtubeError = '';
        this.openExplorerWindow();
      },
      error: () => {
        this.youtubeError = 'No se pudo enviar al NAS.';
      }
    });
  }

  openMessengerFromTask(): void {
    if (!this.messengerOpen) {
      this.openMessengerWindow();
      return;
    }
    this.messengerMinimized = !this.messengerMinimized;
    if (!this.messengerMinimized) this.focusWindow('messenger');
  }

  resetMinesweeper(): void {
    this.stopMinesTimer();
    this.minesStatus = 'ready';
    this.minesElapsed = 0;
    this.minesFlagsLeft = this.minesCount;
    this.minesGenerated = false;
    this.minesBoard = Array.from({ length: this.minesRows }, (_, row) =>
      Array.from({ length: this.minesCols }, (_, col) => ({
        row,
        col,
        mine: false,
        revealed: false,
        flagged: false,
        adjacent: 0
      }))
    );
  }

  revealMineCell(cell: MinesweeperCell): void {
    if (cell.flagged || cell.revealed || this.minesStatus === 'won' || this.minesStatus === 'lost') return;
    if (!this.minesGenerated) {
      this.generateMines(cell.row, cell.col);
      this.startMinesTimer();
      this.minesStatus = 'playing';
    }
    if (cell.mine) {
      cell.revealed = true;
      this.minesStatus = 'lost';
      this.revealAllMines();
      this.stopMinesTimer();
      return;
    }
    this.floodReveal(cell.row, cell.col);
    this.checkMinesWin();
  }

  toggleMineFlag(event: MouseEvent, cell: MinesweeperCell): void {
    event.preventDefault();
    if (cell.revealed || this.minesStatus === 'won' || this.minesStatus === 'lost') return;
    if (!cell.flagged && this.minesFlagsLeft <= 0) return;
    cell.flagged = !cell.flagged;
    this.minesFlagsLeft += cell.flagged ? -1 : 1;
  }

  private generateMines(safeRow: number, safeCol: number): void {
    const candidates = this.minesBoard.flat().filter(cell => {
      const nearSafeClick = Math.abs(cell.row - safeRow) <= 1 && Math.abs(cell.col - safeCol) <= 1;
      return !nearSafeClick;
    });
    for (let placed = 0; placed < this.minesCount && candidates.length; placed++) {
      const index = Math.floor(Math.random() * candidates.length);
      const [cell] = candidates.splice(index, 1);
      cell.mine = true;
    }
    for (const row of this.minesBoard) {
      for (const cell of row) {
        cell.adjacent = this.neighborCells(cell.row, cell.col).filter(neighbor => neighbor.mine).length;
      }
    }
    this.minesGenerated = true;
  }

  private floodReveal(row: number, col: number): void {
    const cell = this.minesBoard[row]?.[col];
    if (!cell || cell.revealed || cell.flagged || cell.mine) return;
    cell.revealed = true;
    if (cell.adjacent > 0) return;
    for (const neighbor of this.neighborCells(row, col)) {
      this.floodReveal(neighbor.row, neighbor.col);
    }
  }

  private neighborCells(row: number, col: number): MinesweeperCell[] {
    const cells: MinesweeperCell[] = [];
    for (let y = row - 1; y <= row + 1; y++) {
      for (let x = col - 1; x <= col + 1; x++) {
        if (y === row && x === col) continue;
        const cell = this.minesBoard[y]?.[x];
        if (cell) cells.push(cell);
      }
    }
    return cells;
  }

  private revealAllMines(): void {
    for (const cell of this.minesBoard.flat()) {
      if (cell.mine) cell.revealed = true;
    }
  }

  private checkMinesWin(): void {
    const hiddenSafeCells = this.minesBoard.flat().some(cell => !cell.mine && !cell.revealed);
    if (hiddenSafeCells) return;
    this.minesStatus = 'won';
    this.stopMinesTimer();
    for (const cell of this.minesBoard.flat()) {
      if (cell.mine && !cell.flagged) {
        cell.flagged = true;
      }
    }
    this.minesFlagsLeft = 0;
  }

  private startMinesTimer(): void {
    this.stopMinesTimer();
    this.minesTimer = window.setInterval(() => {
      this.minesElapsed = Math.min(999, this.minesElapsed + 1);
    }, 1000);
  }

  private stopMinesTimer(): void {
    if (this.minesTimer) {
      window.clearInterval(this.minesTimer);
      this.minesTimer = undefined;
    }
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
    this.clearExplorerSelection();
    this.loadExplorerItems();
  }

  openExplorerFolder(item: MusicMetadataDto): void {
    if (!item.directory) return;
    this.explorerSubPath = item.path;
    this.clearExplorerSelection();
    this.loadExplorerItems();
  }

  goExplorerUp(): void {
    if (!this.explorerSubPath) return;
    const parts = this.explorerSubPath.split(/[/\\]/).filter(Boolean);
    parts.pop();
    this.explorerSubPath = parts.join('/');
    this.clearExplorerSelection();
    this.loadExplorerItems();
  }

  playExplorerTrack(track: MusicMetadataDto): void {
    if (!this.explorerPathId || track.directory) return;
    const tracks = this.explorerItems.filter(item => !item.directory);
    const index = tracks.findIndex(item => item.path === track.path);
    this.musicService.setQueue(this.explorerPathId, tracks, Math.max(0, index));
  }

  selectExplorerItem(item: MusicMetadataDto, event?: MouseEvent): void {
    if (event?.shiftKey && this.explorerSelectionAnchorPath) {
      this.selectExplorerRange(this.explorerSelectionAnchorPath, item.path);
      this.explorerSelectedPath = item.path;
      return;
    }

    if (event?.ctrlKey || event?.metaKey) {
      const next = new Set(this.explorerSelectedPaths);
      if (next.has(item.path)) {
        next.delete(item.path);
      } else {
        next.add(item.path);
      }
      this.explorerSelectedPaths = next;
      const selectedPaths = Array.from(next);
      this.explorerSelectedPath = next.has(item.path) ? item.path : (selectedPaths[selectedPaths.length - 1] || null);
      this.explorerSelectionAnchorPath = item.path;
      return;
    }

    this.explorerSelectedPaths = new Set([item.path]);
    this.explorerSelectedPath = item.path;
    this.explorerSelectionAnchorPath = item.path;
  }

  isExplorerItemSelected(item: MusicMetadataDto): boolean {
    return this.explorerSelectedPaths.has(item.path);
  }

  get explorerSelectedItem(): MusicMetadataDto | null {
    const selected = this.explorerSelectedItems;
    return selected.length === 1 ? selected[0] : null;
  }

  get explorerSelectedItems(): MusicMetadataDto[] {
    if (!this.explorerSelectedPaths.size) return [];
    return this.explorerVisibleItems.filter(item => this.explorerSelectedPaths.has(item.path));
  }

  get explorerSelectionCount(): number {
    return this.explorerSelectedItems.length;
  }

  get explorerCanRename(): boolean {
    return this.explorerSelectionCount === 1;
  }

  get explorerCanBulkOperate(): boolean {
    return this.explorerSelectionCount > 0;
  }

  get explorerClipboardLabel(): string {
    if (!this.explorerClipboard) return '';
    if (this.explorerClipboard.items.length === 1) return this.explorerClipboard.items[0].name;
    return `${this.explorerClipboard.items.length} elementos`;
  }

  private get explorerVisibleItems(): MusicMetadataDto[] {
    return [...this.explorerFolders, ...this.explorerTracks];
  }

  private clearExplorerSelection(): void {
    this.explorerSelectedPath = null;
    this.explorerSelectedPaths = new Set();
    this.explorerSelectionAnchorPath = null;
  }

  private selectExplorerRange(fromPath: string, toPath: string): void {
    const items = this.explorerVisibleItems;
    const fromIndex = items.findIndex(item => item.path === fromPath);
    const toIndex = items.findIndex(item => item.path === toPath);
    if (fromIndex < 0 || toIndex < 0) {
      this.explorerSelectedPaths = new Set([toPath]);
      return;
    }
    const [start, end] = fromIndex < toIndex ? [fromIndex, toIndex] : [toIndex, fromIndex];
    this.explorerSelectedPaths = new Set(items.slice(start, end + 1).map(item => item.path));
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
    const items = this.explorerSelectedItems;
    if (!items.length || this.explorerPathId == null) return;
    const destination = window.prompt('Mover a carpeta', this.explorerSubPath || '');
    if (destination === null) return;
    this.explorerLoading = true;
    forkJoin(items.map(item => this.nasService.move(this.explorerPathId!, item.path, destination.trim()))).subscribe({
      next: () => this.loadExplorerItems(),
      error: (err) => {
        this.explorerLoading = false;
        this.explorerError = err.error?.error || 'No se pudo mover.';
      }
    });
  }

  deleteExplorerItem(): void {
    if (!this.canManageNas) return;
    const items = this.explorerSelectedItems;
    if (!items.length || this.explorerPathId == null) return;
    const ok = window.confirm(this.buildExplorerDeleteMessage(items));
    if (!ok) return;
    this.explorerLoading = true;
    forkJoin(items.map(item => this.nasService.deleteFile(this.explorerPathId!, item.path))).subscribe({
      next: () => {
        this.clearExplorerSelection();
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

  loadExplorerItems(): void {
    if (this.explorerPathId == null) return;
    this.explorerLoading = true;
    this.explorerError = '';
    this.musicService.browse(this.explorerPathId, this.explorerSubPath, 0, 250).subscribe({
      next: (result) => {
        this.explorerItems = result.items;
        if (this.explorerSelectedPaths.size) {
          const visiblePaths = new Set(this.explorerItems.map(item => item.path));
          this.explorerSelectedPaths = new Set(Array.from(this.explorerSelectedPaths).filter(path => visiblePaths.has(path)));
          if (this.explorerSelectedPath && !this.explorerSelectedPaths.has(this.explorerSelectedPath)) {
            this.explorerSelectedPath = Array.from(this.explorerSelectedPaths)[0] || null;
          }
          if (this.explorerSelectionAnchorPath && !this.explorerSelectedPaths.has(this.explorerSelectionAnchorPath)) {
            this.explorerSelectionAnchorPath = this.explorerSelectedPath;
          }
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
    const youtube = this.getDefaultYoutubeWindowSize();
    const browser = this.getDefaultBrowserWindowSize();
    const manager = this.getDefaultMusicManagerWindowSize();
    this.desktopPanelSize = player;
    this.desktopPanelPosition = {
      x: Math.max(24, Math.round((window.innerWidth - player.width) / 2)),
      y: Math.max(24, Math.round((window.innerHeight - player.height) / 2) - 12)
    };
    this.explorerWindowSize = explorer;
    this.explorerWindowPosition = this.clampWindowPosition(132, 84, explorer);
    this.musicManagerWindowSize = manager;
    this.musicManagerWindowPosition = this.clampWindowPosition(190, 96, manager);
    this.messengerWindowSize = messenger;
    this.messengerWindowPosition = this.clampWindowPosition(220, 112, messenger);
    this.youtubeWindowSize = youtube;
    this.youtubeWindowPosition = this.clampWindowPosition(292, 132, youtube);
    this.browserWindowSize = browser;
    this.browserWindowPosition = this.clampWindowPosition(250, 86, browser);
  }

  setSkin(skin: 'wmp' | 'winamp' | 'macos' | 'foobar'): void {
    this.playerSkin = skin;
    this.desktopStartOpen = false;
    if (typeof localStorage !== 'undefined') localStorage.setItem(this.skinStorageKey, skin);
    this.fitPlayerWindowToSkin();
  }

  cycleSkin(): void {
    const ids = this.skinOptions.map(s => s.id);
    this.setSkin(ids[(ids.indexOf(this.playerSkin) + 1) % ids.length]);
  }

  private loadSkinPreference(): void {
    if (typeof localStorage === 'undefined') return;
    const saved = localStorage.getItem(this.skinStorageKey) as any;
    if (saved && this.skinOptions.some(s => s.id === saved)) this.playerSkin = saved;
  }

  private loadWallpaperPreference(): void {
    if (typeof localStorage === 'undefined') return;
    const savedWallpaper = localStorage.getItem(this.wallpaperStorageKey);
    const savedCustomWallpaper = localStorage.getItem(this.customWallpaperStorageKey) || '';
    this.customWallpaperUrl = savedCustomWallpaper;
    if (savedWallpaper === 'custom' && savedCustomWallpaper) {
      this.selectedWallpaper = 'custom';
      return;
    }
    if (savedWallpaper && this.wallpaperOptions.some(option => option.id === savedWallpaper)) {
      this.selectedWallpaper = savedWallpaper;
    }
  }

  private saveWallpaperPreference(): void {
    if (typeof localStorage === 'undefined') return;
    try {
      localStorage.setItem(this.wallpaperStorageKey, this.selectedWallpaper);
      if (this.customWallpaperUrl) {
        localStorage.setItem(this.customWallpaperStorageKey, this.customWallpaperUrl);
      } else {
        localStorage.removeItem(this.customWallpaperStorageKey);
      }
    } catch {
      this.wallpaperUploadError = 'No se pudo guardar el fondo en este navegador.';
    }
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
    this.musicManagerWindowPosition = this.clampWindowPosition(
      this.musicManagerWindowPosition.x,
      this.musicManagerWindowPosition.y,
      this.musicManagerWindowSize
    );
    if (this.messengerMaximized) {
      this.applyMessengerMaximizedBounds();
    } else {
      this.messengerWindowPosition = this.clampWindowPosition(
        this.messengerWindowPosition.x,
        this.messengerWindowPosition.y,
        this.messengerWindowSize
      );
    }
    if (this.youtubeDownloaderMaximized) {
      this.applyYoutubeMaximizedBounds();
    } else {
      this.youtubeWindowPosition = this.clampWindowPosition(
        this.youtubeWindowPosition.x,
        this.youtubeWindowPosition.y,
        this.youtubeWindowSize
      );
    }
    if (this.browserMaximized) {
      this.applyBrowserMaximizedBounds();
    } else {
      this.browserWindowPosition = this.clampWindowPosition(
        this.browserWindowPosition.x,
        this.browserWindowPosition.y,
        this.browserWindowSize
      );
    }
    this.minesweeperWindowPosition = this.clampWindowPosition(
      this.minesweeperWindowPosition.x,
      this.minesweeperWindowPosition.y,
      this.minesweeperWindowSize
    );
    this.desktopPanelSize = this.clampWindowSize('player', this.desktopPanelSize.width, this.desktopPanelSize.height);
    if (!this.explorerMaximized) {
      this.explorerWindowSize = this.clampWindowSize('explorer', this.explorerWindowSize.width, this.explorerWindowSize.height);
    }
    this.musicManagerWindowSize = this.clampWindowSize('manager', this.musicManagerWindowSize.width, this.musicManagerWindowSize.height);
    if (!this.messengerMaximized) {
      this.messengerWindowSize = this.clampWindowSize('messenger', this.messengerWindowSize.width, this.messengerWindowSize.height);
    }
    if (!this.youtubeDownloaderMaximized) {
      this.youtubeWindowSize = this.clampWindowSize('youtube', this.youtubeWindowSize.width, this.youtubeWindowSize.height);
    }
    if (!this.browserMaximized) {
      this.browserWindowSize = this.clampWindowSize('browser', this.browserWindowSize.width, this.browserWindowSize.height);
    }
  }

  private applyExplorerMaximizedBounds(): void {
    if (typeof window === 'undefined') return;
    const taskbarHeight = this.isFullscreen ? 0 : this.desktopTaskbarHeight;
    this.explorerWindowPosition = { x: 0, y: 0 };
    this.explorerWindowSize = {
      width: window.innerWidth,
      height: Math.max(300, window.innerHeight - taskbarHeight)
    };
  }

  private applyMessengerMaximizedBounds(): void {
    if (typeof window === 'undefined') return;
    const taskbarHeight = this.isFullscreen ? 0 : this.desktopTaskbarHeight;
    this.messengerWindowPosition = { x: 0, y: 0 };
    this.messengerWindowSize = {
      width: window.innerWidth,
      height: Math.max(420, window.innerHeight - taskbarHeight)
    };
  }

  private applyYoutubeMaximizedBounds(): void {
    if (typeof window === 'undefined') return;
    const taskbarHeight = this.isFullscreen ? 0 : this.desktopTaskbarHeight;
    this.youtubeWindowPosition = { x: 0, y: 0 };
    this.youtubeWindowSize = {
      width: window.innerWidth,
      height: Math.max(390, window.innerHeight - taskbarHeight)
    };
  }

  private applyBrowserMaximizedBounds(): void {
    if (typeof window === 'undefined') return;
    const taskbarHeight = this.isFullscreen ? 0 : this.desktopTaskbarHeight;
    this.browserWindowPosition = { x: 0, y: 0 };
    this.browserWindowSize = {
      width: window.innerWidth,
      height: Math.max(420, window.innerHeight - taskbarHeight)
    };
  }

  private clampWindowPosition(x: number, y: number, size: { width: number; height: number }): { x: number; y: number } {
    if (typeof window === 'undefined') return { x, y };
    const taskbarHeight = this.isFullscreen ? 0 : this.desktopTaskbarHeight;
    const maxX = Math.max(8, window.innerWidth - size.width - 8);
    const maxY = Math.max(8, window.innerHeight - size.height - taskbarHeight - 8);
    return {
      x: Math.min(Math.max(8, x), maxX),
      y: Math.min(Math.max(8, y), maxY)
    };
  }

  private getDefaultPlayerWindowSize(skin: 'wmp' | 'winamp' | 'macos' | 'foobar' = this.playerSkin): { width: number; height: number } {
    if (skin === 'winamp') {
      return { width: 520, height: 600 };
    }
    if (typeof window === 'undefined') return { width: 1060, height: 690 };
    return {
      width: Math.min(1060, window.innerWidth - 64),
      height: Math.min(690, window.innerHeight - 70)
    };
  }

  private fitPlayerWindowToSkin(): void {
    if (typeof window === 'undefined' || !this.isDesktopWmp || this.isFullscreen) return;
    const size = this.clampWindowSize('player', this.getDefaultPlayerWindowSize(this.playerSkin).width, this.getDefaultPlayerWindowSize(this.playerSkin).height);
    this.desktopPanelSize = size;
    this.desktopPanelPosition = this.clampWindowPosition(this.desktopPanelPosition.x, this.desktopPanelPosition.y, size);
  }

  private getDefaultExplorerWindowSize(): { width: number; height: number } {
    if (typeof window === 'undefined') return { width: 560, height: 420 };
    return {
      width: Math.min(560, window.innerWidth - 200),
      height: Math.min(420, window.innerHeight - 180)
    };
  }

  private getDefaultMusicManagerWindowSize(): { width: number; height: number } {
    if (typeof window === 'undefined') return { width: 760, height: 500 };
    return {
      width: Math.min(760, window.innerWidth - 120),
      height: Math.min(500, window.innerHeight - 120)
    };
  }

  private getDefaultMessengerWindowSize(): { width: number; height: number } {
    if (typeof window === 'undefined') return { width: 760, height: 560 };
    return {
      width: Math.min(760, window.innerWidth - 120),
      height: Math.min(560, window.innerHeight - 120)
    };
  }

  private getDefaultYoutubeWindowSize(): { width: number; height: number } {
    if (typeof window === 'undefined') return { width: 620, height: 430 };
    return {
      width: Math.min(620, window.innerWidth - 140),
      height: Math.min(430, window.innerHeight - 140)
    };
  }

  private getDefaultBrowserWindowSize(): { width: number; height: number } {
    if (typeof window === 'undefined') return { width: 820, height: 560 };
    return {
      width: Math.min(820, window.innerWidth - 110),
      height: Math.min(560, window.innerHeight - 100)
    };
  }

  private clampWindowSize(target: DesktopWindowTarget, width: number, height: number): { width: number; height: number } {
    if (typeof window === 'undefined') return { width, height };
    const taskbarHeight = this.isFullscreen ? 0 : this.desktopTaskbarHeight;
    const minWidth = target === 'player' ? (this.playerSkin === 'winamp' ? 500 : 760) : target === 'manager' ? 560 : target === 'messenger' ? 560 : target === 'browser' ? 540 : target === 'youtube' ? 500
      : target === 'calculator' ? 220 : target === 'notepad' ? 280 : target === 'equalizer' ? 280 : 420;
    const minHeight = target === 'player' ? (this.playerSkin === 'winamp' ? 560 : 500) : target === 'manager' ? 360 : target === 'messenger' ? 420 : target === 'browser' ? 380 : target === 'youtube' ? 360
      : target === 'calculator' ? 280 : target === 'notepad' ? 200 : target === 'equalizer' ? 160 : 300;
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
    this.setWindowSize(state.target, { width: nextWidth, height: nextHeight });
    this.setWindowPosition(state.target, clampedPosition);
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

  // ── Calculator ─────────────────────────────────────────────────────────────
  openCalculator(): void { this.calculatorOpen = true; this.calculatorMinimized = false; this.activeDesktopWindow = 'calculator'; this.desktopStartOpen = false; }
  closeCalculator(): void { this.calculatorOpen = false; this.calculatorMaximized = false; }

  toggleCalculatorMaximize(): void {
    if (typeof window === 'undefined') return;
    this.focusWindow('calculator');
    if (this.calculatorMaximized) {
      this.calculatorWindowSize = this.clampWindowSize('calculator', this.calculatorRestoreWindow.size.width, this.calculatorRestoreWindow.size.height);
      this.calculatorWindowPosition = this.clampWindowPosition(this.calculatorRestoreWindow.position.x, this.calculatorRestoreWindow.position.y, this.calculatorWindowSize);
      this.calculatorMaximized = false;
    } else {
      this.calculatorRestoreWindow = { position: { ...this.calculatorWindowPosition }, size: { ...this.calculatorWindowSize } };
      this.calculatorMaximized = true;
      const h = this.isFullscreen ? 0 : this.desktopTaskbarHeight;
      this.calculatorWindowPosition = { x: 0, y: 0 };
      this.calculatorWindowSize = { width: window.innerWidth, height: Math.max(280, window.innerHeight - h) };
    }
  }

  calcInput(digit: string): void {
    if (this.calcNewInput) { this.calcDisplay = digit === '.' ? '0.' : digit; this.calcNewInput = false; return; }
    if (digit === '.' && this.calcDisplay.includes('.')) return;
    if (this.calcDisplay === '0' && digit !== '.') { this.calcDisplay = digit; return; }
    if (this.calcDisplay.length < 15) this.calcDisplay += digit;
  }

  calcOperation(op: string): void {
    const cur = parseFloat(this.calcDisplay);
    if (this.calcPendingOp && !this.calcNewInput) this.calcEquals();
    this.calcPendingVal = parseFloat(this.calcDisplay);
    this.calcPendingOp = op;
    this.calcNewInput = true;
  }

  calcEquals(): void {
    if (!this.calcPendingOp) return;
    const cur = parseFloat(this.calcDisplay);
    let result = 0;
    switch (this.calcPendingOp) {
      case '+': result = this.calcPendingVal + cur; break;
      case '-': result = this.calcPendingVal - cur; break;
      case '*': result = this.calcPendingVal * cur; break;
      case '/': result = cur !== 0 ? this.calcPendingVal / cur : 0; break;
    }
    this.calcDisplay = parseFloat(result.toPrecision(10)).toString();
    this.calcPendingOp = '';
    this.calcNewInput = true;
  }

  calcClear(): void { this.calcDisplay = '0'; this.calcPendingOp = ''; this.calcPendingVal = 0; this.calcNewInput = true; }
  calcClearEntry(): void { this.calcDisplay = '0'; this.calcNewInput = true; }
  calcBackspace(): void { if (this.calcNewInput) return; this.calcDisplay = this.calcDisplay.length > 1 ? this.calcDisplay.slice(0, -1) : '0'; }
  calcPlusMinus(): void { this.calcDisplay = (parseFloat(this.calcDisplay) * -1).toString(); }
  calcPercent(): void { this.calcDisplay = (parseFloat(this.calcDisplay) / 100).toString(); this.calcNewInput = true; }
  calcSqrt(): void { this.calcDisplay = Math.sqrt(parseFloat(this.calcDisplay)).toString(); this.calcNewInput = true; }

  // ── Notepad ───────────────────────────────────────────────────────────────
  openNotepad(): void {
    this.loadNotepad();
    this.notepadOpen = true;
    this.notepadMinimized = false;
    this.activeDesktopWindow = 'notepad';
    this.desktopStartOpen = false;
  }
  closeNotepad(): void { this.saveNotepad(); this.notepadOpen = false; this.notepadMaximized = false; }

  createNotepadFile(): void {
    this.saveNotepad();
    const file: NotepadFile = {
      id: this.createNotepadFileId(),
      name: this.nextNotepadFileName(),
      content: '',
      updatedAt: Date.now()
    };
    this.notepadFiles = [...this.notepadFiles, file];
    this.selectNotepadFile(file.id);
    this.saveNotepad();
  }

  selectNotepadFile(id: string): void {
    this.saveNotepad();
    const file = this.notepadFiles.find(item => item.id === id);
    if (!file) return;
    this.activeNotepadFileId = file.id;
    this.notepadFileName = file.name;
    this.notepadText = file.content;
    this.notepadSaveStatus = '';
  }

  deleteNotepadFile(file: NotepadFile, event?: MouseEvent): void {
    event?.stopPropagation();
    if (this.notepadFiles.length <= 1) {
      this.notepadText = '';
      this.notepadFileName = file.name;
      this.saveNotepad();
      return;
    }
    this.notepadFiles = this.notepadFiles.filter(item => item.id !== file.id);
    if (this.activeNotepadFileId === file.id) {
      const next = this.notepadFiles[0];
      this.activeNotepadFileId = next.id;
      this.notepadFileName = next.name;
      this.notepadText = next.content;
    }
    this.saveNotepad();
  }

  onNotepadInput(): void {
    this.notepadSaveStatus = 'WINDOWS.NOTEPAD_STATUS_SAVING';
    if (this.notepadSaveTimer) window.clearTimeout(this.notepadSaveTimer);
    this.notepadSaveTimer = window.setTimeout(() => this.saveNotepad(), 350);
  }

  renameNotepadFile(): void {
    const nextName = window.prompt('Nombre del archivo', this.notepadFileName);
    if (!nextName?.trim()) return;
    this.notepadFileName = nextName.trim().endsWith('.txt') ? nextName.trim() : `${nextName.trim()}.txt`;
    this.saveNotepad();
  }

  saveNotepad(): void {
    if (typeof localStorage === 'undefined') return;
    if (!this.notepadFiles.length) {
      this.notepadFiles = [this.createDefaultNotepadFile('')];
      this.activeNotepadFileId = this.notepadFiles[0].id;
    }
    const activeIndex = this.notepadFiles.findIndex(file => file.id === this.activeNotepadFileId);
    if (activeIndex >= 0) {
      const name = this.notepadFileName.trim() || 'notas.txt';
      this.notepadFiles = this.notepadFiles.map((file, index) => index === activeIndex
        ? { ...file, name, content: this.notepadText, updatedAt: Date.now() }
        : file
      );
    }
    localStorage.setItem(this.getNotepadStorageKey(), JSON.stringify({
      owner: this.getNotepadUserName(),
      activeId: this.activeNotepadFileId,
      files: this.notepadFiles
    }));
    this.notepadSaveStatus = 'WINDOWS.NOTEPAD_STATUS_SAVED';
  }

  private loadNotepad(): void {
    if (typeof localStorage === 'undefined') return;
    const ownerKey = this.getNotepadOwnerKey();
    this.notepadOwnerKey = ownerKey;
    const raw = localStorage.getItem(this.getNotepadStorageKey());
    try {
      if (raw) {
        const disk = JSON.parse(raw) as { activeId?: string; files?: NotepadFile[] };
        this.notepadFiles = Array.isArray(disk.files) && disk.files.length
          ? disk.files
          : [this.createDefaultNotepadFile('')];
        this.activeNotepadFileId = disk.activeId && this.notepadFiles.some(file => file.id === disk.activeId)
          ? disk.activeId
          : this.notepadFiles[0].id;
      } else {
        const legacyText = localStorage.getItem(this.notepadStorageKey) || '';
        this.notepadFiles = [this.createDefaultNotepadFile(legacyText)];
        this.activeNotepadFileId = this.notepadFiles[0].id;
        this.saveNotepad();
      }
    } catch {
      this.notepadFiles = [this.createDefaultNotepadFile('')];
      this.activeNotepadFileId = this.notepadFiles[0].id;
    }
    const active = this.activeNotepadFile || this.notepadFiles[0];
    this.activeNotepadFileId = active.id;
    this.notepadFileName = active.name;
    this.notepadText = active.content;
    this.notepadSaveStatus = raw ? 'WINDOWS.NOTEPAD_STATUS_LOADED' : 'WINDOWS.NOTEPAD_STATUS_READY';
  }

  private getNotepadStorageKey(): string {
    return `${this.notepadDiskPrefix}.${this.getNotepadOwnerKey()}.notepad`;
  }

  private getNotepadOwnerKey(): string {
    return this.getNotepadUserName().toLowerCase().replace(/[^a-z0-9_-]+/g, '_') || 'invitado';
  }

  private getNotepadUserName(): string {
    return this.authService.getCurrentUser()?.username || 'Invitado';
  }

  private createDefaultNotepadFile(content: string): NotepadFile {
    return {
      id: this.createNotepadFileId(),
      name: 'notas.txt',
      content,
      updatedAt: Date.now()
    };
  }

  private createNotepadFileId(): string {
    return `note-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  }

  private nextNotepadFileName(): string {
    const base = 'nota';
    let count = this.notepadFiles.length + 1;
    let name = `${base}-${count}.txt`;
    while (this.notepadFiles.some(file => file.name === name)) {
      count++;
      name = `${base}-${count}.txt`;
    }
    return name;
  }

  toggleNotepadMaximize(): void {
    if (typeof window === 'undefined') return;
    this.focusWindow('notepad');
    if (this.notepadMaximized) {
      this.notepadWindowSize = this.clampWindowSize('notepad', this.notepadRestoreWindow.size.width, this.notepadRestoreWindow.size.height);
      this.notepadWindowPosition = this.clampWindowPosition(this.notepadRestoreWindow.position.x, this.notepadRestoreWindow.position.y, this.notepadWindowSize);
      this.notepadMaximized = false;
    } else {
      this.notepadRestoreWindow = { position: { ...this.notepadWindowPosition }, size: { ...this.notepadWindowSize } };
      this.notepadMaximized = true;
      const h = this.isFullscreen ? 0 : this.desktopTaskbarHeight;
      this.notepadWindowPosition = { x: 0, y: 0 };
      this.notepadWindowSize = { width: window.innerWidth, height: Math.max(200, window.innerHeight - h) };
    }
  }

  // ── Equalizer ─────────────────────────────────────────────────────────────
  openEqualizer(): void { this.equalizerOpen = true; this.equalizerMinimized = false; this.activeDesktopWindow = 'equalizer'; this.desktopStartOpen = false; }
  closeEqualizer(): void { this.equalizerOpen = false; this.equalizerMaximized = false; }

  toggleEqualizerMaximize(): void {
    if (typeof window === 'undefined') return;
    this.focusWindow('equalizer');
    if (this.equalizerMaximized) {
      this.equalizerWindowSize = this.clampWindowSize('equalizer', this.equalizerRestoreWindow.size.width, this.equalizerRestoreWindow.size.height);
      this.equalizerWindowPosition = this.clampWindowPosition(this.equalizerRestoreWindow.position.x, this.equalizerRestoreWindow.position.y, this.equalizerWindowSize);
      this.equalizerMaximized = false;
    } else {
      this.equalizerRestoreWindow = { position: { ...this.equalizerWindowPosition }, size: { ...this.equalizerWindowSize } };
      this.equalizerMaximized = true;
      const h = this.isFullscreen ? 0 : this.desktopTaskbarHeight;
      this.equalizerWindowPosition = { x: 0, y: 0 };
      this.equalizerWindowSize = { width: window.innerWidth, height: Math.max(160, window.innerHeight - h) };
    }
  }

  setEqBand(band: 'low' | 'mid' | 'high', value: string | number): void {
    const dB = parseFloat(value as string);
    if (band === 'low') this.eqLow = dB;
    if (band === 'mid') this.eqMid = dB;
    if (band === 'high') this.eqHigh = dB;
    this.musicService.mainPlayer.setEq(band, dB);
  }

  resetEq(): void { ['low', 'mid', 'high'].forEach(b => this.setEqBand(b as any, 0)); }

  setWaEqBand(index: number, value: string | number): void {
    this.waEqBands[index] = parseFloat(value as string);
    if (!this.waEqOn) return;
    const low  = (this.waEqBands[0] + this.waEqBands[1] + this.waEqBands[2]) / 3;
    const mid  = (this.waEqBands[3] + this.waEqBands[4] + this.waEqBands[5]) / 3;
    const high = (this.waEqBands[6] + this.waEqBands[7] + this.waEqBands[8] + this.waEqBands[9]) / 4;
    this.musicService.mainPlayer.setEq('low', low);
    this.musicService.mainPlayer.setEq('mid', mid);
    this.musicService.mainPlayer.setEq('high', high);
  }

  toggleWaEq(): void {
    this.waEqOn = !this.waEqOn;
    if (!this.waEqOn) {
      this.musicService.mainPlayer.setEq('low', 0);
      this.musicService.mainPlayer.setEq('mid', 0);
      this.musicService.mainPlayer.setEq('high', 0);
    } else {
      this.setWaEqBand(0, this.waEqBands[0]);
    }
  }

  resetWaEq(): void {
    this.waEqBands = new Array(10).fill(0);
    this.musicService.mainPlayer.setEq('low', 0);
    this.musicService.mainPlayer.setEq('mid', 0);
    this.musicService.mainPlayer.setEq('high', 0);
  }

  // ── Snake ──────────────────────────────────────────────────────────────────
  openSnake(): void { this.snakeOpen = true; this.snakeMinimized = false; this.activeDesktopWindow = 'snake'; this.desktopStartOpen = false; }
  closeSnake(): void { this.snakeOpen = false; if (this.snakeRaf) { cancelAnimationFrame(this.snakeRaf); this.snakeRaf = undefined; } }

  startSnake(): void {
    this.snakeShowLeaderboard = false;
    this.snakeBody = [{ x: 10, y: 9 }, { x: 9, y: 9 }, { x: 8, y: 9 }];
    this.snakeDir = 'right'; this.snakePendingDir = 'right';
    this.snakeScore = 0; this.snakeStatus = 'playing';
    this.spawnSnakeFood();
    if (this.snakeRaf) cancelAnimationFrame(this.snakeRaf);
    this.snakeLastTime = 0;
    this.snakeRaf = requestAnimationFrame(ts => this.snakeLoop(ts));
  }

  private snakeLoop(ts: number): void {
    if (this.snakeStatus !== 'playing') return;
    if (ts - this.snakeLastTime >= this.snakeSpeed) {
      this.snakeLastTime = ts;
      this.snakeStep();
    }
    this.snakeDraw();
    this.snakeRaf = requestAnimationFrame(t => this.snakeLoop(t));
  }

  private snakeStep(): void {
    this.snakeDir = this.snakePendingDir;
    const head = { ...this.snakeBody[0] };
    if (this.snakeDir === 'right') head.x++;
    if (this.snakeDir === 'left')  head.x--;
    if (this.snakeDir === 'up')    head.y--;
    if (this.snakeDir === 'down')  head.y++;
    head.x = (head.x + this.SNAKE_COLS) % this.SNAKE_COLS;
    head.y = (head.y + this.SNAKE_ROWS) % this.SNAKE_ROWS;
    if (this.snakeBody.some(s => s.x === head.x && s.y === head.y)) {
      this.snakeStatus = 'dead';
      this.cdr.detectChanges();
      this.submitSnakeScore(this.snakeScore);
      this.loadSnakeLeaderboard();
      return;
    }
    this.snakeBody.unshift(head);
    if (head.x === this.snakeFood.x && head.y === this.snakeFood.y) {
      this.snakeScore += 10; this.spawnSnakeFood();
    } else { this.snakeBody.pop(); }
  }

  private spawnSnakeFood(): void {
    let pos: { x: number; y: number };
    do { pos = { x: Math.floor(Math.random() * this.SNAKE_COLS), y: Math.floor(Math.random() * this.SNAKE_ROWS) }; }
    while (this.snakeBody.some(s => s.x === pos.x && s.y === pos.y));
    this.snakeFood = pos;
  }

  private snakeDraw(): void {
    const canvas = this.snakeCanvas?.nativeElement;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const C = this.SNAKE_CELL;
    ctx.fillStyle = '#0a0a0a';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#ff4444';
    ctx.fillRect(this.snakeFood.x * C + 1, this.snakeFood.y * C + 1, C - 2, C - 2);
    this.snakeBody.forEach((seg, i) => {
      ctx.fillStyle = i === 0 ? '#00ff88' : '#00cc66';
      ctx.fillRect(seg.x * C + 1, seg.y * C + 1, C - 2, C - 2);
    });
    if (this.snakeStatus === 'dead') {
      ctx.fillStyle = 'rgba(0,0,0,0.6)';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = '#ff4444';
      ctx.font = 'bold 20px monospace';
      ctx.textAlign = 'center';
      ctx.fillText('GAME OVER', canvas.width / 2, canvas.height / 2 - 10);
      ctx.fillStyle = '#aaa';
      ctx.font = '13px monospace';
      ctx.fillText(`Score: ${this.snakeScore}`, canvas.width / 2, canvas.height / 2 + 14);
      ctx.fillText('Press Space to restart', canvas.width / 2, canvas.height / 2 + 34);
    }
  }

  onSnakeKey(e: KeyboardEvent): void {
    const opp: Record<string, string> = { right: 'left', left: 'right', up: 'down', down: 'up' };
    const map: Record<string, 'up'|'down'|'left'|'right'> = {
      ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right',
      w: 'up', s: 'down', a: 'left', d: 'right'
    };
    if (e.key === ' ' || e.key === 'Enter') {
      e.preventDefault();
      if (this.snakeStatus !== 'playing') this.startSnake();
      return;
    }
    const newDir = map[e.key];
    if (newDir && newDir !== opp[this.snakeDir]) { e.preventDefault(); this.snakePendingDir = newDir; }
  }

  private submitSnakeScore(score: number): void {
    if (score <= 0) return;
    this.http.post(`${this.backendUrl}/api/snake/score`, { score }).subscribe({
      error: err => console.warn('Could not submit snake score', err)
    });
  }

  loadSnakeLeaderboard(): void {
    this.snakeLeaderboardLoading = true;
    this.snakeLeaderboard = [];
    this.http.get<{ rank: number; username: string; avatarUrl: string; score: number }[]>(
      `${this.backendUrl}/api/snake/leaderboard`
    ).subscribe({
      next: data => {
        this.snakeLeaderboard = data;
        this.snakeLeaderboardLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.snakeLeaderboard = [];
        this.snakeLeaderboardLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  openSnakeLeaderboard(): void { this.snakeShowLeaderboard = true; this.loadSnakeLeaderboard(); }
  closeSnakeLeaderboard(): void { this.snakeShowLeaderboard = false; }

  // ── Screensaver ───────────────────────────────────────────────────────────
  private lastActivity = Date.now();

  private resetInactivityTimestamp(): void { this.lastActivity = Date.now(); }

  private checkInactivity(): void {
    if (!this.isOpen || this.bsodActive || this.screensaverActive) return;
    if (Date.now() - this.lastActivity > this.screensaverDelay) this.activateScreensaver();
  }

  private activateScreensaver(): void {
    this.screensaverActive = true;
    this.dvdX = Math.random() * 200 + 50;
    this.dvdY = Math.random() * 100 + 50;
    this.screensaverRaf = requestAnimationFrame(() => this.screensaverLoop());
  }

  private screensaverLoop(): void {
    if (!this.screensaverActive) return;
    if (typeof window === 'undefined') return;
    const w = window.innerWidth - 180;
    const h = window.innerHeight - 60;
    this.dvdX += this.dvdVX;
    this.dvdY += this.dvdVY;
    let bounced = false;
    if (this.dvdX <= 0 || this.dvdX >= w) { this.dvdVX *= -1; bounced = true; }
    if (this.dvdY <= 0 || this.dvdY >= h) { this.dvdVY *= -1; bounced = true; }
    if (bounced) this.dvdColor = `hsl(${Math.random() * 360},100%,60%)`;
    this.dvdX = Math.max(0, Math.min(this.dvdX, w));
    this.dvdY = Math.max(0, Math.min(this.dvdY, h));
    this.screensaverRaf = requestAnimationFrame(() => this.screensaverLoop());
  }

  dismissScreensaver(): void {
    this.screensaverActive = false;
    this.lastActivity = Date.now();
    if (this.screensaverRaf) { cancelAnimationFrame(this.screensaverRaf); this.screensaverRaf = undefined; }
  }

  // ── BSOD ──────────────────────────────────────────────────────────────────
  @HostListener('document:keydown', ['$event'])
  onGlobalKeydown(event: KeyboardEvent): void {
    if (!this.isOpen) return;
    if (this.screensaverActive) { this.dismissScreensaver(); return; }
    if (this.bsodActive) { if (event.key === 'Enter') this.bsodActive = false; return; }
    this.resetInactivityTimestamp();
    this.bsodKeySeq = (this.bsodKeySeq + event.key.toLowerCase()).slice(-this.bsodCode.length);
    if (this.bsodKeySeq === this.bsodCode) { this.bsodActive = true; this.bsodKeySeq = ''; }
    if (this.snakeOpen && !this.snakeMinimized && this.activeDesktopWindow === 'snake') this.onSnakeKey(event);
  }

  // ── XP Notifications ──────────────────────────────────────────────────────
  private showXpNotif(title: string, body: string): void {
    const id = ++this.xpNotifCounter;
    this.xpNotifs = [...this.xpNotifs, { id, title, body }];
    setTimeout(() => this.dismissXpNotif(id), 5000);
  }

  dismissXpNotif(id: number): void { this.xpNotifs = this.xpNotifs.filter(n => n.id !== id); }

  // ── Context menu ──────────────────────────────────────────────────────────
  openCtxMenu(e: MouseEvent): void {
    e.preventDefault();
    this.ctxMenuOpen = true;
    this.ctxMenuX = e.clientX;
    this.ctxMenuY = e.clientY;
    this.desktopStartOpen = false;
  }

  closeCtxMenu(): void { this.ctxMenuOpen = false; }

  // ── Explorer context menu ─────────────────────────────────────────────────
  openExplorerCtxMenu(e: MouseEvent, item: MusicMetadataDto | null): void {
    e.preventDefault();
    e.stopPropagation();
    if (item && !this.explorerSelectedPaths.has(item.path)) {
      this.explorerSelectedPaths = new Set([item.path]);
      this.explorerSelectedPath = item.path;
      this.explorerSelectionAnchorPath = item.path;
    }
    this.explorerCtxTarget = item;
    this.explorerCtxOpen = true;
    this.explorerCtxX = e.clientX;
    this.explorerCtxY = e.clientY;
  }

  closeExplorerCtxMenu(): void { this.explorerCtxOpen = false; }

  explorerCtxCopy(): void {
    const items = this.getExplorerContextItems();
    if (!items.length || !this.explorerPathId) return;
    this.explorerClipboard = { items: items.map(item => ({ ...item })), pathId: this.explorerPathId, mode: 'copy' };
    this.closeExplorerCtxMenu();
  }

  explorerCtxCut(): void {
    const items = this.getExplorerContextItems();
    if (!items.length || !this.explorerPathId) return;
    this.explorerClipboard = { items: items.map(item => ({ ...item })), pathId: this.explorerPathId, mode: 'cut' };
    this.closeExplorerCtxMenu();
  }

  explorerCtxPaste(): void {
    if (!this.explorerClipboard || !this.explorerPathId) return;
    const { items, pathId, mode } = this.explorerClipboard;
    if (!items.length) return;
    const destFolder = this.explorerSubPath || '';
    this.explorerLoading = true;
    if (mode === 'copy') {
      forkJoin(items.map(item => this.nasService.copyFile(pathId, item.path, this.explorerPathId!, destFolder))).subscribe({
        next: () => { this.explorerClipboard = null; this.loadExplorerItems(); },
        error: (err) => {
          this.explorerLoading = false;
          alert('Error al copiar: ' + (err.error?.error || err.message));
        }
      });
    } else {
      if (pathId !== this.explorerPathId) { this.explorerLoading = false; alert('El cortar entre rutas NAS distintas no esta soportado. Usa Copiar.'); return; }
      forkJoin(items.map(item => this.nasService.move(this.explorerPathId!, item.path, destFolder))).subscribe({
        next: () => { this.explorerClipboard = null; this.clearExplorerSelection(); this.loadExplorerItems(); },
        error: (err) => {
          this.explorerLoading = false;
          alert('Error al mover: ' + (err.error?.error || err.message));
        }
      });
    }
    this.closeExplorerCtxMenu();
  }

  explorerCtxDelete(): void {
    const items = this.getExplorerContextItems();
    if (!items.length || !this.explorerPathId) return;
    const ok = window.confirm(this.buildExplorerDeleteMessage(items));
    this.closeExplorerCtxMenu();
    if (!ok) return;
    this.explorerLoading = true;
    forkJoin(items.map(item => this.nasService.deleteFile(this.explorerPathId!, item.path))).subscribe({
      next: () => { this.clearExplorerSelection(); this.loadExplorerItems(); },
      error: (err) => {
        this.explorerLoading = false;
        alert('Error al eliminar: ' + (err.error?.error || err.message));
      }
    });
  }

  explorerCtxRename(): void {
    if (!this.explorerSelectedItem || !this.explorerPathId) return;
    const item = this.explorerSelectedItem;
    this.closeExplorerCtxMenu();
    const nextName = window.prompt('Nuevo nombre', item.name);
    if (!nextName || nextName.trim() === item.name) return;
    this.nasService.rename(this.explorerPathId, item.path, nextName.trim()).subscribe({
      next: () => this.loadExplorerItems(),
      error: (err) => alert('Error al renombrar: ' + (err.error?.error || err.message))
    });
  }

  explorerCtxPlay(): void {
    const item = this.explorerCtxTarget;
    this.closeExplorerCtxMenu();
    if (item && !item.directory) this.playExplorerTrack(item);
  }

  explorerCtxDownload(): void {
    const item = this.explorerCtxTarget;
    this.closeExplorerCtxMenu();
    if (!item || !this.explorerPathId) return;
    if (item.directory) {
      this.nasService.downloadFolderZip(this.explorerPathId, item.path).subscribe(blob => {
        const a = document.createElement('a'); a.href = URL.createObjectURL(blob);
        a.download = item.name + '.zip'; a.click();
      });
    } else {
      this.nasService.downloadFile(this.explorerPathId, item.path).subscribe(blob => {
        const a = document.createElement('a'); a.href = URL.createObjectURL(blob);
        a.download = item.name; a.click();
      });
    }
  }

  explorerCtxNewFolder(): void {
    this.closeExplorerCtxMenu();
    this.createExplorerFolder();
  }

  private getExplorerContextItems(): MusicMetadataDto[] {
    if (this.explorerCtxTarget && this.explorerSelectedPaths.has(this.explorerCtxTarget.path)) {
      return this.explorerSelectedItems;
    }
    return this.explorerCtxTarget ? [this.explorerCtxTarget] : [];
  }

  private buildExplorerDeleteMessage(items: MusicMetadataDto[]): string {
    if (items.length === 1) {
      const item = items[0];
      return `Eliminar ${item.directory ? 'la carpeta' : 'el archivo'} "${item.name}"?`;
    }
    return `Eliminar ${items.length} elementos seleccionados?`;
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
