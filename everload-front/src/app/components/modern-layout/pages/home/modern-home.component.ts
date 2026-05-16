import { Component, ElementRef, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { forkJoin, Subscription } from 'rxjs';
import { ArtistProfileDto, MusicService, MusicMetadataDto } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';

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
  listenNow: AlbumCard[] = [];
  topArtists: ArtistCard[] = [];
  recentlyAdded: AlbumCard[] = [];
  newReleases: AlbumCard[] = [];
  selectedArtist: ArtistCard | null = null;
  selectedArtistTracks: MusicMetadataDto[] = [];
  artistLoading = false;
  artistError = '';
  loading = true;

  editMode = false;
  homeSections: HomeSection[] = [];
  listenNowStyle: 'cards' | 'list' = 'cards';

  private sub!: Subscription;
  private indexPoll?: ReturnType<typeof setTimeout>;

  @ViewChild('artistsRow') artistsRowRef?: ElementRef<HTMLElement>;

  constructor(public music: MusicService, private state: ModernStateService) {}

  ngOnInit() {
    this.loadHomeConfig();
    this.sub = this.state.pathId$.subscribe(pid => {
      if (pid != null) this.load(pid);
    });
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
    if (this.indexPoll) clearTimeout(this.indexPoll);
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

  sectionLabel(key: string): string {
    const labels: Record<string, string> = {
      featured: 'Featured',
      listen_now: 'Listen Now',
      top_artists: 'Top Artists',
      recently_added: 'Recently Added',
      explore: 'Explore',
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
      overview: this.music.getLibraryOverview(pathId, 5000),
      profiles: this.music.getArtistProfiles()
    }).subscribe({
      next: ({ history, overview, profiles }) => {
        const items = history || [];
        const tracks = overview.tracks || [];
        if (this.indexPoll) clearTimeout(this.indexPoll);
        if (overview.indexing && tracks.length === 0) {
          this.indexPoll = setTimeout(() => this.load(pathId), 6000);
        }
        const profileByKey = new Map<string, ArtistProfileDto>();
        profiles.forEach(profile => this.profileKeys(profile).forEach(key => profileByKey.set(key, profile)));

        // Featured = most recent
        if (items[0]) {
          this.featured = { track: this.toTrack(items[0], pathId), pathId: items[0].nasPathId ?? pathId };
        } else if (tracks[0]) {
          this.featured = { track: tracks[0], pathId: tracks[0].nasPathId ?? pathId };
        }

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
        this.listenNow = Array.from(albumMap.values()).slice(0, 8);

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
        this.topArtists = Array.from(artistMap.values())
          .sort((a, b) => b.tracks.length - a.tracks.length || a.artist.localeCompare(b.artist))
          .slice(0, 14);
        this.resolveAutoArtistImages();

        // Recently Added = latest albums by lastModified
        const recentMap = new Map<string, AlbumCard>();
        [...tracks]
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
            this.listenNow = Array.from(m.values()).slice(0, 8);
            this.recentlyAdded = [];
            this.newReleases = Array.from(m.values()).slice(0, 10);
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

  private pickExploreTracks(tracks: MusicMetadataDto[]): MusicMetadataDto[] {
    return [...tracks]
      .sort((a, b) => this.key(`${a.album} ${a.title} ${a.path}`).localeCompare(this.key(`${b.album} ${b.title} ${b.path}`)))
      .slice(0, 80)
      .sort(() => Math.random() - 0.5)
      .slice(0, 14);
  }

  private profileImage(profile?: ArtistProfileDto): string {
    if (!profile?.imageUrl) return '';
    return profile.imageUrl.startsWith('http') ? profile.imageUrl : `${this.music.BASE}${profile.imageUrl}`;
  }

  private resolveAutoArtistImages() {
    const candidates = this.topArtists.filter(a => !a.imageUrl && a.tracks.length > 0 && !this.isSuspiciousArtistName(a.artist));
    this.music.resolveArtistImages(candidates);
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
        this.artistError = 'No se pudieron cargar todas las canciones.';
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
    this.music.toggleFavorite(t.path, t.title, t.artist, t.album, this.featured.pathId).subscribe();
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
}
