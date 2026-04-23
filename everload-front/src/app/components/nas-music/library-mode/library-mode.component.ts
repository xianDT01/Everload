import { ChangeDetectorRef, Component, OnInit, OnDestroy, AfterViewInit, HostListener, ViewChild, ElementRef } from '@angular/core';
import { HttpEventType } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { NasPath, NasService } from '../../../services/nas.service';
import { MusicMetadataDto, MusicService, PagedMusicResult, PlayerState } from '../../../services/music.service';
import { AuthService } from '../../../services/auth.service';
import { TranslateService } from '@ngx-translate/core';

interface NasBanner {
  id: number;
  title: string;
  subtitle: string;
  gradient: string;
  view?: 'home' | 'liked' | 'history';
  pathIndex?: number;
  subPath?: string;
  track?: MusicMetadataDto;
  pathId?: number;
}

@Component({
  selector: 'app-library-mode',
  templateUrl: './library-mode.component.html',
  styleUrls: ['./library-mode.component.css']
})
export class LibraryModeComponent implements OnInit, AfterViewInit, OnDestroy {
  private static readonly DETAILS_PANEL_STORAGE_KEY = 'ev_nas_details_panel_open';

  paths: NasPath[] = [];
  selectedPathId: number | null = null;
  currentSubPath = '';

  favoriteFolders: { pathId: number; subPath: string; name: string }[] = [];
  private brokenCoverPaths = new Set<string>();

  currentView: 'home' | 'liked' | 'history' | 'folder' = 'folder';

  items: MusicMetadataDto[] = [];
  historyItems: any[] = [];
  likedItems: any[] = [];

  state: PlayerState | null = null;
  shuffle = false;
  repeat: 'none' | 'one' | 'all' = 'none';
  queueIndex = -1;
  searchQuery = '';
  searchResults: MusicMetadataDto[] | null = null;
  searchLoading = false;
  private searchDebounce?: ReturnType<typeof setTimeout>;
  likedSortBy: 'date' | 'title' | 'artist' = 'date';

  private static readonly RANDOM_GRADIENTS = [
    'linear-gradient(135deg, #0a1f14 0%, #14532d 45%, #1db954 100%)',
    'linear-gradient(135deg, #1c0a0a 0%, #7f1d1d 45%, #ef4444 100%)',
    'linear-gradient(135deg, #0c1a2e 0%, #0e3a5c 45%, #0ea5e9 100%)',
    'linear-gradient(135deg, #1a1200 0%, #713f12 45%, #f59e0b 100%)',
    'linear-gradient(135deg, #150a2a 0%, #581c87 45%, #a855f7 100%)',
  ];

  // ── Banners ───────────────────────────────────────────────────────────────
  banners: NasBanner[] = [
    {
      id: 1,
      title: 'MUSIC.BANNER_FAV_TITLE',
      subtitle: 'MUSIC.BANNER_FAV_SUB',
      gradient: 'linear-gradient(135deg, #1a0533 0%, #4c1d95 45%, #7c3aed 100%)',
      view: 'liked'
    },
    {
      id: 2,
      title: 'MUSIC.BANNER_RECENT_TITLE',
      subtitle: 'MUSIC.BANNER_RECENT_SUB',
      gradient: 'linear-gradient(135deg, #0c1445 0%, #1e3a8a 45%, #3b82f6 100%)',
      view: 'history'
    },
    {
      id: 3,
      title: 'MUSIC.BANNER_LIB_TITLE',
      subtitle: 'MUSIC.BANNER_LIB_SUB',
      gradient: LibraryModeComponent.RANDOM_GRADIENTS[0],
    },
    {
      id: 4,
      title: 'MUSIC.BANNER_LIB_TITLE',
      subtitle: 'MUSIC.BANNER_LIB_SUB',
      gradient: LibraryModeComponent.RANDOM_GRADIENTS[1],
    },
    {
      id: 5,
      title: 'MUSIC.BANNER_LIB_TITLE',
      subtitle: 'MUSIC.BANNER_LIB_SUB',
      gradient: LibraryModeComponent.RANDOM_GRADIENTS[2],
    }
  ];
  activeBannerIndex = 0;
  private bannerInterval?: ReturnType<typeof setInterval>;
  private pendingAutoPlay = false;
  private preloadAudio = new Audio();

  // ── Mobile ────────────────────────────────────────────────────────────────
  mobileMenuOpen = false;
  mobileSearchOpen = false;
  detailsPanelOpen = false;
  detailsVisualizerBars: number[] = Array.from({ length: 28 }, () => 0.18);
  private detailsVizRaf?: number;

  toggleMobileMenu(): void { this.mobileMenuOpen = !this.mobileMenuOpen; }
  closeMobileMenu(): void  { this.mobileMenuOpen = false; }

  // ── Edit mode ─────────────────────────────────────────────────────────────
  editMode = false;
  activeMenuPath: string | null = null;

  dialog: {
    type: 'rename' | 'delete' | 'move' | 'metadata' | 'createFolder' | 'cover' | null;
    item: MusicMetadataDto | null;
    value: string;
    title: string;
    artist: string;
    album: string;
    year: string;
    loading: boolean;
    error: string;
  } = { type: null, item: null, value: '', title: '', artist: '', album: '', year: '', loading: false, error: '' };

  uploadState: {
    active: boolean;
    progress: number;
    status: 'idle' | 'uploading' | 'done' | 'error';
    results: { name: string; status: 'ok' | 'error'; message?: string }[];
    totalFiles: number;
  } = { active: false, progress: 0, status: 'idle', results: [], totalFiles: 0 };

  downloadingPaths = new Set<string>();

  copyModal: {
    open: boolean;
    track: MusicMetadataDto | null;
    copying: boolean;
    error: string;
    success: boolean;
  } = { open: false, track: null, copying: false, error: '', success: false };

  // ── YouTube → NAS panel ───────────────────────────────────────────────────
  ytPanel = false;
  ytQuery = '';
  ytSearching = false;
  ytResults: any[] = [];
  ytFormat = 'mp3';
  ytDownloadingIds = new Set<string>();

  // ── Visualizer ────────────────────────────────────────────────────────────
  vizActive = false;
  @ViewChild('vizCanvas') vizCanvas?: ElementRef<HTMLCanvasElement>;
  private vizRaf?: number;
  private vizPeaks: number[] = [];

  toggleViz(): void {
    this.vizActive = !this.vizActive;
    if (this.vizActive) this.startViz();
    else this.stopViz();
  }

  private startViz(): void {
    this.stopViz();
    this.drawViz();
  }

  private stopViz(): void {
    if (this.vizRaf) { cancelAnimationFrame(this.vizRaf); this.vizRaf = undefined; }
  }

