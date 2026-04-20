import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { HttpEventType } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { NasPath, NasService } from '../../../services/nas.service';
import { MusicMetadataDto, MusicService, PlayerState } from '../../../services/music.service';
import { AuthService } from '../../../services/auth.service';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-library-mode',
  templateUrl: './library-mode.component.html',
  styleUrls: ['./library-mode.component.css']
})
export class LibraryModeComponent implements OnInit, OnDestroy {

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
  likedSortBy: 'date' | 'title' | 'artist' = 'date';

  // ── Mobile ────────────────────────────────────────────────────────────────
  mobileMenuOpen = false;
  mobileSearchOpen = false;

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
    loading: boolean;
    error: string;
  } = { type: null, item: null, value: '', title: '', artist: '', loading: false, error: '' };

  uploadState: {
    active: boolean;
    progress: number;
    status: 'idle' | 'uploading' | 'done' | 'error';
    results: { name: string; status: 'ok' | 'error'; message?: string }[];
    totalFiles: number;
  } = { active: false, progress: 0, status: 'idle', results: [], totalFiles: 0 };

  downloadingPaths = new Set<string>();

  private uploadSub?: Subscription;
  private subs: Subscription[] = [];

  constructor(
    public musicService: MusicService,
    private nasService: NasService,
    private authService: AuthService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.loadFavoriteFolders();
    this.nasService.getPaths().subscribe(paths => {
      this.paths = paths;
      if (paths.length > 0) this.selectPath(paths[0].id);
      else this.setView('home');
    });

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

    this.musicService.getFavorites().subscribe(favs => {
      this.likedItems = favs;
    });
  }

  ngOnDestroy(): void { this.subs.forEach(s => s.unsubscribe()); }

  // ── Navigation ────────────────────────────────────────────────────────────

  setView(view: 'home' | 'liked' | 'history') {
    this.currentView = view;
    this.selectedPathId = null;
    this.currentSubPath = '';
    this.searchQuery = '';
    this.items = [];
    this.load();
  }

  selectPath(id: number) {
    this.currentView = 'folder';
    this.selectedPathId = id;
    this.currentSubPath = '';
    this.searchQuery = '';
    this.load();
  }

  load() {
    this.brokenCoverPaths.clear();
    if (this.currentView === 'home') {
      this.musicService.getHistory(10).subscribe(h => {
        this.historyItems = h;
      });
      // Top folders could be local folders in path 0
      if (this.paths.length > 0) {
        this.musicService.browse(this.paths[0].id, '').subscribe(homeItems => {
          this.items = homeItems.filter(i => i.directory).slice(0, 8);
        });
      }
    } else if (this.currentView === 'liked') {
      this.musicService.getFavorites().subscribe(favs => {
        this.likedItems = favs;
        // Sort liked items
        const sorted = [...favs];
        if (this.likedSortBy === 'title') {
          sorted.sort((a, b) => (a.title || '').localeCompare(b.title || ''));
        } else if (this.likedSortBy === 'artist') {
          sorted.sort((a, b) => (a.artist || '').localeCompare(b.artist || ''));
        }
        // 'date' is default sort from backend (DESC createdAt)
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
        // Buscar portadas de iTunes para los ítems visibles
        this.items.slice(0, 30).forEach(t => this.musicService.fetchCoverIfNeeded(t));
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
        // Buscar portadas de iTunes para los ítems visibles
        this.items.slice(0, 30).forEach(t => this.musicService.fetchCoverIfNeeded(t));
      });
    } else if (this.currentView === 'folder' && this.selectedPathId !== null) {
      this.musicService.browse(this.selectedPathId, this.currentSubPath).subscribe(items => {
        this.items = items;
        this.fetchCoversForVisible();
      });
    }
  }

  navigate(item: MusicMetadataDto) {
    if (!item.directory) return;
    this.currentSubPath = item.path;
    this.load();
  }

  goUp() {
    if (!this.currentSubPath || this.currentView !== 'folder') return;
    const parts = this.currentSubPath.split(/[/\\]/).filter(Boolean);
    parts.pop();
    this.currentSubPath = parts.join('/');
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
    if (!this.searchQuery.trim()) return this.tracks;
    const q = this.searchQuery.trim().toLowerCase();
    return this.tracks.filter(t =>
      (t.title  || t.name  || '').toLowerCase().includes(q) ||
      (t.artist || '').toLowerCase().includes(q) ||
      (t.album  || '').toLowerCase().includes(q)
    );
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
    return this.paths.find(p => p.id === pathId)?.name ?? 'Biblioteca';
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
    this.tracks.filter(t => !this.musicService.hasCoverToShow(t)).slice(0, 30).forEach(t => this.musicService.fetchCoverIfNeeded(t));
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
    this.dialog = { type: 'rename', item, value: displayName, title: '', artist: '', loading: false, error: '' };
  }

  openDelete(e: Event, item: MusicMetadataDto): void {
    e.stopPropagation();
    this.activeMenuPath = null;
    this.dialog = { type: 'delete', item, value: '', title: '', artist: '', loading: false, error: '' };
  }

  openMove(e: Event, item: MusicMetadataDto): void {
    e.stopPropagation();
    this.activeMenuPath = null;
    this.dialog = { type: 'move', item, value: this.currentSubPath, title: '', artist: '', loading: false, error: '' };
  }

  openMetadata(e: Event, track: MusicMetadataDto): void {
    e.stopPropagation();
    this.activeMenuPath = null;
    this.dialog = { type: 'metadata', item: track, value: '', title: track.title || track.name, artist: track.artist || '', loading: false, error: '' };
  }

  openCreateFolder(): void {
    this.dialog = { type: 'createFolder', item: null, value: '', title: '', artist: '', loading: false, error: '' };
  }

  openCover(e: Event, folder: MusicMetadataDto): void {
    e.stopPropagation();
    this.activeMenuPath = null;
    this.dialog = { type: 'cover', item: folder, value: '', title: '', artist: '', loading: false, error: '' };
  }

  closeDialog(): void {
    this.dialog = { type: null, item: null, value: '', title: '', artist: '', loading: false, error: '' };
  }

  confirmRename(): void {
    const item = this.dialog.item!;
    const pid = (item.nasPathId != null ? item.nasPathId : this.selectedPathId)!;
    if (!this.dialog.value.trim()) return;
    this.dialog.loading = true;
    this.nasService.rename(pid, item.path, this.dialog.value.trim()).subscribe({
      next: () => { this.closeDialog(); this.load(); },
      error: (err: any) => { this.dialog.loading = false; this.dialog.error = err.error?.error || 'Error al renombrar'; }
    });
  }

  confirmDelete(): void {
    const item = this.dialog.item!;
    const pid = (item.nasPathId != null ? item.nasPathId : this.selectedPathId)!;
    this.dialog.loading = true;
    this.nasService.deleteFile(pid, item.path).subscribe({
      next: () => { this.closeDialog(); this.load(); },
      error: (err: any) => { this.dialog.loading = false; this.dialog.error = err.error?.error || 'Error al eliminar'; }
    });
  }

  confirmMove(): void {
    const item = this.dialog.item!;
    const pid = (item.nasPathId != null ? item.nasPathId : this.selectedPathId)!;
    this.dialog.loading = true;
    this.nasService.move(pid, item.path, this.dialog.value.trim()).subscribe({
      next: () => { this.closeDialog(); this.load(); },
      error: (err: any) => { this.dialog.loading = false; this.dialog.error = err.error?.error || 'Error al mover'; }
    });
  }

  confirmMetadata(): void {
    const track = this.dialog.item!;
    const pid = (track.nasPathId != null ? track.nasPathId : this.selectedPathId)!;
    this.dialog.loading = true;
    this.nasService.updateMetadata(pid, track.path, this.dialog.title, this.dialog.artist).subscribe({
      next: () => { this.closeDialog(); this.load(); },
      error: (err: any) => { this.dialog.loading = false; this.dialog.error = err.error?.error || 'Error al actualizar metadatos'; }
    });
  }

  confirmCreateFolder(): void {
    if (!this.selectedPathId || !this.dialog.value.trim()) return;
    this.dialog.loading = true;
    this.nasService.mkdir(this.selectedPathId, this.dialog.value.trim(), this.currentSubPath).subscribe({
      next: () => { this.closeDialog(); this.load(); },
      error: (err: any) => { this.dialog.loading = false; this.dialog.error = err.error?.error || 'Error al crear carpeta'; }
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
      error: (err: any) => { this.dialog.loading = false; this.dialog.error = err.error?.error || 'Error al subir portada'; }
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
        this.uploadState.results = [{ name: 'Upload', status: 'error', message: err.error?.error || 'Error al subir' }];
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

  private triggerBlobDownload(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.click();
    URL.revokeObjectURL(url);
  }
}
