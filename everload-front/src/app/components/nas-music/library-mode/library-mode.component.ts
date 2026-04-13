import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
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

  // iTunes cover cache: trackPath → url
  private coverOverrideMap = new Map<string, string>();
  private itunesFetchedTerms = new Set<string>();

  private subs: Subscription[] = [];

  constructor(
    public musicService: MusicService,
    private nasService: NasService,
    private authService: AuthService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.nasService.getPaths().subscribe(paths => {
      this.paths = paths;
      if (paths.length > 0) this.selectPath(paths[0].id);
      else this.setView('home');
    });

    this.subs.push(this.musicService.mainPlayer.state$.subscribe(s => {
      const prev = this.state?.currentTrack?.path;
      this.state = s;
      if (s.currentTrack && s.currentTrack.path !== prev) {
        this.fetchCoverIfNeeded(s.currentTrack);
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
          hasCover: true,
          directory: false,
          nasPathId: f.nasPathId,
          duration: 0,
          size: 0,
          format: '',
          lastModified: '',
          bpm: 0
        } as MusicMetadataDto));
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
           hasCover: true,
           directory: false,
           nasPathId: h.nasPathId,
           duration: h.durationSeconds,
           size: 0,
           format: ''
        } as MusicMetadataDto));
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
    if (!this.searchQuery.trim()) return this.folders;
    const q = this.searchQuery.trim().toLowerCase();
    return this.folders.filter(f => f.name.toLowerCase().includes(q));
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

  togglePlay()  { this.musicService.mainPlayer.togglePlay(); }
  next()        { this.musicService.playNextMain(); }
  prev()        { this.musicService.playPrevMain(); }
  toggleShuffle() { this.musicService.toggleShuffle(); }
  toggleRepeat()  { this.musicService.toggleRepeat(); }

  onSeek(e: Event)   { this.musicService.mainPlayer.seek(+(e.target as HTMLInputElement).value); }
  onVolume(e: Event) { this.musicService.mainPlayer.setVolume(+(e.target as HTMLInputElement).value); }

  onSeekClick(e: MouseEvent) {
    const bar = (e.currentTarget as HTMLElement);
    const rect = bar.getBoundingClientRect();
    const pct = (e.clientX - rect.left) / rect.width;
    const duration = this.state?.duration ?? 0;
    if (duration > 0) this.musicService.mainPlayer.seek(pct * duration);
  }

  // ── Cover art & Interactions ──────────────────────────────────────────────

  coverUrl(track: MusicMetadataDto): string {
    if (this.coverOverrideMap.has(track.path)) return this.coverOverrideMap.get(track.path)!;
    const pid = track.nasPathId ?? ((this.state?.currentTrack?.path === track.path && this.state?.pathId)
                ? this.state.pathId
                : this.selectedPathId);
    if (pid === null || pid === undefined) return '';
    return this.musicService.getCoverUrl(pid, track.path);
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
    return track.hasCover || this.coverOverrideMap.has(track.path) || this.currentView !== 'folder';
  }

  playerCoverUrl(): string {
    const t = this.state?.currentTrack;
    if (!t) return '';
    if (this.coverOverrideMap.has(t.path)) return this.coverOverrideMap.get(t.path)!;
    if (!t.hasCover || !this.state?.pathId) return '';
    return this.musicService.getCoverUrl(this.state.pathId, t.path);
  }

  playerHasCover(): boolean {
    const t = this.state?.currentTrack;
    return !!t && (t.hasCover || this.coverOverrideMap.has(t.path));
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
    this.tracks.filter(t => !t.hasCover).slice(0, 30).forEach(t => this.fetchCoverIfNeeded(t));
  }

  private fetchCoverIfNeeded(track: MusicMetadataDto) {
    if (!track || track.hasCover || this.coverOverrideMap.has(track.path)) return;
    const term = `${track.artist || ''} ${track.album || track.title || ''}`.trim();
    if (!term) return;
    if (this.itunesFetchedTerms.has(term)) return;
    
    this.itunesFetchedTerms.add(term);
    const encoded = encodeURIComponent(term);
    fetch(`https://itunes.apple.com/search?term=${encoded}&entity=album&limit=1`)
      .then(r => r.json())
      .then(data => {
        const result = data.results?.[0];
        if (result?.artworkUrl100) {
          const hq = result.artworkUrl100.replace('100x100bb', '600x600bb');
          this.tracks
            .filter(t => !t.hasCover && `${t.artist || ''} ${t.album || t.title || ''}`.trim() === term)
            .forEach(t => this.coverOverrideMap.set(t.path, hq));
          this.coverOverrideMap.set(track.path, hq);
        }
      })
      .catch(() => {});
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
    const displayName = item.directory ? item.name : (item.title || item.name);
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
    this.dialog.loading = true;
    this.nasService.uploadFolderCover(pid, this.dialog.item.path, file).subscribe({
      next: () => { this.closeDialog(); this.load(); },
      error: (err: any) => { this.dialog.loading = false; this.dialog.error = err.error?.error || 'Error al subir portada'; }
    });
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

  progressPct(): number {
    if (!this.state?.duration) return 0;
    return (this.state.currentTime / this.state.duration) * 100;
  }

  repeatIcon(): string {
    if (this.repeat === 'one') return 'repeat1';
    if (this.repeat === 'all') return 'repeatAll';
    return 'none';
  }
}