  private drawViz(): void {
    const canvas = this.vizCanvas?.nativeElement;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const data = this.musicService.mainPlayer.getFrequencyData();
    const W = canvas.width;
    const H = canvas.height;
    const bins = data ? data.length : 0;
    const BAR_COUNT = Math.min(bins, 48);
    const gap = 2;
    const barW = Math.floor((W - gap * (BAR_COUNT - 1)) / BAR_COUNT);

    ctx.clearRect(0, 0, W, H);
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, W, H);

    if (!data) { this.vizRaf = requestAnimationFrame(() => this.drawViz()); return; }

    if (this.vizPeaks.length !== BAR_COUNT) this.vizPeaks = new Array(BAR_COUNT).fill(0);

    for (let i = 0; i < BAR_COUNT; i++) {
      const value = data[i] / 255;
      const barH = Math.max(2, Math.floor(value * H));
      const x = i * (barW + gap);

      // Gradient per-bar: green → yellow → red
      const grad = ctx.createLinearGradient(0, H, 0, H - barH);
      grad.addColorStop(0,    '#00e676');
      grad.addColorStop(0.5,  '#ffea00');
      grad.addColorStop(0.8,  '#ff6d00');
      grad.addColorStop(1,    '#d50000');
      ctx.fillStyle = grad;
      ctx.fillRect(x, H - barH, barW, barH);

      // Peak dot
      if (barH > this.vizPeaks[i]) this.vizPeaks[i] = barH;
      else this.vizPeaks[i] = Math.max(0, this.vizPeaks[i] - 1.5);

      if (this.vizPeaks[i] > 2) {
        ctx.fillStyle = '#fff';
        ctx.fillRect(x, H - this.vizPeaks[i] - 2, barW, 2);
      }
    }

