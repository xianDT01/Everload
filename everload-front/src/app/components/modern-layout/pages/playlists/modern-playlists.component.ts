import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, NavigationEnd, ActivatedRoute } from '@angular/router';
import { smartShuffle } from '../../../../services/smart-shuffle';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { MusicService, MusicMetadataDto } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';
import { AuthService } from '../../../../services/auth.service';

@Component({
  selector: 'app-modern-playlists',
  templateUrl: './modern-playlists.component.html',
  styleUrls: ['./modern-playlists.component.css']
})
export class ModernPlaylistsComponent implements OnInit, OnDestroy {
  playlists: any[] = [];
  publicPlaylists: any[] = [];
  sharedPlaylists: any[] = [];
  loading = false;
  loadingPublic = false;
  loadingShared = false;
  newName = '';
  creating = false;
  importUrl = '';
  importName = '';
  importing = false;
  actionBusy = false;
  actionError = '';
  importResult: { count: number; skipped: number; spotify: boolean } | null = null;
  shareLink = '';
  linkCopied = false;
  private linkSub?: Subscription;
  private actionSub?: Subscription;
  private importSub?: Subscription;

  tab: 'mine' | 'community' | 'shared' = 'mine';
  view: 'list' | 'detail' = 'list';
  selectedPlaylist: any | null = null;

  searchQuery = '';
  searchResults: MusicMetadataDto[] = [];
  searching = false;
  private searchDebounce: any;
  private searchSub?: Subscription;

  // Share dialog
  showShareDialog = false;
  shareUsername = '';
  sharingBusy = false;
  shareError = '';
  userSuggestions: string[] = [];
  private userSearchDebounce: any;
  private userSearchSub?: Subscription;
  private routerSub?: Subscription;

  constructor(public music: MusicService, public state: ModernStateService, private auth: AuthService, private router: Router, private route: ActivatedRoute) {}

  ngOnInit() {
    this.load();
    this.linkSub = this.route.queryParamMap.subscribe(params => {
      const id = Number(params.get('playlist') || sessionStorage.getItem('everload_playlist_link'));
      sessionStorage.removeItem('everload_playlist_link');
      if (!Number.isSafeInteger(id) || id <= 0) return;
      this.actionSub?.unsubscribe();
      this.actionSub = this.music.getPlaylist(id).subscribe({
        next: playlist => this.openPlaylist(playlist),
        error: () => { this.actionError = 'PLAYLISTS.UNAVAILABLE'; }
      });
    });
    // El layout moderno mantiene esta página viva entre pestañas (RouteReuseStrategy),
    // así que ngOnInit solo corre una vez. Recargamos al volver a entrar para que las
    // playlists no queden vacías/obsoletas si la primera carga falló o llegó vacía.
    this.routerSub = this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe(() => {
        if (this.router.url.includes('/playlists')) this.refreshActive();
      });
  }

  ngOnDestroy() {
    this.linkSub?.unsubscribe();
    this.actionSub?.unsubscribe();
    this.importSub?.unsubscribe();
    this.searchSub?.unsubscribe();
    this.userSearchSub?.unsubscribe();
    this.routerSub?.unsubscribe();
    clearTimeout(this.searchDebounce);
    clearTimeout(this.userSearchDebounce);
  }

  /** Recarga los datos de la pestaña activa (al re-entrar en la vista). */
  private refreshActive() {
    this.load();
    if (this.tab === 'community') this.loadPublic();
    if (this.tab === 'shared') this.loadShared();
  }

  load() {
    this.loading = true;
    this.music.getPlaylists().subscribe({
      next: p => {
        this.playlists = p;
        this.loading = false;
        if (this.selectedPlaylist) {
          const all = [...this.playlists, ...this.sharedPlaylists, ...this.publicPlaylists];
          const updated = all.find((pl: any) => pl.id === this.selectedPlaylist.id);
          if (updated) this.selectedPlaylist = updated;
        }
      },
      error: () => { this.loading = false; }
    });
  }

  loadPublic() {
    this.loadingPublic = true;
    this.music.getPublicPlaylists().subscribe({
      next: p => { this.publicPlaylists = p; this.loadingPublic = false; },
      error: () => { this.loadingPublic = false; }
    });
  }

  loadShared() {
    this.loadingShared = true;
    this.music.getSharedPlaylists().subscribe({
      next: p => { this.sharedPlaylists = p; this.loadingShared = false; },
      error: () => { this.loadingShared = false; }
    });
  }

  switchTab(t: 'mine' | 'community' | 'shared') {
    this.tab = t;
    this.view = 'list';
    this.selectedPlaylist = null;
    if (t === 'community' && !this.publicPlaylists.length) this.loadPublic();
    if (t === 'shared' && !this.sharedPlaylists.length) this.loadShared();
  }

