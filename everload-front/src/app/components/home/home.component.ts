import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { forkJoin, Subscription } from 'rxjs';
import { AuthService, AuthResponse } from '../../services/auth.service';
import { ChatService } from '../../services/chat.service';
import { ArtistProfileDto, MusicService, MusicMetadataDto } from '../../services/music.service';
import { NasService } from '../../services/nas.service';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit, OnDestroy {

  @ViewChild('avatarInput') avatarInput!: ElementRef<HTMLInputElement>;
  @ViewChild('artistsRow') artistsRowRef?: ElementRef<HTMLElement>;

  menuOpen = false;
  currentUser: AuthResponse | null = null;
  avatarUrl: string | null = null;
  avatarError = '';
  avatarLoading = false;

  unreadCount = 0;
  randomTracks: MusicMetadataDto[] = [];

  // Music sections
  listenNow: { album: string; artist: string; track: MusicMetadataDto; pathId: number }[] = [];
  topArtists: { artist: string; pathId: number; imageUrl?: string; autoImageUrl?: string; profile?: ArtistProfileDto; tracks: MusicMetadataDto[] }[] = [];
  musicLoading = false;

  private subs: Subscription[] = [];

  constructor(
    private translate: TranslateService,
    public authService: AuthService,
    private router: Router,
    public chatService: ChatService,
    public musicService: MusicService,
    private nasService: NasService,
  ) {
    // Language is configured by APP_INITIALIZER in app.module.ts.
    // Do NOT call setDefaultLang() here — it would override the initializer
    // and trigger an extra HTTP fetch that can fail with a stale SW cache.

    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
      this.avatarUrl = this.authService.getAvatarUrl();
    });
  }

  ngOnInit(): void {
    this.subs.push(
      this.chatService.unreadCount$.subscribe(count => {
        this.unreadCount = count;
      })
    );
    if (this.hasNasAccess) {
      this.musicService.getRandomTracks(3).subscribe({
        next: tracks => this.randomTracks = tracks,
        error: () => {}
      });
      this.loadMusicSections();
    }
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
  }

  private loadMusicSections() {
    this.musicLoading = true;
    this.nasService.getPaths().subscribe({
      next: paths => {
        const path = paths.find(p => p.readable);
        if (!path) { this.musicLoading = false; return; }
        const pathId = path.id;
        forkJoin({
          history: this.musicService.getHistory(24),
          overview: this.musicService.getLibraryOverview(pathId, 3000),
          profiles: this.musicService.getArtistProfiles(),
        }).subscribe({
          next: ({ history, overview, profiles }) => {
            const tracks = overview.tracks || [];
            // Listen Now — unique albums from history
            const albumMap = new Map<string, any>();
            (history || []).forEach((h: any) => {
              const key = (h.album || h.title || '').trim();
              if (key && !albumMap.has(key)) {
                const t: MusicMetadataDto = { name: h.title, path: h.trackPath, directory: false, size: 0, lastModified: '', title: h.title, artist: h.artist, album: h.album, duration: 0, format: '', hasCover: false, bpm: 0, source: 'nas', nasPathId: h.nasPathId ?? pathId };
                albumMap.set(key, { album: h.album || h.title, artist: h.artist, track: t, pathId: h.nasPathId ?? pathId });
              }
            });
            tracks.forEach(t => {
              const key = (t.album || t.title || '').trim();
              if (key && !albumMap.has(key)) albumMap.set(key, { album: t.album || t.title, artist: t.artist, track: t, pathId: t.nasPathId ?? pathId });
            });
            this.listenNow = Array.from(albumMap.values()).slice(0, 8);
            // Top Artists
            const profileByKey = new Map<string, ArtistProfileDto>();
            profiles.forEach(p => {
              [p.name, ...(p.aliases || '').split(/[\n,]+/).map((a: string) => a.trim()).filter(Boolean)]
                .forEach(n => profileByKey.set(this.normalizeKey(n), p));
            });
            const artistMap = new Map<string, any>();
            tracks.forEach(t => {
              const parts = (t.artist || '').split(/\s*(?:,|;|&|\+|\/|\bfeat\.?\b|\bft\.?\b)\s*/i).map((s: string) => s.trim()).filter(Boolean);
              (parts.length ? parts : [t.artist || '']).forEach(name => {
                if (!name) return;
                const key = this.normalizeKey(name);
                const profile = profileByKey.get(key);
                if (!artistMap.has(key)) {
                  artistMap.set(key, { artist: profile?.name || name, pathId: t.nasPathId ?? pathId, tracks: [t], profile, imageUrl: profile?.imageUrl ? (profile.imageUrl.startsWith('http') ? profile.imageUrl : `${this.musicService.BASE}${profile.imageUrl}`) : undefined });
                } else {
                  artistMap.get(key).tracks.push(t);
                }
              });
            });
            profiles.forEach(p => {
              const key = this.normalizeKey(p.name);
              if (!artistMap.has(key)) artistMap.set(key, { artist: p.name, pathId, tracks: [], profile: p, imageUrl: p.imageUrl ? (p.imageUrl.startsWith('http') ? p.imageUrl : `${this.musicService.BASE}${p.imageUrl}`) : undefined });
            });
            this.topArtists = Array.from(artistMap.values()).sort((a, b) => b.tracks.length - a.tracks.length).slice(0, 14);
            this.resolveAutoImages();
            this.musicLoading = false;
          },
          error: () => { this.musicLoading = false; }
        });
      },
      error: () => { this.musicLoading = false; }
    });
  }

  private resolveAutoImages() {
    this.topArtists.filter(a => !a.imageUrl && a.tracks.length).forEach(a => {
      this.musicService.getArtistImage(a.artist).subscribe({
        next: (r: any) => { if (r.found && r.imageUrl && !a.imageUrl) a.autoImageUrl = r.imageUrl; },
        error: () => {}
      });
    });
  }

  private normalizeKey(v: string): string {
    return (v || '').normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
  }

  coverFor(t: MusicMetadataDto, pathId: number): string {
    return this.musicService.getCoverUrlWithCache(pathId, t.path, t.source);
  }

  playAlbum(card: any) {
    this.musicService.mainPlayer.load(card.track, card.pathId).then(() => this.musicService.mainPlayer.play());
  }

  openArtistTracks(artist: any) {
    const aliases = (artist.profile?.aliases || '').split(/[\n,]+/).map((a: string) => a.trim()).filter(Boolean);
    this.musicService.setQueue(artist.pathId, artist.tracks.length ? artist.tracks : [], 0);
  }

  scrollArtists(dir: 1 | -1) {
    const el = this.artistsRowRef?.nativeElement;
    if (el) el.scrollBy({ left: dir * 220, behavior: 'smooth' });
  }

  get isAdmin(): boolean { return this.authService.isAdmin(); }
  get hasNasAccess(): boolean { return this.authService.hasNasAccess(); }

  toggleMenu(): void { this.menuOpen = !this.menuOpen; }
  closeMenu(): void { this.menuOpen = false; }

  @HostListener('document:keydown.escape')
  onEscape(): void { this.closeMenu(); }

  changeLanguage(lang: string) {
    this.translate.use(lang);
    localStorage.setItem('language', lang);
  }

  openWindowsMode(): void {
    if (!this.canOpenWindowsMode()) {
      window.alert(this.translate.instant('HOME.WINDOWS_MODE_UNAVAILABLE'));
      return;
    }
    this.musicService.nowPlayingPanelOpen = true;
  }

  private canOpenWindowsMode(): boolean {
    if (typeof window === 'undefined' || typeof navigator === 'undefined') return true;
    const viewportTooSmall = window.innerWidth < 980;
    const mobileUserAgent = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
    return !viewportTooSmall && !mobileUserAgent;
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  // ── Avatar ────────────────────────────────────────────────────────────────

  openAvatarPicker(): void {
    this.avatarInput.nativeElement.click();
  }

  onAvatarFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    const allowed = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
    if (!allowed.includes(file.type)) {
      this.avatarError = this.translate.instant('HOME.AVATAR_ERROR_TYPE');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.avatarError = this.translate.instant('HOME.AVATAR_ERROR_SIZE');
      return;
    }

    this.avatarError = '';
    this.avatarLoading = true;
    this.authService.uploadAvatar(file).subscribe({
      next: () => { this.avatarLoading = false; },
      error: (err) => {
        this.avatarLoading = false;
        this.avatarError = err.error?.error || this.translate.instant('HOME.AVATAR_ERROR_UPLOAD');
      }
    });
    input.value = '';
  }

  removeAvatar(): void {
    if (!confirm(this.translate.instant('HOME.AVATAR_REMOVE_CONFIRM'))) return;
    this.avatarLoading = true;
    this.authService.removeAvatar().subscribe({
      next: () => { this.avatarLoading = false; },
      error: () => { this.avatarLoading = false; }
    });
  }

  onCoverLoaded(event: Event): void {
    (event.target as HTMLImageElement).classList.add('loaded');
  }

  onCoverError(event: Event): void {
    (event.target as HTMLImageElement).style.display = 'none';
  }

  getRoleBadgeClass(): string {
    const role = this.currentUser?.role;
    if (role === 'ADMIN') return 'badge-admin';
    if (role === 'NAS_USER') return 'badge-nas';
    return 'badge-basic';
  }
}