    this.vizRaf = requestAnimationFrame(() => this.drawViz());
  }

  // ── Active yt-dlp downloads panel ────────────────────────────────────────
  ytJobs: any[] = [];
  private pollInterval?: ReturnType<typeof setInterval>;
  private completedJobIds = new Set<string>();

  // ── Cover scan ────────────────────────────────────────────────────────────
  coverScanActive = false;
  coverScanProgress = '';

  // ── Pagination ────────────────────────────────────────────────────────────
  private readonly PAGE_SIZE = 50;
  private currentPage = 0;
  totalTracks = 0;
  pageLoading = false;
  loadingPage = false; // dedup guard, también usado en template
  private loadingAllPages = false;
  private intersectionObserver?: IntersectionObserver;
  @ViewChild('tracksEndSentinel') tracksEndSentinel?: ElementRef;

  private uploadSub?: Subscription;
  private subs: Subscription[] = [];

  constructor(
    public musicService: MusicService,
    private nasService: NasService,
    private authService: AuthService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.detailsPanelOpen = this.readDetailsPanelPreference();
    this.loadFavoriteFolders();
    this.startBannerRotation();
    this.loadFavHistoryBanners();
    this.nasService.getPaths().subscribe(paths => {
      this.paths = paths;
      if (paths.length > 0) this.selectPath(paths[0].id);
      else this.setView('home');
    });
    this.loadRandomBanners();

    this.subs.push(this.musicService.mainPlayer.state$.subscribe(s => {
      const prev = this.state?.currentTrack?.path;
      this.state = s;
      if (s.currentTrack && s.currentTrack.path !== prev) {
        this.musicService.fetchCoverIfNeeded(s.currentTrack);
      }
    }));

    this.subs.push(this.musicService.shuffle$.subscribe(v => this.shuffle = v));
    this.subs.push(this.musicService.repeat$.subscribe(v => this.repeat = v));
    this.subs.push(this.musicService.queue$.subscribe(q => this.queueIndex = q.index));

    // Cuando llega una portada de iTunes, forzar re-render si afecta a un banner
    this.subs.push(this.musicService.coverReady$.subscribe(trackPath => {
      const affectsBanner = this.banners.some(b => b.track?.path === trackPath);
      if (affectsBanner) this.cdr.detectChanges();
    }));

    this.musicService.getFavorites().subscribe(favs => {
      this.likedItems = favs;
    });

    this.startDetailsVisualizerLoop();
  }

  ngAfterViewInit(): void {
    this.setupIntersectionObserver();
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    clearInterval(this.bannerInterval);
    this.stopPollJobs();
    this.stopViz();
    if (this.detailsVizRaf) {
      cancelAnimationFrame(this.detailsVizRaf);
      this.detailsVizRaf = undefined;
    }
    this.intersectionObserver?.disconnect();
    this.preloadAudio.src = '';
    this.preloadAudio.load();
  }

  private setupIntersectionObserver(): void {
    if (typeof IntersectionObserver === 'undefined') return;
    this.intersectionObserver = new IntersectionObserver(entries => {
      if (entries[0]?.isIntersecting) this.loadNextPage();
    }, { threshold: 0.1 });
    if (this.tracksEndSentinel) {
      this.intersectionObserver.observe(this.tracksEndSentinel.nativeElement);
    }
  }

  // ── Banner methods ────────────────────────────────────────────────────────

  startBannerRotation(): void {
    clearInterval(this.bannerInterval);
    this.bannerInterval = setInterval(() => {
      this.activeBannerIndex = (this.activeBannerIndex + 1) % this.banners.length;
      this.preloadActiveBanner();
    }, 5000);
  }

  goToBanner(index: number): void {
    this.activeBannerIndex = index;
    this.preloadActiveBanner();
    this.startBannerRotation();
  }

  nextBanner(): void {
    this.goToBanner((this.activeBannerIndex + 1) % this.banners.length);
  }

  prevBanner(): void {
    this.goToBanner((this.activeBannerIndex - 1 + this.banners.length) % this.banners.length);
  }

  onBannerClick(banner: NasBanner): void {
    if (banner.track && banner.pathId != null) {
      // Track already pre-buffered → instant play like DJ booth
      this.musicService.mainPlayer.load(banner.track, banner.pathId).then(() => {
        this.musicService.mainPlayer.play();
      });
      return;
    }
    // Fallback: navigate to view and auto-play first track
    this.pendingAutoPlay = true;
    if (banner.view) {
      this.setView(banner.view);
    } else if (banner.pathIndex !== undefined && this.paths.length > banner.pathIndex) {
      this.currentView = 'folder';
      this.selectedPathId = this.paths[banner.pathIndex].id;
      this.currentSubPath = banner.subPath || '';
      this.searchQuery = '';
      this.searchResults = null;
      this.load();
    } else {
      this.pendingAutoPlay = false;
    }
  }

  private preloadActiveBanner(): void {
    const banner = this.banners[this.activeBannerIndex];
    if (banner?.track && banner.pathId != null) {
      const url = this.musicService.getStreamUrl(banner.pathId, banner.track.path);
      this.preloadAudio.preload = 'auto';
      this.preloadAudio.src = url;
      this.preloadAudio.load();
    }
  }

  getBannerCoverUrl(banner: NasBanner): string {
    if (!banner.track || banner.pathId == null) return '';
    return this.musicService.getCoverUrlWithCache(banner.pathId, banner.track.path);
  }

  private loadFavHistoryBanners(): void {
    this.musicService.getFavorites().subscribe(favs => {
      if (!favs.length) return;
      const fav = favs[0];
      const track: MusicMetadataDto = {
        name: fav.title, path: fav.trackPath, title: fav.title, artist: fav.artist,
        album: fav.album, hasCover: false, directory: false, nasPathId: fav.nasPathId,
        duration: 0, size: 0, format: '', lastModified: '', bpm: 0
      };
      this.musicService.fetchCoverIfNeeded(track);
      this.banners = this.banners.map((b, i) => i === 0
        ? { ...b, title: fav.title || b.title, subtitle: fav.artist || b.subtitle, track, pathId: fav.nasPathId }
        : b);
      if (this.activeBannerIndex === 0) this.preloadActiveBanner();
    });

    this.musicService.getHistory(1).subscribe(hist => {
      if (!hist.length) return;
      const h = hist[0];
      const track: MusicMetadataDto = {
        name: h.title, path: h.trackPath, title: h.title, artist: h.artist,
        album: h.album, hasCover: false, directory: false, nasPathId: h.nasPathId,
        duration: h.durationSeconds || 0, size: 0, format: '', lastModified: '', bpm: 0
      };
      this.musicService.fetchCoverIfNeeded(track);
      this.banners = this.banners.map((b, i) => i === 1
        ? { ...b, title: h.title || b.title, subtitle: h.artist || b.subtitle, track, pathId: h.nasPathId }
        : b);
      if (this.activeBannerIndex === 1) this.preloadActiveBanner();
    });
  }

  private loadRandomBanners(): void {
    this.musicService.getRandomTracks(3).subscribe(tracks => {
      if (!tracks.length) return;
      const updated = [...this.banners];
      tracks.forEach((track, i) => {
        const slotIndex = 2 + i;
        if (slotIndex >= updated.length) return;
        const pathId = track.nasPathId;
        if (!pathId) return;
        track.nasPathId = pathId;
        this.musicService.fetchCoverIfNeeded(track);
        updated[slotIndex] = {
          ...updated[slotIndex],
          title: track.title || track.name || updated[slotIndex].title,
          subtitle: track.artist || updated[slotIndex].subtitle,
          gradient: LibraryModeComponent.RANDOM_GRADIENTS[i % LibraryModeComponent.RANDOM_GRADIENTS.length],
          track,
          pathId,
        };
      });
      this.banners = updated;
      if (this.activeBannerIndex >= 2) this.preloadActiveBanner();
    });
  }

  private checkPendingAutoPlay(): void {
    if (this.pendingAutoPlay && this.tracks.length > 0) {
      this.pendingAutoPlay = false;
      this.playTrack(this.tracks[0]);
    } else if (this.pendingAutoPlay && this.tracks.length === 0) {
      this.pendingAutoPlay = false;
    }
  }

  // ── Navigation ────────────────────────────────────────────────────────────

  setView(view: 'home' | 'liked' | 'history') {
    this.currentView = view;
    this.selectedPathId = null;
    this.currentSubPath = '';
    this.searchQuery = '';
    this.searchResults = null;
    this.items = [];
    this.load();
  }

  /** Navigate to the root of the first NAS path — same view the user sees on first load */
  goHome(): void {
    this.closeMobileMenu();
    if (this.paths.length > 0) {
      this.selectPath(this.paths[0].id);
    } else {
      this.setView('home');
    }
  }

  selectPath(id: number) {
    this.currentView = 'folder';
    this.selectedPathId = id;
    this.currentSubPath = '';
    this.searchQuery = '';
    this.searchResults = null;
    this.load();
  }

  load() {
    this.brokenCoverPaths.clear();
    this.currentPage = 0;
    this.totalTracks = 0;
    this.loadingPage = false;
    this.loadingAllPages = false;
    if (this.currentView === 'home') {
      this.musicService.getHistory(10).subscribe(h => {
        this.historyItems = h;
      });
      if (this.paths.length > 0) {
        this.musicService.browse(this.paths[0].id, '', 0, this.PAGE_SIZE).subscribe(result => {
          this.items = result.items;
          this.totalTracks = result.totalTracks;
          this.currentPage = 0;
          this.fetchCoversForVisible();
          this.checkPendingAutoPlay();
        });
      }
    } else if (this.currentView === 'liked') {
      this.musicService.getFavorites().subscribe(favs => {
        this.likedItems = favs;
        const sorted = [...favs];
        if (this.likedSortBy === 'title') {
          sorted.sort((a, b) => (a.title || '').localeCompare(b.title || ''));
        } else if (this.likedSortBy === 'artist') {
          sorted.sort((a, b) => (a.artist || '').localeCompare(b.artist || ''));
        }
        this.items = sorted.map(f => ({
          name: f.title,
          path: f.trackPath,
          title: f.title,
          artist: f.artist,
          album: f.album,
          hasCover: false,
          directory: false,
          nasPathId: f.nasPathId,
          duration: 0,
          size: 0,
          format: '',
          lastModified: '',
          bpm: 0
        } as MusicMetadataDto));
        this.totalTracks = this.items.length;
        this.items.slice(0, 30).forEach(t => this.musicService.fetchCoverIfNeeded(t));
        this.checkPendingAutoPlay();
      });
    } else if (this.currentView === 'history') {
      this.musicService.getHistory(50).subscribe(hist => {
        this.historyItems = hist;
        this.items = hist.map(h => ({
           name: h.title,
           path: h.trackPath,
           title: h.title,
           artist: h.artist,
           album: h.album,
           hasCover: false,
           directory: false,
           nasPathId: h.nasPathId,
           duration: h.durationSeconds,
           size: 0,
           format: ''
        } as MusicMetadataDto));
        this.totalTracks = this.items.length;
        this.items.slice(0, 30).forEach(t => this.musicService.fetchCoverIfNeeded(t));
        this.checkPendingAutoPlay();
      });
    } else if (this.currentView === 'folder' && this.selectedPathId !== null) {
      this.pageLoading = true;
      this.musicService.browse(this.selectedPathId, this.currentSubPath, 0, this.PAGE_SIZE).subscribe(result => {
        this.items = result.items;
        this.totalTracks = result.totalTracks;
        this.currentPage = 0;
        this.pageLoading = false;
        this.fetchCoversForVisible();
        this.checkPendingAutoPlay();
      });
    }
  }

  get allTracksLoaded(): boolean {
    return this.totalTracks > 0 && this.tracks.length >= this.totalTracks;
  }

  loadNextPage(): void {
    if (this.currentView !== 'folder') return;
    if (this.allTracksLoaded) return;
    if (this.loadingPage || this.pageLoading) return;
    if (this.selectedPathId === null) return;
    if (this.searchQuery.trim()) return; // búsqueda activa: no paginar

    this.loadingPage = true;
    const nextPage = this.currentPage + 1;
    this.musicService.browse(this.selectedPathId, this.currentSubPath, nextPage, this.PAGE_SIZE).subscribe({
      next: result => {
        this.items = [...this.items, ...result.items];
        this.totalTracks = result.totalTracks;
        this.currentPage = nextPage;
        this.loadingPage = false;
        // Buscar portadas solo para las pistas recién cargadas (no repetir las anteriores)
        result.items
          .filter((t: MusicMetadataDto) => !this.musicService.hasCoverToShow(t))
          .forEach((t: MusicMetadataDto) => this.musicService.fetchCoverIfNeeded(t));
      },
      error: () => { this.loadingPage = false; }
    });
  }

  // Carga todas las páginas restantes (usado cuando el usuario busca)
  private loadAllRemainingPages(): void {
    if (this.currentView !== 'folder') return;
    if (this.allTracksLoaded) return;
    if (this.loadingAllPages) return; // ya hay una cadena en curso, no lanzar otra
    if (this.selectedPathId === null) return;

    this.loadingAllPages = true;
    const startPage = this.currentPage + 1;

    const fetchNext = (page: number) => {
      this.musicService.browse(this.selectedPathId!, this.currentSubPath, page, this.PAGE_SIZE).subscribe({
        next: result => {
          // Deduplicar: solo añadir items cuyo path no esté ya en this.items
          const existingPaths = new Set(this.items.map(i => i.path));
          const newItems = result.items.filter((i: any) => !existingPaths.has(i.path));
          this.items = [...this.items, ...newItems];
          this.totalTracks = result.totalTracks;
          this.currentPage = page;
          if (this.tracks.length < this.totalTracks) {
            fetchNext(page + 1);
          } else {
            this.loadingAllPages = false;
          }
        },
        error: () => { this.loadingAllPages = false; }
      });
    };
    fetchNext(startPage);
  }

  navigate(item: MusicMetadataDto) {
    if (!item.directory) return;
    this.currentSubPath = item.path;
    this.searchQuery = '';
    this.searchResults = null;
    this.load();
  }

  goUp() {
    if (!this.currentSubPath || this.currentView !== 'folder') return;
    const parts = this.currentSubPath.split(/[/\\]/).filter(Boolean);
    parts.pop();
    this.currentSubPath = parts.join('/');
    this.searchQuery = '';
    this.searchResults = null;
    this.load();
  }

  get isRoot() { return !this.currentSubPath || this.currentView !== 'folder'; }

  get folders(): MusicMetadataDto[] { return this.items.filter(i => i.directory); }
  get tracks():  MusicMetadataDto[] { return this.items.filter(i => !i.directory); }

  get filteredFolders(): MusicMetadataDto[] {
    if (this.searchQuery.trim()) return [];
    return this.folders;
  }

  get filteredTracks(): MusicMetadataDto[] {
    if (this.searchResults !== null) return this.searchResults;
    return this.tracks;
  }

  onSearchChange(): void {
    clearTimeout(this.searchDebounce);
    if (!this.searchQuery.trim()) {
      this.searchResults = null;
      this.searchLoading = false;
      return;
    }
    this.searchLoading = true;
    this.searchDebounce = setTimeout(() => this.runSearch(), 400);
  }

  private runSearch(): void {
    if (!this.selectedPathId || !this.searchQuery.trim()) return;
    this.musicService.search(this.selectedPathId, this.currentSubPath || undefined, this.searchQuery.trim()).subscribe({
      next: results => {
        this.searchResults = results;
        this.searchLoading = false;
        results.filter(t => !this.musicService.hasCoverToShow(t))
               .forEach(t => this.musicService.fetchCoverIfNeeded(t));
      },
      error: () => { this.searchLoading = false; }
    });
  }

  get breadcrumbs(): string[] {
    if (!this.currentSubPath) return [];
    return this.currentSubPath.split(/[/\\]/).filter(Boolean);
  }

  get headerGradient(): string {
    const palettes = [
      'linear-gradient(180deg, #1a3a2a 0%, #121212 100%)',
      'linear-gradient(180deg, #2d1b69 0%, #121212 100%)',
      'linear-gradient(180deg, #4a1942 0%, #121212 100%)',
      'linear-gradient(180deg, #1a2a4a 0%, #121212 100%)',
      'linear-gradient(180deg, #3a2a10 0%, #121212 100%)',
    ];
    let idx = 0;
    if (this.currentView === 'liked') idx = 1;
    else if (this.currentView === 'history') idx = 2;
    else if (this.currentView === 'folder') idx = (this.selectedPathId ?? 0) % palettes.length;

    return palettes[idx];
  }

  getPathName(pathId: number | null): string {
    return this.paths.find(p => p.id === pathId)?.name ?? this.translate.instant('MUSIC.SIDEBAR_LIBRARY');
  }

  get currentFolderName(): string {
    if (this.currentView === 'home') return this.translate.instant('MUSIC.VIEW_HOME');
    if (this.currentView === 'liked') return this.translate.instant('MUSIC.VIEW_LIKED');
    if (this.currentView === 'history') return this.translate.instant('MUSIC.VIEW_HISTORY');
    
    if (!this.currentSubPath) {
      return this.paths.find(p => p.id === this.selectedPathId)?.name ?? this.translate.instant('MUSIC.SIDEBAR_LIBRARY');
    }
    const parts = this.currentSubPath.split(/[/\\]/).filter(Boolean);
    return parts[parts.length - 1] || this.translate.instant('MUSIC.SIDEBAR_LIBRARY');
  }

  sortLikedBy(sort: 'date' | 'title' | 'artist') {
    this.likedSortBy = sort;
    if (this.currentView === 'liked') this.load();
  }

  // ── Playback ──────────────────────────────────────────────────────────────

  playTrack(track: MusicMetadataDto) {
    const pid = track.nasPathId ?? this.selectedPathId;
    if (pid === null || pid === undefined) return;
    const idx = this.tracks.findIndex(t => t.path === track.path);
    this.musicService.setQueue(pid, this.tracks, Math.max(0, idx));
  }

  isCurrentTrack(track: MusicMetadataDto): boolean {
    return this.state?.currentTrack?.path === track.path;
  }


  // ── Cover art & Interactions ──────────────────────────────────────────────

  coverUrl(track: MusicMetadataDto): string {
    const pid = track.nasPathId ?? ((this.state?.currentTrack?.path === track.path && this.state?.pathId)
                ? this.state.pathId
                : this.selectedPathId);
    if (pid === null || pid === undefined) return '';
    return this.musicService.getCoverUrlWithCache(pid, track.path);
  }
  
  folderCoverUrl(folder: MusicMetadataDto): string {
    if (this.selectedPathId == null) return '';
    return this.musicService.getFolderCoverUrl(this.selectedPathId, folder.path);
  }

  folderCoverError(event: Event) {
    // On 404, hide the broken image and let CSS fallback show
    const img = event.target as HTMLImageElement;
    if (img) img.style.display = 'none';
  }

  hasCoverToShow(track: MusicMetadataDto): boolean {
    if (this.brokenCoverPaths.has(track.path)) return false;
    return this.musicService.hasCoverToShow(track) || this.currentView !== 'folder';
  }

  onTrackCoverError(e: Event, track: MusicMetadataDto): void {
    const img = e.target as HTMLImageElement;
    if (img) img.style.display = 'none';
    this.brokenCoverPaths.add(track.path);
    // La portada falló (corrupta, formato no soportado, URL caducada, etc.)
    // Limpiar la cache para que fetchCoverIfNeeded intente buscar de nuevo
    track.hasCover = false;
    this.musicService.coverOverrideMap.delete(track.path);
    this.musicService.fetchCoverIfNeeded(track);
  }

  playerHasCover(): boolean {
    const t = this.state?.currentTrack;
    return !!t && this.musicService.hasCoverToShow(t);
  }

  playerCoverUrl(): string {
    const t = this.state?.currentTrack;
    if (!t || !this.state?.pathId) return '';
    return this.musicService.getCoverUrlWithCache(this.state.pathId, t.path);
  }

  toggleDetailsPanel(): void {
    this.detailsPanelOpen = !this.detailsPanelOpen;
    this.persistDetailsPanelPreference();
  }

  closeDetailsPanel(): void {
    if (!this.detailsPanelOpen) return;
    this.detailsPanelOpen = false;
    this.persistDetailsPanelPreference();
  }

  currentTrackTitle(): string {
    return this.state?.currentTrack?.title || this.state?.currentTrack?.name || 'EverLoad';
  }

  currentTrackArtist(): string {
    return this.state?.currentTrack?.artist || this.translate.instant('MUSIC.UNKNOWN_ARTIST');
  }

  currentTrackAlbum(): string {
    return this.state?.currentTrack?.album || this.currentFolderName;
  }

  currentTrackFormat(): string {
    const format = this.state?.currentTrack?.format;
    return format ? format.toUpperCase() : 'AUDIO';
  }

  currentTrackYear(): string {
    const lastModified = this.state?.currentTrack?.lastModified;
    if (!lastModified) return '';
    const year = new Date(lastModified).getFullYear();
    return Number.isFinite(year) ? String(year) : '';
  }

  progressPct(): number {
    const duration = this.state?.duration ?? 0;
    if (!duration) return 0;
    return Math.max(0, Math.min(100, ((this.state?.currentTime ?? 0) / duration) * 100));
  }

  detailsVisualizerStyle(i: number): { [key: string]: string } {
    const level = this.detailsVisualizerBars[i] ?? 0.18;
    return {
      height: `${Math.max(14, Math.round(level * 100))}%`,
      opacity: `${Math.min(1, 0.35 + level * 0.9)}`,
      animationDelay: `${(i % 7) * 0.08}s`
    };
  }

  private readDetailsPanelPreference(): boolean {
    if (typeof window === 'undefined') return false;
    return localStorage.getItem(LibraryModeComponent.DETAILS_PANEL_STORAGE_KEY) === '1';
  }

  private persistDetailsPanelPreference(): void {
    if (typeof window === 'undefined') return;
    localStorage.setItem(LibraryModeComponent.DETAILS_PANEL_STORAGE_KEY, this.detailsPanelOpen ? '1' : '0');
  }

  private startDetailsVisualizerLoop(): void {
    if (typeof window === 'undefined') return;
    const tick = () => {
      const liveData = this.musicService.mainPlayer.getFrequencyData();
      if (this.state?.playing && liveData && liveData.length > 0) {
        this.detailsVisualizerBars = this.sampleDetailsVisualizerBars(liveData, this.detailsVisualizerBars.length);
      } else {
        const now = performance.now() / 1000;
        this.detailsVisualizerBars = this.detailsVisualizerBars.map((_, index) => {
          const wave =
            0.26 +
            Math.sin(now * 2.8 + index * 0.42) * 0.12 +
            Math.sin(now * 5.2 + index * 0.21) * 0.06;
          return Math.max(0.14, Math.min(0.58, wave));
        });
      }
      this.detailsVizRaf = requestAnimationFrame(tick);
    };
    tick();
  }

  private sampleDetailsVisualizerBars(data: Uint8Array, count: number): number[] {
    const chunk = Math.max(1, Math.floor(data.length / count));
    const bars: number[] = [];
    for (let i = 0; i < count; i++) {
      const start = i * chunk;
      const end = Math.min(data.length, start + chunk);
      let sum = 0;
      for (let j = start; j < end; j++) sum += data[j];
      const avg = end > start ? sum / (end - start) : 0;
      const normalized = avg / 255;
      bars.push(Math.max(0.12, Math.min(1, normalized * 1.45 + 0.08)));
    }
    return bars;
  }

  toggleLike(e: Event, track: MusicMetadataDto) {
    e.stopPropagation();
    const pid = track.nasPathId ?? this.selectedPathId;
    if (pid === null || pid === undefined) return;

    // Optimistic update: toggle immediately for instant visual feedback
    const wasLiked = this.isLiked(track);
    if (wasLiked) {
      this.likedItems = this.likedItems.filter(f => !(f.trackPath === track.path && f.nasPathId === pid));
    } else {
      this.likedItems = [...this.likedItems, { trackPath: track.path, nasPathId: pid }];
    }

    this.musicService.toggleFavorite(track.path, track.title || track.name, track.artist || '', track.album || '', pid)
      .subscribe({
        next: (res: any) => {
          // Sync final state with server response
          const nowLiked = this.isLiked(track);
          if (res.isFavorite && !nowLiked) {
            this.likedItems = [...this.likedItems, { trackPath: track.path, nasPathId: pid }];
          } else if (!res.isFavorite && nowLiked) {
            this.likedItems = this.likedItems.filter(f => !(f.trackPath === track.path && f.nasPathId === pid));
          }
          if (this.currentView === 'liked') this.load();
        },
        error: () => {
          // Rollback optimistic update on error
          if (wasLiked) {
            this.likedItems = [...this.likedItems, { trackPath: track.path, nasPathId: pid }];
          } else {
            this.likedItems = this.likedItems.filter(f => !(f.trackPath === track.path && f.nasPathId === pid));
          }
        }
      });
  }

  isLiked(track: MusicMetadataDto): boolean {
    const pid = track.nasPathId ?? this.selectedPathId;
    if (pid === null || pid === undefined) return false;
    return this.likedItems.some(f => f.trackPath === track.path && Number(f.nasPathId) === Number(pid));
  }

  private fetchCoversForVisible() {
    this.tracks.filter(t => !this.musicService.hasCoverToShow(t)).slice(0, 60).forEach(t => this.musicService.fetchCoverIfNeeded(t));
  }

  // ── Cover scan ────────────────────────────────────────────────────────────

  async scanCovers(): Promise<void> {
    const tracksToScan = this.filteredTracks.filter(t => !this.musicService.hasCoverToShow(t));
    if (tracksToScan.length === 0) {
      // All tracks already have covers — force re-scan by fetching all
      const all = this.filteredTracks;
      if (all.length === 0) return;
      this.coverScanActive = true;
      for (let i = 0; i < all.length; i++) {
        this.coverScanProgress = `${i + 1}/${all.length}`;
        this.musicService.fetchCoverIfNeeded(all[i]);
        await this.delay(400);
      }
      this.coverScanActive = false;
      this.coverScanProgress = '';
      return;
    }

    this.coverScanActive = true;
    for (let i = 0; i < tracksToScan.length; i++) {
      this.coverScanProgress = `${i + 1}/${tracksToScan.length}`;
      this.musicService.fetchCoverIfNeeded(tracksToScan[i]);
      await this.delay(400);
    }
    this.coverScanActive = false;
    this.coverScanProgress = '';
  }

  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  // ── Edit mode ─────────────────────────────────────────────────────────────

  get canEdit(): boolean {
    return this.authService.hasNasAccess();
  }

  toggleEditMode(): void {
    this.editMode = !this.editMode;
    this.activeMenuPath = null;
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    this.activeMenuPath = null;
  }

  toggleMenu(e: Event, item: MusicMetadataDto): void {
    e.stopPropagation();
    this.activeMenuPath = this.activeMenuPath === item.path ? null : item.path;
  }

  openRename(e: Event, item: MusicMetadataDto): void {
    e.stopPropagation();
    this.activeMenuPath = null;
    // For files, strip extension from the display name so the user edits only the stem;
    // the backend always preserves the original extension automatically.
    let displayName = item.name;
    if (!item.directory) {
      const dot = item.name.lastIndexOf('.');
      displayName = dot > 0 ? item.name.substring(0, dot) : item.name;
    }
    this.dialog = { type: 'rename', item, value: displayName, title: '', artist: '', album: '', year: '', loading: false, error: '' };
  }

  openDelete(e: Event, item: MusicMetadataDto): void {
    e.stopPropagation();
    this.activeMenuPath = null;
    this.dialog = { type: 'delete', item, value: '', title: '', artist: '', album: '', year: '', loading: false, error: '' };
  }

  openMove(e: Event, item: MusicMetadataDto): void {
    e.stopPropagation();
    this.activeMenuPath = null;
    this.dialog = { type: 'move', item, value: this.currentSubPath, title: '', artist: '', album: '', year: '', loading: false, error: '' };
  }

  openMetadata(e: Event, track: MusicMetadataDto): void {
    e.stopPropagation();
    this.activeMenuPath = null;
    this.dialog = { type: 'metadata', item: track, value: '', title: track.title || track.name, artist: track.artist || '', album: (track as any).album || '', year: (track as any).year || '', loading: false, error: '' };
  }

  openCreateFolder(): void {
    this.dialog = { type: 'createFolder', item: null, value: '', title: '', artist: '', album: '', year: '', loading: false, error: '' };
  }

  openCover(e: Event, folder: MusicMetadataDto): void {
    e.stopPropagation();
    this.activeMenuPath = null;
    this.dialog = { type: 'cover', item: folder, value: '', title: '', artist: '', album: '', year: '', loading: false, error: '' };
  }

  closeDialog(): void {
    this.dialog = { type: null, item: null, value: '', title: '', artist: '', album: '', year: '', loading: false, error: '' };
  }

  // ── AcoustID fingerprint ──────────────────────────────────────────────────

  fingerprintingPaths = new Set<string>();
  fingerprintResults = new Map<string, { found: boolean; msg: string }>();

  fingerprintTrack(e: Event, track: MusicMetadataDto): void {
    e.stopPropagation();
    this.activeMenuPath = null;
    const pid = this.selectedPathId!;
    this.fingerprintingPaths.add(track.path);
    this.musicService.fingerprintTrack(pid, track.path).subscribe({
      next: (r: any) => {
        this.fingerprintingPaths.delete(track.path);
        if (r.found) {
          if (r.title)  track.title  = r.title;
          if (r.artist) track.artist = r.artist;
          if (r.album)  (track as any).album = r.album;
          if (r.coverEmbedded) {
            track.hasCover = true;
            this.musicService.coverOverrideMap?.delete(track.path);
          }
          const parts = [];
          if (r.tagsUpdated)    parts.push(this.translate.instant('NAS.FINGERPRINT_UPDATED'));
          if (r.coverEmbedded)  parts.push(this.translate.instant('NAS.FINGERPRINT_COVER_ADDED'));
          const msg = parts.length ? `✅ ${parts.join(', ')}` : this.translate.instant('NAS.FINGERPRINT_IDENTIFIED');
          this.fingerprintResults.set(track.path, { found: true, msg });
        } else {
          this.fingerprintResults.set(track.path, { found: false, msg: r.error || this.translate.instant('NAS.FINGERPRINT_NOT_FOUND') });
        }
        setTimeout(() => this.fingerprintResults.delete(track.path), 4000);
      },
      error: () => {
        this.fingerprintingPaths.delete(track.path);
        this.fingerprintResults.set(track.path, { found: false, msg: '❌ ' + this.translate.instant('CHAT.ERROR') });
        setTimeout(() => this.fingerprintResults.delete(track.path), 4000);
      }
    });
  }

  confirmRename(): void {
    const item = this.dialog.item!;
    const pid = (item.nasPathId != null ? item.nasPathId : this.selectedPathId)!;
    if (!this.dialog.value.trim()) return;
    this.dialog.loading = true;
    this.nasService.rename(pid, item.path, this.dialog.value.trim()).subscribe({
      next: () => { this.closeDialog(); this.load(); },
      error: (err: any) => { this.dialog.loading = false; this.dialog.error = err.error?.error || this.translate.instant('NAS.ERROR_RENAME'); }
    });
  }

  confirmDelete(): void {
    const item = this.dialog.item!;
    const pid = (item.nasPathId != null ? item.nasPathId : this.selectedPathId)!;
    this.dialog.loading = true;
    this.nasService.deleteFile(pid, item.path).subscribe({
      next: () => { this.closeDialog(); this.load(); },
      error: (err: any) => { this.dialog.loading = false; this.dialog.error = err.error?.error || this.translate.instant('NAS.ERROR_DELETE'); }
    });
  }

  confirmMove(): void {
    const item = this.dialog.item!;
    const pid = (item.nasPathId != null ? item.nasPathId : this.selectedPathId)!;
    this.dialog.loading = true;
    this.nasService.move(pid, item.path, this.dialog.value.trim()).subscribe({
      next: () => { this.closeDialog(); this.load(); },
      error: (err: any) => { this.dialog.loading = false; this.dialog.error = err.error?.error || this.translate.instant('NAS.ERROR_MOVE'); }
    });
  }

  confirmMetadata(): void {
    const track = this.dialog.item!;
    const pid = (track.nasPathId != null ? track.nasPathId : this.selectedPathId)!;
    this.dialog.loading = true;
    this.nasService.updateMetadata(pid, track.path, this.dialog.title, this.dialog.artist, this.dialog.album, this.dialog.year).subscribe({
      next: () => { this.closeDialog(); this.load(); },
      error: (err: any) => { this.dialog.loading = false; this.dialog.error = err.error?.error || this.translate.instant('NAS.ERROR_METADATA'); }
    });
  }

  confirmCreateFolder(): void {
    if (!this.selectedPathId || !this.dialog.value.trim()) return;
    this.dialog.loading = true;
    this.nasService.mkdir(this.selectedPathId, this.dialog.value.trim(), this.currentSubPath).subscribe({
      next: () => { this.closeDialog(); this.load(); },
      error: (err: any) => { this.dialog.loading = false; this.dialog.error = err.error?.error || this.translate.instant('NAS.ERROR_CREATE_FOLDER'); }
    });
  }

  onCoverFileSelected(e: Event): void {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !this.dialog.item) return;
    const pid = (this.dialog.item.nasPathId != null ? this.dialog.item.nasPathId : this.selectedPathId)!;
    const folderPath = this.dialog.item.path;
    this.dialog.loading = true;
    this.nasService.uploadFolderCover(pid, folderPath, file).subscribe({
      next: () => {
        this.musicService.invalidateFolderCover(pid, folderPath);
        this.closeDialog();
        this.load();
      },
      error: (err: any) => { this.dialog.loading = false; this.dialog.error = err.error?.error || this.translate.instant('NAS.ERROR_UPLOAD'); }
    });
  }

  // ── Favorite folders ─────────────────────────────────────────────────────

  loadFavoriteFolders(): void {
    try {
      const stored = localStorage.getItem('nas_fav_folders');
      this.favoriteFolders = stored ? JSON.parse(stored) : [];
    } catch { this.favoriteFolders = []; }
  }

  saveFavoriteFolders(): void {
    localStorage.setItem('nas_fav_folders', JSON.stringify(this.favoriteFolders));
  }

  isFolderFav(folder: MusicMetadataDto): boolean {
    const pid = this.selectedPathId;
    return this.favoriteFolders.some(f => f.pathId === pid && f.subPath === folder.path);
  }

  toggleFolderFav(e: Event, folder: MusicMetadataDto): void {
    e.stopPropagation();
    const pid = this.selectedPathId;
    if (pid === null) return;
    if (this.isFolderFav(folder)) {
      this.favoriteFolders = this.favoriteFolders.filter(f => !(f.pathId === pid && f.subPath === folder.path));
    } else {
      this.favoriteFolders = [...this.favoriteFolders, { pathId: pid, subPath: folder.path, name: folder.name }];
    }
    this.saveFavoriteFolders();
  }

  navigateToFavFolder(fav: { pathId: number; subPath: string; name: string }): void {
    this.currentView = 'folder';
    this.selectedPathId = fav.pathId;
    this.currentSubPath = fav.subPath;
    this.searchQuery = '';
    this.searchResults = null;
    this.closeMobileMenu();
    this.load();
  }

  favFolderCoverUrl(fav: { pathId: number; subPath: string }): string {
    return this.musicService.getFolderCoverUrl(fav.pathId, fav.subPath);
  }

  folderFallbackGradient(folder: MusicMetadataDto): string {
    const gradients = [
      'linear-gradient(135deg, #1a3a2a 0%, #2d5a3d 100%)',
      'linear-gradient(135deg, #2d1b69 0%, #4a2f8f 100%)',
      'linear-gradient(135deg, #4a1942 0%, #7b2960 100%)',
      'linear-gradient(135deg, #1a2a4a 0%, #2d4a7a 100%)',
      'linear-gradient(135deg, #3a2a10 0%, #6b4a1a 100%)',
      'linear-gradient(135deg, #1a3a3a 0%, #2d6060 100%)',
      'linear-gradient(135deg, #3a1a1a 0%, #6b2d2d 100%)',
      'linear-gradient(135deg, #2a3a1a 0%, #4a6b2d 100%)',
      'linear-gradient(135deg, #3a1a3a 0%, #6b2d6b 100%)',
      'linear-gradient(135deg, #1a1a3a 0%, #2d2d6b 100%)',
    ];
    let hash = 0;
    for (let i = 0; i < folder.name.length; i++) {
      hash = ((hash << 5) - hash) + folder.name.charCodeAt(i);
      hash |= 0;
    }
    return gradients[Math.abs(hash) % gradients.length];
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  fmt(seconds: number): string {
    if (!seconds || seconds <= 0) return '—';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  }

  fmtClass(format: string): string {
    return (format || '').toLowerCase();
  }


  repeatIcon(): string {
    if (this.repeat === 'one') return 'repeat1';
    if (this.repeat === 'all') return 'repeatAll';
    return 'none';
  }

  // ── Upload ────────────────────────────────────────────────────────────────

  onUploadFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files || []);
    input.value = '';
    if (!files.length || this.selectedPathId === null) return;
    this.startUpload(files, undefined);
  }

  onUploadFolderSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files || []);
    input.value = '';
    if (!files.length || this.selectedPathId === null) return;
    // webkitRelativePath gives e.g. "Beatles/Abbey Road/01-Come Together.mp3"
    const relativePaths = files.map(f => (f as any).webkitRelativePath || f.name);
    this.startUpload(files, relativePaths);
  }

  private startUpload(files: File[], relativePaths?: string[]): void {
    this.uploadState = { active: true, progress: 0, status: 'uploading', results: [], totalFiles: files.length };

    this.uploadSub = this.nasService.uploadFiles(
      this.selectedPathId!, this.currentSubPath || undefined, files, relativePaths
    ).subscribe({
      next: (ev: any) => {
        if (ev.type === HttpEventType.UploadProgress && ev.total) {
          this.uploadState.progress = Math.round(100 * ev.loaded / ev.total);
        } else if (ev.type === HttpEventType.Response) {
          const results = ev.body as any[];
          this.uploadState.status = results.some((r: any) => r.status === 'error') ? 'error' : 'done';
          this.uploadState.results = results;
          this.uploadState.progress = 100;
          this.load();
        }
      },
      error: (err: any) => {
        this.uploadState.status = 'error';
        this.uploadState.results = [{ name: 'Upload', status: 'error', message: err.error?.error || this.translate.instant('NAS.ERROR_UPLOAD') }];
      }
    });
  }

  cancelUpload(): void {
    this.uploadSub?.unsubscribe();
    this.uploadState = { active: false, progress: 0, status: 'idle', results: [], totalFiles: 0 };
  }

  closeUploadPanel(): void {
    this.uploadState = { active: false, progress: 0, status: 'idle', results: [], totalFiles: 0 };
  }

  // ── Download ──────────────────────────────────────────────────────────────

  openCopyModal(e: Event, track: MusicMetadataDto): void {
    e.stopPropagation();
    this.copyModal = { open: true, track, copying: false, error: '', success: false };
  }

  closeCopyModal(): void {
    this.copyModal = { open: false, track: null, copying: false, error: '', success: false };
  }

  onCopyDestinationSelected(dest: { pathId: number; subPath: string }): void {
    if (!this.copyModal.track) return;
    const track = this.copyModal.track;
    const pid = track.nasPathId ?? this.selectedPathId;
    if (pid === null) return;
    this.copyModal.copying = true;
    this.copyModal.error = '';
    this.nasService.copyFile(pid, track.path, dest.pathId, dest.subPath).subscribe({
      next: () => {
        this.copyModal.copying = false;
        this.copyModal.success = true;
        setTimeout(() => this.closeCopyModal(), 1500);
      },
      error: (err) => {
        this.copyModal.copying = false;
        this.copyModal.error = err.error?.error || this.translate.instant('NAS.ERROR_COPY');
      }
    });
  }

  downloadTrack(e: Event, track: MusicMetadataDto): void {
    e.stopPropagation();
    const pid = track.nasPathId ?? this.selectedPathId;
    if (pid === null) return;
    this.downloadingPaths.add(track.path);
    this.nasService.downloadFile(pid, track.path).subscribe({
      next: (blob) => {
        this.triggerBlobDownload(blob, track.name);
        this.downloadingPaths.delete(track.path);
      },
      error: () => this.downloadingPaths.delete(track.path)
    });
  }

  downloadFolder(e: Event, folder: MusicMetadataDto): void {
    e.stopPropagation();
    if (this.selectedPathId === null) return;
    this.downloadingPaths.add(folder.path);
    this.nasService.downloadFolderZip(this.selectedPathId, folder.path).subscribe({
      next: (blob) => {
        this.triggerBlobDownload(blob, folder.name + '.zip');
        this.downloadingPaths.delete(folder.path);
      },
      error: () => this.downloadingPaths.delete(folder.path)
    });
  }

  isDownloading(path: string): boolean {
    return this.downloadingPaths.has(path);
  }

  // ── YouTube → NAS ─────────────────────────────────────────────────────────

  toggleYtPanel(): void {
    this.ytPanel = !this.ytPanel;
    if (!this.ytPanel) { this.ytResults = []; this.ytQuery = ''; }
  }

  searchYouTube(): void {
    if (!this.ytQuery.trim()) return;
    this.ytSearching = true;
    this.ytResults = [];
    this.musicService.searchYouTube(this.ytQuery.trim(), 10).subscribe({
      next: (res: any) => { this.ytResults = res?.items || []; this.ytSearching = false; },
      error: () => { this.ytSearching = false; }
    });
  }

  downloadYtToNas(video: any): void {
    if (!this.selectedPathId) return;
    const vid = video.id?.videoId || video.id;
    const title = video.snippet?.title || vid;
    this.ytDownloadingIds.add(vid);
    this.musicService.ytDlpQueue(vid, title, this.selectedPathId, this.currentSubPath, this.ytFormat).subscribe({
      next: () => {
        this.ytDownloadingIds.delete(vid);
        this.startPollJobs();
      },
      error: () => this.ytDownloadingIds.delete(vid)
    });
  }

  isYtDownloading(video: any): boolean {
    return this.ytDownloadingIds.has(video.id?.videoId || video.id);
  }

  ytThumbnail(video: any): string {
    const id = video.id?.videoId || video.id;
    return `https://img.youtube.com/vi/${id}/mqdefault.jpg`;
  }

  // ── Downloads polling ─────────────────────────────────────────────────────

  startPollJobs(): void {
    if (this.pollInterval) return;
    this.pollInterval = setInterval(() => this.pollJobs(), 2500);
    this.pollJobs();
  }

  stopPollJobs(): void {
    clearInterval(this.pollInterval);
    this.pollInterval = undefined;
  }

  private pollJobs(): void {
    this.musicService.ytDlpActiveJobs().subscribe({
      next: (jobs: any[]) => {
        this.ytJobs = jobs;
        const hasActive = jobs.some(j => j.status === 'QUEUED' || j.status === 'RUNNING');
        if (!hasActive) this.stopPollJobs();
        // Auto-refresh folder when a new job finishes in current location
        jobs.filter(j => j.status === 'DONE' && !this.completedJobIds.has(j.jobId)).forEach(j => {
          this.completedJobIds.add(j.jobId);
          if (j.nasPathId === this.selectedPathId && (j.subPath || '') === (this.currentSubPath || '')) {
            this.load();
          }
        });
      },
      error: () => this.stopPollJobs()
    });
  }

  closeJobsPanel(): void {
    this.ytJobs = this.ytJobs.filter(j => j.status === 'RUNNING' || j.status === 'QUEUED');
    if (!this.ytJobs.length) this.stopPollJobs();
  }

  jobStatusLabel(job: any): string {
    if (job.status === 'QUEUED') return this.translate.instant('QUEUE.STATUS_PENDING');
    if (job.status === 'RUNNING') return `${job.progress}%`;
    if (job.status === 'DONE') return this.translate.instant('QUEUE.STATUS_DONE') + ' ✓';
    return this.translate.instant('CHAT.ERROR');
  }

  private triggerBlobDownload(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.click();
    URL.revokeObjectURL(url);
  }
}