  create() {
    if (!this.newName.trim()) return;
    this.creating = true;
    this.music.createPlaylist(this.newName.trim()).subscribe({
      next: () => { this.newName = ''; this.creating = false; this.load(); },
      error: () => { this.creating = false; }
    });
  }

  delete(id: number) {
    if (!confirm('¿Eliminar playlist?')) return;
    this.music.deletePlaylist(id).subscribe(() => this.load());
  }

  openPlaylist(pl: any) {
    this.shareLink = '';
    this.linkCopied = false;
    this.selectedPlaylist = pl;
    this.view = 'detail';
    this.searchQuery = '';
    this.searchResults = [];
  }

  back() {
    this.view = 'list';
    this.selectedPlaylist = null;
    this.router.navigate([], { relativeTo: this.route, queryParams: { playlist: null }, queryParamsHandling: 'merge' });
  }

  importPlaylist() {
    if (this.importing || !this.importUrl.trim() || !this.importName.trim()) return;
    this.importing = true;
    this.actionError = '';
    this.importResult = null;
    this.importSub = this.music.importPlaylist(this.importUrl.trim(), this.importName.trim()).subscribe({
      next: result => {
        this.importing = false;
        this.importResult = { count: result.playlist.tracks.length, skipped: result.skipped, spotify: result.spotifySource };
        this.importUrl = '';
        this.importName = '';
        this.playlists = [result.playlist, ...this.playlists];
        this.openPlaylist(result.playlist);
      },
      error: () => { this.importing = false; this.actionError = 'PLAYLISTS.IMPORT_ERROR'; }
    });
  }

  duplicate() {
    if (!this.selectedPlaylist || this.actionBusy) return;
    this.actionBusy = true;
    this.actionError = '';
    this.actionSub = this.music.duplicatePlaylist(this.selectedPlaylist.id).subscribe({
      next: playlist => {
        this.actionBusy = false;
        this.playlists = [playlist, ...this.playlists];
        this.tab = 'mine';
        this.openPlaylist(playlist);
      },
      error: () => { this.actionBusy = false; this.actionError = 'PLAYLISTS.ACTION_ERROR'; }
    });
  }

  async copyLink() {
    if (!this.selectedPlaylist) return;
    const tree = this.router.createUrlTree(['/modern/playlists'], { queryParams: { playlist: this.selectedPlaylist.id } });
    this.shareLink = new URL(this.router.serializeUrl(tree), location.origin).href;
    try {
      await navigator.clipboard.writeText(this.shareLink);
      this.linkCopied = true;
    } catch { this.linkCopied = false; }
  }

  playSmart() {
    if (!this.selectedPlaylist?.tracks?.length) return;
    if (this.music.shuffle) this.music.toggleShuffle();
    this.playFromPlaylist({ ...this.selectedPlaylist, tracks: smartShuffle(this.selectedPlaylist.tracks) }, 0);
  }

  isOwned(pl: any): boolean {
    return pl != null && this.playlists.some(p => p.id === pl.id);
  }

  get currentUsername(): string | null {
    return this.auth.getCurrentUser()?.username ?? null;
  }

  isCollaborator(pl: any): boolean {
    return pl != null && (pl.collaboratorUsernames ?? []).includes(this.currentUsername);
  }

  canEditTracks(pl: any): boolean {
    return this.isOwned(pl) || this.isCollaborator(pl);
  }

  toggleVisibility(pl: any, event: Event) {
    event.stopPropagation();
    this.music.setPlaylistVisibility(pl.id, !pl.isPublic).subscribe(() => {
      this.load();
      if (this.publicPlaylists.length) this.loadPublic();
    });
  }

  // ── Colaboradores ─────────────────────────────────────────────────────────

  openShareDialog() {
    this.shareUsername = '';
    this.shareError = '';
    this.userSuggestions = [];
    this.showShareDialog = true;
  }

  closeShareDialog() {
    this.showShareDialog = false;
    this.userSuggestions = [];
  }

  onShareUsernameInput() {
    clearTimeout(this.userSearchDebounce);
    const q = this.shareUsername.trim();
    if (!q) { this.userSuggestions = []; return; }
    this.userSearchDebounce = setTimeout(() => {
      this.userSearchSub?.unsubscribe();
      this.userSearchSub = this.music.searchUsers(q).subscribe({
        next: users => {
          const existing = this.selectedPlaylist?.collaboratorUsernames ?? [];
          this.userSuggestions = users.filter(u => !existing.includes(u));
        },
        error: () => { this.userSuggestions = []; }
      });
    }, 250);
  }

  selectUserSuggestion(username: string) {
    this.shareUsername = username;
    this.userSuggestions = [];
  }

