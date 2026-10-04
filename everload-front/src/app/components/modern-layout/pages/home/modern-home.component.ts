import { Component, ElementRef, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { catchError, forkJoin, of, Subscription } from 'rxjs';
import { ArtistProfileDto, CommunityDiscoverDto, MusicService, MusicMetadataDto, YtMusicDiscoverItemDto } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';
import { AuthService } from '../../../../services/auth.service';

interface AlbumCard {
  album: string;
  artist: string;
  track: MusicMetadataDto;
  pathId: number;
  tracks: MusicMetadataDto[];
}

interface ArtistCard {
  artist: string;
  track: MusicMetadataDto;
  pathId: number;
  tracks: MusicMetadataDto[];
  profile?: ArtistProfileDto;
  imageUrl?: string;
  autoImageUrl?: string;
}

interface HomeSection {
  key: string;
  enabled: boolean;
}

const HOME_SECTION_DEFAULTS: HomeSection[] = [
  { key: 'featured', enabled: true },
  { key: 'listen_now', enabled: true },
  { key: 'top_artists', enabled: true },
  { key: 'recently_added', enabled: true },
  { key: 'explore', enabled: true },
  { key: 'yt_playlists', enabled: true },
];

const LS_SECTIONS = 'modern_home_sections';
const LS_LISTEN_STYLE = 'modern_home_listen_style';

@Component({
  selector: 'app-modern-home',
  templateUrl: './modern-home.component.html',
  styleUrls: ['./modern-home.component.css']
})
export class ModernHomeComponent implements OnInit, OnDestroy {
  featured: { track: MusicMetadataDto; pathId: number } | null = null;
  // Pool de "lo más escuchado" que rota en el hero con un fundido suave.
  featuredPool: { track: MusicMetadataDto; pathId: number }[] = [];
  featuredFade = false;
  featuredFav = false;
  private featuredIndex = 0;
  private featuredTimer?: ReturnType<typeof setInterval>;
  private featuredFadeTimer?: ReturnType<typeof setTimeout>;
  listenNow: AlbumCard[] = [];
  topArtists: ArtistCard[] = [];
  recentlyAdded: AlbumCard[] = [];
  newReleases: AlbumCard[] = [];
  ytPlaylists: YtMusicDiscoverItemDto[] = [];
  selectedArtist: ArtistCard | null = null;
  selectedArtistTracks: MusicMetadataDto[] = [];
  artistLoading = false;
  artistError = '';
  loading = true;
  isFlow = false;

  editMode = false;
  homeSections: HomeSection[] = [];
  listenNowStyle: 'cards' | 'list' = 'cards';

  private sub!: Subscription;
  private coverSub?: Subscription;
  private indexPoll?: ReturnType<typeof setTimeout>;
  private imageRetryTimer?: ReturnType<typeof setTimeout>;

  @ViewChild('artistsRow') artistsRowRef?: ElementRef<HTMLElement>;
  @ViewChild('recentRow') recentRowRef?: ElementRef<HTMLElement>;
  @ViewChild('exploreRow') exploreRowRef?: ElementRef<HTMLElement>;
  @ViewChild('ytPlaylistsRow') ytPlaylistsRowRef?: ElementRef<HTMLElement>;

  constructor(public music: MusicService, private state: ModernStateService, private router: Router, private auth: AuthService) {}

  ngOnInit() {
    this.isFlow = this.router.url.startsWith('/modern/flow');
    this.loadHomeConfig();
    this.sub = this.state.pathId$.subscribe(pid => {
      if (pid != null) this.load(pid);
    });
    this.sub.add(this.router.events.subscribe(event => {
      if (event instanceof NavigationEnd) this.isFlow = event.urlAfterRedirects.startsWith('/modern/flow');
    }));
    this.coverSub = this.music.coverReady$.subscribe(() => {});
    this.loadYtPlaylists();
  }

  get flowCards(): AlbumCard[] {
    const seen = new Set<string>();
    return [...this.listenNow, ...this.newReleases, ...this.recentlyAdded].filter(card => {
      const key = `${card.album}\u0000${card.artist}`.toLocaleLowerCase();
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    }).slice(0, 12);
  }

  openFlowLink(route: string): void {
    this.router.navigateByUrl(route);
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
    this.coverSub?.unsubscribe();
    if (this.indexPoll) clearTimeout(this.indexPoll);
    if (this.imageRetryTimer) clearTimeout(this.imageRetryTimer);
    this.stopFeaturedRotation();
  }

  /** Rota el hero "Álbum destacado" entre lo más escuchado, con un fundido suave. */
  private startFeaturedRotation() {
    this.stopFeaturedRotation();
    if (this.featuredPool.length <= 1) return;
    this.featuredTimer = setInterval(() => this.rotateFeatured(), 8000);
  }

  private stopFeaturedRotation() {
    if (this.featuredTimer) clearInterval(this.featuredTimer);
    if (this.featuredFadeTimer) clearTimeout(this.featuredFadeTimer);
    this.featuredTimer = undefined;
    this.featuredFadeTimer = undefined;
  }

  private rotateFeatured() {
    if (this.featuredPool.length <= 1) return;
    this.featuredFade = true;                       // fundido de salida
    this.featuredFadeTimer = setTimeout(() => {
      this.featuredIndex = (this.featuredIndex + 1) % this.featuredPool.length;
      this.featured = this.featuredPool[this.featuredIndex];
      this.checkFeaturedFav();
      this.featuredFade = false;                    // fundido de entrada
    }, 450);
  }

  // ── Home config (localStorage) ────────────────────────────────

  private loadHomeConfig() {
    try {
      const raw = localStorage.getItem(LS_SECTIONS);
      if (raw) {
        const saved: HomeSection[] = JSON.parse(raw);
        const validKeys = HOME_SECTION_DEFAULTS.map(s => s.key);
        const merged = saved.filter(s => validKeys.includes(s.key));
        const missing = HOME_SECTION_DEFAULTS.filter(d => !merged.some(m => m.key === d.key));
        this.homeSections = [...merged, ...missing];
      } else {
        this.homeSections = HOME_SECTION_DEFAULTS.map(s => ({ ...s }));
      }
    } catch {
      this.homeSections = HOME_SECTION_DEFAULTS.map(s => ({ ...s }));
    }
    this.listenNowStyle = (localStorage.getItem(LS_LISTEN_STYLE) as 'cards' | 'list') || 'cards';
  }

  private saveHomeConfig() {
    localStorage.setItem(LS_SECTIONS, JSON.stringify(this.homeSections));
  }

  isEnabled(key: string): boolean {
    return this.homeSections.find(s => s.key === key)?.enabled ?? true;
  }

  sectionOrder(key: string): number {
    return this.homeSections.findIndex(s => s.key === key);
  }

  sectionLabelKey(key: string): string {
    const labels: Record<string, string> = {
      featured: 'MUSIC.MODERN_FEATURED_ALBUM',
      listen_now: 'MUSIC.MODERN_LISTEN_NOW',
      top_artists: 'MUSIC.MODERN_TOP_ARTISTS',
      recently_added: 'MUSIC.MODERN_RECENTLY_ADDED',
      explore: 'MUSIC.MODERN_EXPLORE',
      yt_playlists: 'MUSIC.MODERN_YTMUSIC_PLAYLISTS',
    };
    return labels[key] || key;
  }

  toggleSection(key: string) {
    const s = this.homeSections.find(s => s.key === key);
    if (s) { s.enabled = !s.enabled; this.saveHomeConfig(); }
  }

  moveSection(key: string, dir: 1 | -1) {
    const i = this.homeSections.findIndex(s => s.key === key);
    const j = i + dir;
    if (j < 0 || j >= this.homeSections.length) return;
    [this.homeSections[i], this.homeSections[j]] = [this.homeSections[j], this.homeSections[i]];
    this.saveHomeConfig();
  }

  resetSections() {
    this.homeSections = HOME_SECTION_DEFAULTS.map(s => ({ ...s }));
    this.saveHomeConfig();
  }

  toggleListenNowStyle() {
    this.listenNowStyle = this.listenNowStyle === 'cards' ? 'list' : 'cards';
    localStorage.setItem(LS_LISTEN_STYLE, this.listenNowStyle);
  }

  // ── Data loading ──────────────────────────────────────────────

  private toTrack(i: any, pathId: number): MusicMetadataDto {
    return {
      name: i.title, path: i.trackPath, directory: false, size: 0,
      lastModified: i.lastModified || '', title: i.title, artist: i.artist, album: i.album,
      duration: 0, format: '', hasCover: false, bpm: 0, source: 'nas' as const,
      nasPathId: i.nasPathId ?? pathId
    };
  }

  private load(pathId: number) {
    this.loading = true;

    forkJoin({
      history: this.music.getHistory(24),
      overview: this.state.getOverview(pathId),
      recent: this.music.getRecentTracks(pathId, 60),
      profiles: this.music.getArtistProfiles(),
      topArtists: this.music.getTopArtists(50),
      favorites: this.music.getFavorites().pipe(catchError(() => of([]))),
      community: this.music.getCommunityDiscover(60).pipe(catchError(() => of({ topArtists: [], topTracks: [] } as CommunityDiscoverDto))),
    }).subscribe({
      next: ({ history, overview, recent, profiles, topArtists, favorites, community }) => {
        const items = history || [];
        const tracks = overview.tracks || [];
        const allTimePlays = (topArtists || []).reduce((total, entry) => total + (entry.playCount || 0), 0);
        const personalSignals = Math.max(items.length, allTimePlays) + favorites.length * 2;
        const accountCreatedAt = this.auth.getCurrentUser()?.createdAt;
        const existingAccount = !accountCreatedAt || accountCreatedAt < '2026-09-25T00:00:00';
        const personalWeight = existingAccount ? 1 : Math.min(1, personalSignals / 15);
        if (this.indexPoll) clearTimeout(this.indexPoll);
        if (overview.indexing && tracks.length === 0) {
          this.indexPoll = setTimeout(() => this.load(pathId), 6000);
        }
        const profileByKey = new Map<string, ArtistProfileDto>();
        profiles.forEach(profile => this.profileKeys(profile).forEach(key => profileByKey.set(key, profile)));

        const ownTrackCounts = new Map<string, number>();
        items.forEach((item: any) => {
          const key = `${item.nasPathId ?? pathId}:${item.trackPath || ''}`;
          ownTrackCounts.set(key, (ownTrackCounts.get(key) || 0) + 1);
        });
        const favoriteTrackCounts = new Map<string, number>();
        favorites.forEach((favorite: any) => {
          const key = `${favorite.nasPathId ?? pathId}:${favorite.trackPath || ''}`;
          favoriteTrackCounts.set(key, (favoriteTrackCounts.get(key) || 0) + 2);
        });
        const communityTrackCounts = new Map<string, number>();
        const communityTrackFallback = new Map<string, number>();
        community.topTracks.forEach(entry => {
          const key = this.trackRecommendationKey(entry.title, entry.artist, entry.album);
          communityTrackCounts.set(key, entry.playCount);
          const fallback = `${this.key(entry.title)}|${this.key(entry.album)}`;
          communityTrackFallback.set(fallback, (communityTrackFallback.get(fallback) || 0) + entry.playCount);
        });
        const candidateTracks = new Map<string, { track: MusicMetadataDto; pathId: number }>();
        items.forEach((item: any) => {
          const track = this.toTrack(item, pathId);
          if (track.path) candidateTracks.set(`${item.nasPathId ?? pathId}:${track.path}`, { track, pathId: item.nasPathId ?? pathId });
        });
        tracks.forEach(track => {
          if (track.path) candidateTracks.set(`${track.nasPathId ?? pathId}:${track.path}`, { track, pathId: track.nasPathId ?? pathId });
        });
        const candidateScores = Array.from(candidateTracks.values()).map(candidate => {
          const key = `${candidate.pathId}:${candidate.track.path}`;
          const own = (ownTrackCounts.get(key) || 0) + (favoriteTrackCounts.get(key) || 0);
          const recommendationKey = this.trackRecommendationKey(candidate.track.title, candidate.track.artist, candidate.track.album);
          const global = communityTrackCounts.get(recommendationKey)
            || communityTrackFallback.get(`${this.key(candidate.track.title)}|${this.key(candidate.track.album)}`) || 0;
          return { ...candidate, own, global };
        });
        const maxOwnTrack = this.maxCount(candidateScores.map(candidate => candidate.own));
        const maxGlobalTrack = this.maxCount(candidateScores.map(candidate => candidate.global));
        candidateScores.sort((a, b) => this.blendedScore(b.own, b.global, personalWeight, maxOwnTrack, maxGlobalTrack)
          - this.blendedScore(a.own, a.global, personalWeight, maxOwnTrack, maxGlobalTrack));
        const seenAlbum = new Set<string>();
        const pool: { track: MusicMetadataDto; pathId: number }[] = [];
        if (existingAccount) {
          const frequency = new Map<string, number>();
          items.forEach((item: any) => {
            const key = (item.trackPath || '').trim();
            if (key) frequency.set(key, (frequency.get(key) || 0) + 1);
          });
          [...items].sort((a: any, b: any) =>
            (frequency.get((b.trackPath || '').trim()) || 0) - (frequency.get((a.trackPath || '').trim()) || 0))
            .forEach((item: any) => {
              const albumKey = (item.album || item.title || '').trim().toLowerCase();
              if (albumKey && !seenAlbum.has(albumKey)) {
                seenAlbum.add(albumKey);
                pool.push({ track: this.toTrack(item, pathId), pathId: item.nasPathId ?? pathId });
              }
            });
          if (!pool.length) tracks.forEach(track => {
            const albumKey = (track.album || track.title || '').trim().toLowerCase();
            if (albumKey && !seenAlbum.has(albumKey)) {
              seenAlbum.add(albumKey);
              pool.push({ track, pathId: track.nasPathId ?? pathId });
            }
          });
        } else {
          candidateScores.forEach(candidate => {
            const albumKey = this.albumRecommendationKey(candidate.track.artist, candidate.track.album);
            if (albumKey && !seenAlbum.has(albumKey)) {
              seenAlbum.add(albumKey);
              pool.push({ track: candidate.track, pathId: candidate.pathId });
            }
          });
        }
        this.featuredPool = pool.slice(0, 8);
        this.featuredIndex = 0;
        this.featured = this.featuredPool[0]
          || (tracks[0] ? { track: tracks[0], pathId: tracks[0].nasPathId ?? pathId } : null);
        this.checkFeaturedFav();
        this.startFeaturedRotation();

        // Listen Now = unique albums from history + overview
        const albumMap = new Map<string, AlbumCard>();
        items.forEach((i: any) => {
          const key = (i.album || i.title || '').trim();
          if (key && !albumMap.has(key)) {
            const t = this.toTrack(i, pathId);
            albumMap.set(key, { album: i.album || i.title, artist: i.artist, track: t, pathId: i.nasPathId ?? pathId, tracks: [t] });
          } else if (key) {
            albumMap.get(key)!.tracks.push(this.toTrack(i, pathId));
          }
        });
        tracks.forEach(t => {
          const key = (t.album || t.title || '').trim();
          if (!key) return;
          if (!albumMap.has(key)) {
            albumMap.set(key, { album: t.album || t.title, artist: t.artist, track: t, pathId: t.nasPathId ?? pathId, tracks: [t] });
          } else if (!albumMap.get(key)!.tracks.some(existing => existing.path === t.path)) {
            albumMap.get(key)!.tracks.push(t);
          }
        });
        const ownAlbumCounts = new Map<string, number>();
        items.forEach((item: any) => {
          const key = this.albumRecommendationKey(item.artist, item.album || item.title);
          ownAlbumCounts.set(key, (ownAlbumCounts.get(key) || 0) + 1);
        });
        favorites.forEach((favorite: any) => {
          const key = this.albumRecommendationKey(favorite.artist, favorite.album || favorite.title);
          ownAlbumCounts.set(key, (ownAlbumCounts.get(key) || 0) + 2);
        });
        const globalAlbumCounts = new Map<string, number>();
        community.topTracks.forEach(entry => {
          const key = this.albumRecommendationKey(entry.artist, entry.album || entry.title);
          globalAlbumCounts.set(key, (globalAlbumCounts.get(key) || 0) + entry.playCount);
        });
        const albumCards = Array.from(albumMap.values());
        if (existingAccount) {
          this.listenNow = albumCards.slice(0, 9);
        } else {
          const maxOwnAlbum = this.maxCount(Array.from(ownAlbumCounts.values()));
          const maxGlobalAlbum = this.maxCount(Array.from(globalAlbumCounts.values()));
          this.listenNow = albumCards.sort((a, b) => {
            const keyA = this.albumRecommendationKey(a.artist, a.album);
            const keyB = this.albumRecommendationKey(b.artist, b.album);
            return this.blendedScore(ownAlbumCounts.get(keyB) || 0, globalAlbumCounts.get(keyB) || 0, personalWeight, maxOwnAlbum, maxGlobalAlbum)
              - this.blendedScore(ownAlbumCounts.get(keyA) || 0, globalAlbumCounts.get(keyA) || 0, personalWeight, maxOwnAlbum, maxGlobalAlbum);
          }).slice(0, 9);
        }

        // Top Artists
        const artistMap = new Map<string, ArtistCard>();
        tracks.forEach(t => {
          this.artistDisplayParts(t.artist || '').forEach(artistName => {
            const profile = this.findProfileForArtist(artistName, profileByKey);
            this.addArtistTrack(artistMap, t.nasPathId ?? pathId, profile?.name || artistName, t, profile);
          });
        });
        profiles.forEach(profile => {
          const key = this.key(profile.name);
          if (!artistMap.has(key)) {
            const placeholder = this.placeholderTrack(profile.name);
            artistMap.set(key, { artist: profile.name, track: placeholder, pathId, tracks: [], profile, imageUrl: this.profileImage(profile) });
          } else {
            const card = artistMap.get(key)!;
            card.profile = profile;
            card.imageUrl = this.profileImage(profile);
          }
        });
        const playCountMap = new Map<string, number>();
        (topArtists as { artist: string; playCount: number }[]).forEach(entry => {
          playCountMap.set(this.key(entry.artist), entry.playCount);
        });
        if (!existingAccount) {
          items.forEach((item: any) => this.artistDisplayParts(item.artist || '').forEach(name => {
            const key = this.key(name);
            if (key && !playCountMap.has(key)) playCountMap.set(key, 1);
          }));
          favorites.forEach((favorite: any) => this.artistDisplayParts(favorite.artist || '').forEach(name => {
            const key = this.key(name);
            if (key) playCountMap.set(key, (playCountMap.get(key) || 0) + 2);
          }));
        }
        const globalArtistCounts = new Map<string, number>();
        community.topArtists.forEach(entry => this.artistDisplayParts(entry.artist).forEach(name => {
          const key = this.key(name);
          if (key) globalArtistCounts.set(key, (globalArtistCounts.get(key) || 0) + entry.playCount);
        }));
        const maxOwnArtist = this.maxCount(Array.from(playCountMap.values()));
        const maxGlobalArtist = this.maxCount(Array.from(globalArtistCounts.values()));
        this.topArtists = Array.from(artistMap.values())
          .sort((a, b) => {
            const pa = playCountMap.get(this.key(a.artist)) ?? 0;
            const pb = playCountMap.get(this.key(b.artist)) ?? 0;
            if (!existingAccount) {
              const score = this.blendedScore(pa, globalArtistCounts.get(this.key(a.artist)) || 0, personalWeight, maxOwnArtist, maxGlobalArtist)
                - this.blendedScore(pb, globalArtistCounts.get(this.key(b.artist)) || 0, personalWeight, maxOwnArtist, maxGlobalArtist);
              if (score !== 0) return score;
            } else if (pa !== pb) {
              return pb - pa;
            }
            return b.tracks.length - a.tracks.length || a.artist.localeCompare(b.artist);
          })
          .slice(0, 14);
        this.resolveAutoArtistImages();

        // Recently Added = latest albums by lastModified
        const recentMap = new Map<string, AlbumCard>();
        const recentSource = (recent?.length ? recent : tracks);
        [...recentSource]
          .sort((a, b) => (b.lastModified || '').localeCompare(a.lastModified || ''))
          .forEach(t => {
            const key = (t.album || t.title || '').trim();
            if (key && !recentMap.has(key)) {
              recentMap.set(key, { album: t.album || t.title, artist: t.artist, track: t, pathId: t.nasPathId ?? pathId, tracks: [t] });
            }
          });
        this.recentlyAdded = Array.from(recentMap.values()).slice(0, 10);

        // Explore = random album selection
        const exploreMap = new Map<string, AlbumCard>();
        this.pickExploreTracks(tracks).forEach(t => {
          const key = (t.album || t.title || '').trim();
          if (key && !exploreMap.has(key)) {
            exploreMap.set(key, { album: t.album || t.title, artist: t.artist, track: t, pathId: t.nasPathId ?? pathId, tracks: [t] });
          }
        });
        this.newReleases = Array.from(exploreMap.values()).slice(0, 10);
        this.prefetchHomeCovers([...this.recentlyAdded, ...this.newReleases]);
        this.loading = false;
      },
      error: () => {
        forkJoin({
          random: this.music.getRandomTracks(14),
          profiles: this.music.getArtistProfiles()
        }).subscribe({
          next: ({ random, profiles }) => {
            const tracks = random || [];
            if (tracks[0]) this.featured = { track: tracks[0], pathId };
            const m = new Map<string, AlbumCard>();
            tracks.forEach(t => {
              const k = (t.album || t.title).trim();
              if (!m.has(k)) m.set(k, { album: t.album || t.title, artist: t.artist, track: t, pathId, tracks: [t] });
            });
            this.listenNow = Array.from(m.values()).slice(0, 9);
            this.recentlyAdded = [];
            this.newReleases = Array.from(m.values()).slice(0, 10);
            this.prefetchHomeCovers(this.newReleases);
            this.topArtists = profiles.map(profile => ({
              artist: profile.name, track: this.placeholderTrack(profile.name),
              pathId, tracks: [], profile, imageUrl: this.profileImage(profile)
            })).slice(0, 14);
            this.loading = false;
          },
          error: () => { this.loading = false; }
        });
      }
    });
  }

  private placeholderTrack(name: string): MusicMetadataDto {
    return {
      name, path: '', directory: false, size: 0, lastModified: '',
      title: name, artist: name, album: '', duration: 0, format: '', hasCover: false, bpm: 0, source: 'nas'
    };
  }

  private trackRecommendationKey(title: string, artist: string, album: string): string {
    return `${this.key(title)}|${this.key(artist)}|${this.key(album)}`;
  }

  private albumRecommendationKey(artist: string, album: string): string {
    return `${this.key(artist)}|${this.key(album)}`;
  }

  private maxCount(values: number[]): number {
    return values.reduce((max, value) => Math.max(max, value || 0), 0);
  }

  private blendedScore(own: number, community: number, personalWeight: number, maxOwn: number, maxCommunity: number): number {
    const ownScore = maxOwn ? own / maxOwn : 0;
    const communityScore = maxCommunity ? community / maxCommunity : 0;
    return ownScore * personalWeight + communityScore * (1 - personalWeight);
  }

  private pickExploreTracks(tracks: MusicMetadataDto[]): MusicMetadataDto[] {
    return [...tracks]
      .sort((a, b) => this.key(`${a.album} ${a.title} ${a.path}`).localeCompare(this.key(`${b.album} ${b.title} ${b.path}`)))
      .slice(0, 80)
      .sort(() => Math.random() - 0.5)
      .slice(0, 14);
  }

  private prefetchHomeCovers(cards: AlbumCard[]) {
    const seen = new Set<string>();
    cards.forEach(card => {
      const path = card.track?.path;
      if (!path || seen.has(path)) return;
      seen.add(path);
      this.music.fetchCoverIfNeeded(card.track);
    });
  }

  private profileImage(profile?: ArtistProfileDto): string {
    if (!profile?.imageUrl) return '';
    return profile.imageUrl.startsWith('http') ? profile.imageUrl : `${this.music.BASE}${profile.imageUrl}`;
  }

  private resolveAutoArtistImages() {
    const candidates = this.topArtists.filter(a => !a.imageUrl && a.tracks.length > 0 && !this.isSuspiciousArtistName(a.artist));
    this.music.resolveArtistImages(candidates);
    this.scheduleImageRetry();
  }

  private scheduleImageRetry() {
    if (this.imageRetryTimer) clearTimeout(this.imageRetryTimer);
    this.imageRetryTimer = setTimeout(() => {
      const missing = this.topArtists.filter(a => !a.imageUrl && !a.autoImageUrl && a.tracks.length > 0 && !this.isSuspiciousArtistName(a.artist));
      if (missing.length > 0) {
        this.music.clearArtistImageCacheFailed();
        this.music.resolveArtistImages(missing);
      }
    }, 12000);
  }

  private profileKeys(profile: ArtistProfileDto): string[] {
    const aliases = (profile.aliases || '').split(/[\n,]+/).map(a => a.trim()).filter(Boolean);
    return [profile.name, ...aliases].map(v => this.key(v)).filter(Boolean);
  }

  private findProfileForArtist(rawArtist: string, profileByKey: Map<string, ArtistProfileDto>): ArtistProfileDto | undefined {
    const exact = profileByKey.get(this.key(rawArtist));
    if (exact) return exact;
    for (const part of this.artistParts(rawArtist)) {
      const profile = profileByKey.get(part);
      if (profile) return profile;
    }
    return undefined;
  }

  private addArtistTrack(map: Map<string, ArtistCard>, pathId: number, artist: string, track: MusicMetadataDto, profile?: ArtistProfileDto) {
    const displayName = artist.trim();
    if (!displayName) return;
    const key = this.key(displayName);
    if (!map.has(key)) {
      map.set(key, { artist: displayName, track, pathId, tracks: [track], profile, imageUrl: this.profileImage(profile) });
      return;
    }
    const card = map.get(key)!;
    if (!card.tracks.some(existing => existing.path === track.path)) card.tracks.push(track);
    if (profile && !card.profile) { card.profile = profile; card.imageUrl = this.profileImage(profile); }
  }

  private artistDisplayParts(value: string): string[] {
    const raw = (value || '').trim();
    if (!raw) return [];
    const parts = raw
      .split(/\s*(?:,|;|&|\+|\/|\bfeat\.?\b|\bft\.?\b|\bcon\b|\band\b| y )\s*/i)
      .map(part => part.trim())
      .filter(Boolean);
    const unique = new Map<string, string>();
    (parts.length ? parts : [raw]).forEach(part => {
      const key = this.key(part);
      if (key && !this.isSuspiciousArtistName(part) && !unique.has(key)) unique.set(key, part);
    });
    return Array.from(unique.values());
  }

  private isSuspiciousArtistName(value: string): boolean {
    const key = this.key(value);
    if (!key) return true;
    return /\b(clean edit|audio edit|extended edit|radio edit|lyrics?|lyric video)\b/.test(key)
      || /\b(vevo|official|topic|records|recordings|music tv|musictv|entertainment|official channel)\b/.test(key)
      || key === 'dj clean edit' || key === 'unknown' || key === 'desconocido';
  }

  coverFor(t: MusicMetadataDto, pid: number): string {
    return this.music.getCoverUrlWithCache(pid, t.path, t.source);
  }

  coverFallbackStyle(title: string, subtitle = ''): Record<string, string> {
    const seed = this.hash(`${title}|${subtitle}`);
    const hueA = seed % 360;
    const hueB = (hueA + 42 + (seed % 70)) % 360;
    const hueC = (hueA + 176) % 360;
    return {
      background: [
        `radial-gradient(circle at 24% 18%, hsl(${hueB} 78% 55% / 0.78), transparent 34%)`,
        `radial-gradient(circle at 82% 70%, hsl(${hueC} 70% 48% / 0.62), transparent 38%)`,
        `linear-gradient(135deg, hsl(${hueA} 54% 24%), hsl(${hueB} 58% 14%))`
      ].join(', ')
    };
  }

  playAlbum(card: AlbumCard) {
    this.music.setQueue(card.pathId, card.tracks, 0);
  }

  playFeatured() {
    if (!this.featured) return;
    this.music.mainPlayer.load(this.featured.track, this.featured.pathId).then(() => this.music.mainPlayer.play());
  }

  openArtist(artist: ArtistCard) {
    this.selectedArtist = artist;
    this.selectedArtistTracks = [];
    this.artistError = '';
    this.artistLoading = true;
    const aliases = (artist.profile?.aliases || '').split(/[\n,]+/).map(a => a.trim()).filter(Boolean);
    this.music.getArtistTracks(artist.pathId, artist.artist, aliases, 1000).subscribe({
      next: tracks => {
        const artistKeys = new Set([artist.artist, ...aliases].map(v => this.key(v)).filter(Boolean));
        this.selectedArtistTracks = tracks.filter(t => this.artistParts(t.artist || '').some(part => artistKeys.has(part)));
        if (!this.selectedArtistTracks.length && artist.tracks.length) this.selectedArtistTracks = artist.tracks;
        if (!this.selectedArtistTracks.length && artist.track.path) this.selectedArtistTracks = [artist.track];
        this.artistLoading = false;
      },
      error: () => {
        this.selectedArtistTracks = artist.tracks.length ? artist.tracks : (artist.track.path ? [artist.track] : []);
        this.artistError = 'MUSIC.MODERN_ARTIST_TRACKS_ERROR';
        this.artistLoading = false;
      }
    });
  }

  closeArtist() { this.selectedArtist = null; this.selectedArtistTracks = []; this.artistError = ''; }

  playArtistAll() {
    if (!this.selectedArtist || !this.selectedArtistTracks.length) return;
    this.music.setQueue(this.selectedArtist.pathId, this.selectedArtistTracks, 0);
  }

  playArtistTrack(index: number) {
    if (!this.selectedArtist || !this.selectedArtistTracks[index]) return;
    this.music.setQueue(this.selectedArtist.pathId, this.selectedArtistTracks, index);
  }

  toggleFavFeatured() {
    if (!this.featured) return;
    const t = this.featured.track;
    this.music.toggleFavorite(t.path, t.title, t.artist, t.album, this.featured.pathId)
      .subscribe({ next: (res: any) => { this.featuredFav = !!res?.isFavorite; }, error: () => {} });
  }

  /** Refresca el estado de "me gusta" del destacado actual. */
  private checkFeaturedFav() {
    const f = this.featured;
    if (!f || !f.track?.path || (f.pathId ?? 0) < 0) { this.featuredFav = false; return; }
    this.music.checkFavorite(f.track.path, f.pathId)
      .subscribe({ next: (res: any) => { this.featuredFav = !!res?.isFavorite; }, error: () => { this.featuredFav = false; } });
  }

  private artistParts(value: string): string[] {
    const full = this.key(value);
    const parts = value
      .split(/\s*(?:,|;|&|\+|\/|\bfeat\.?\b|\bft\.?\b|\bcon\b|\band\b| y )\s*/i)
      .map(part => this.key(part))
      .filter(Boolean);
    return Array.from(new Set([full, ...parts].filter(Boolean)));
  }

  scrollArtists(dir: 1 | -1) {
    const el = this.artistsRowRef?.nativeElement;
    if (el) el.scrollBy({ left: dir * 220, behavior: 'smooth' });
  }

  scrollRecent(dir: 1 | -1) {
    const el = this.recentRowRef?.nativeElement;
    if (el) el.scrollBy({ left: dir * 380, behavior: 'smooth' });
  }

  scrollExplore(dir: 1 | -1) {
    const el = this.exploreRowRef?.nativeElement;
    if (el) el.scrollBy({ left: dir * 460, behavior: 'smooth' });
  }

  scrollYtPlaylists(dir: 1 | -1) {
    const el = this.ytPlaylistsRowRef?.nativeElement;
    if (el) el.scrollBy({ left: dir * 380, behavior: 'smooth' });
  }

  private loadYtPlaylists() {
    this.music.discoverYtMusicHome().subscribe({
      next: res => {
        const playlists: YtMusicDiscoverItemDto[] = [];
        const seen = new Set<string>();
        (res.shelves || []).forEach(shelf => {
          (shelf.items || []).forEach(item => {
            if (item.type === 'PLAYLIST' && item.playlistId && !seen.has(item.playlistId)) {
              seen.add(item.playlistId);
              playlists.push(item);
            }
          });
        });
        this.ytPlaylists = playlists.slice(0, 14);
      },
      error: () => { this.ytPlaylists = []; }
    });
  }

  openYtPlaylist(item: YtMusicDiscoverItemDto) {
    if (!item.playlistId) return;
    this.router.navigate(['/modern/ytmusic'], { queryParams: { playlist: item.playlistId } });
  }

  private key(value: string): string {
    return (value || '')
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private hash(value: string): number {
    let h = 0;
    for (let i = 0; i < value.length; i++) h = Math.imul(31, h) + value.charCodeAt(i) | 0;
    return Math.abs(h);
  }
}