  addCollaborator() {
    if (!this.selectedPlaylist || !this.shareUsername.trim()) return;
    this.sharingBusy = true;
    this.shareError = '';
    this.userSuggestions = [];
    this.music.addPlaylistCollaborator(this.selectedPlaylist.id, this.shareUsername.trim()).subscribe({
      next: (usernames: string[]) => {
        this.selectedPlaylist.collaboratorUsernames = usernames;
        this.shareUsername = '';
        this.sharingBusy = false;
      },
      error: (err) => {
        this.shareError = err?.error?.error || 'No se pudo añadir al colaborador';
        this.sharingBusy = false;
      }
    });
  }

  removeCollaborator(username: string) {
    if (!this.selectedPlaylist) return;
    this.music.removePlaylistCollaborator(this.selectedPlaylist.id, username).subscribe(() => {
      this.selectedPlaylist.collaboratorUsernames = (this.selectedPlaylist.collaboratorUsernames ?? [])
        .filter((u: string) => u !== username);
    });
  }

  leavePlaylist(pl: any, event: Event) {
    event.stopPropagation();
    if (!confirm('¿Abandonar esta playlist colaborativa?')) return;
    this.music.leavePlaylist(pl.id).subscribe(() => {
      this.sharedPlaylists = this.sharedPlaylists.filter(p => p.id !== pl.id);
      if (this.selectedPlaylist?.id === pl.id) this.back();
    });
  }

  play(pl: any) {
    this.playFromPlaylist(pl, 0);
  }

  /** Reproduce la playlist abierta empezando por la pista [index] (al pulsar una canción). */
  playFrom(index: number) {
    this.playFromPlaylist(this.selectedPlaylist, index);
  }

  private playFromPlaylist(pl: any, index: number) {
    const pid = this.state.pathId ?? 0;
    if (!pl?.tracks?.length) return;
    const tracks = pl.tracks.map((t: any) => ({
      name: t.title, path: t.trackPath, directory: false, size: 0,
      lastModified: '', title: t.title, artist: t.artist, album: t.album,
      duration: t.durationSeconds ?? 0, format: '', hasCover: false, bpm: 0,
      source: t.source || 'nas', nasPathId: t.nasPathId ?? pid
    }));
    this.music.setQueue(pid, tracks, Math.min(Math.max(index, 0), tracks.length - 1));
  }

  removeTrack(trackId: number) {
    if (!this.selectedPlaylist) return;
    this.music.removeTrackFromPlaylist(this.selectedPlaylist.id, trackId).subscribe(() => this.reloadCurrentTab());
  }

  /** Reloads whichever playlist list is backing the current tab and refreshes the open detail view. */
  private reloadCurrentTab() {
    if (this.tab === 'shared') {
      this.music.getSharedPlaylists().subscribe(p => {
        this.sharedPlaylists = p;
        if (this.selectedPlaylist) {
          const updated = p.find((pl: any) => pl.id === this.selectedPlaylist.id);
          if (updated) this.selectedPlaylist = updated;
        }
      });
    } else {
      this.load();
    }
  }

  onSearchInput() {
    clearTimeout(this.searchDebounce);
    if (!this.searchQuery.trim()) { this.searchResults = []; return; }
    this.searchDebounce = setTimeout(() => this.doSearch(), 250);
  }

  doSearch() {
    const pid = this.state.pathId;
    if (!this.searchQuery.trim() || pid == null) return;
    this.searchSub?.unsubscribe();
    this.searching = true;
    this.searchSub = this.music.search(pid, undefined, this.searchQuery, 50).subscribe({
      next: r => { this.searchResults = r; this.searching = false; },
      error: () => { this.searching = false; }
    });
  }

  addTrack(track: MusicMetadataDto) {
    const pid = this.state.pathId;
    if (!this.selectedPlaylist || pid == null) return;
    this.music.addTrackToPlaylist(this.selectedPlaylist.id, track, track.nasPathId ?? pid).subscribe(() => this.reloadCurrentTab());
  }

  trackCoverUrl(t: any): string {
    return this.music.getCoverUrlWithCache(t.nasPathId ?? this.state.pathId ?? 0, t.trackPath, t.source || 'nas');
  }

  searchCoverUrl(t: MusicMetadataDto): string {
    return this.music.getCoverUrlWithCache(t.nasPathId ?? this.state.pathId ?? 0, t.path, t.source);
  }

  coverTracks(pl: any): any[] {
    return (pl.tracks ?? []).slice(0, 4);
  }

  isInPlaylist(track: MusicMetadataDto): boolean {
    return (this.selectedPlaylist?.tracks ?? []).some((t: any) => t.trackPath === track.path);
  }

  fmtDur(s: number): string {
    if (!s || !isFinite(s)) return '';
    return `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;
  }
}
