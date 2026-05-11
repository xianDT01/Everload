import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicService, MusicMetadataDto } from '../../../../services/music.service';
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
}

@Component({
  selector: 'app-modern-home',
  templateUrl: './modern-home.component.html',
  styleUrls: ['./modern-home.component.css']
})
export class ModernHomeComponent implements OnInit, OnDestroy {
  featured: { track: MusicMetadataDto; pathId: number } | null = null;
  listenNow: AlbumCard[] = [];
  topArtists: ArtistCard[] = [];
  newReleases: AlbumCard[] = [];
  selectedArtist: ArtistCard | null = null;
  selectedArtistTracks: MusicMetadataDto[] = [];
  artistLoading = false;
  artistError = '';
  loading = true;
  private sub!: Subscription;

  constructor(public music: MusicService, private state: ModernStateService) {}

  ngOnInit() {
    this.sub = this.state.pathId$.subscribe(pid => {
      if (pid != null) this.load(pid);
    });
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }

  private toTrack(i: any, pathId: number): MusicMetadataDto {
    return {
      name: i.title, path: i.trackPath, directory: false, size: 0,
      lastModified: '', title: i.title, artist: i.artist, album: i.album,
      duration: 0, format: '', hasCover: false, bpm: 0, source: 'nas' as const,
      nasPathId: i.nasPathId ?? pathId
    };
  }

  private load(pathId: number) {
    this.loading = true;

    this.music.getHistory(24).subscribe({
      next: (items: any[]) => {
        // Featured = most recent
        if (items[0]) {
          this.featured = { track: this.toTrack(items[0], pathId), pathId: items[0].nasPathId ?? pathId };
        }

        // Listen Now = unique albums from history (horizontal cards)
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
        this.listenNow = Array.from(albumMap.values()).slice(0, 8);

        // Top Artists = unique artists from history
        const artistMap = new Map<string, ArtistCard>();
        items.forEach((i: any) => {
          const a = (i.artist || '').trim();
          if (a && !artistMap.has(a)) {
            artistMap.set(a, { artist: a, track: this.toTrack(i, pathId), pathId: i.nasPathId ?? pathId });
          }
        });
        this.topArtists = Array.from(artistMap.values()).slice(0, 10);
        this.loading = false;

        // New releases / explore = random tracks grouped by album
        this.music.getRandomTracks(14).subscribe({
          next: tracks => {
            const randMap = new Map<string, AlbumCard>();
            tracks.forEach(t => {
              const key = (t.album || t.title || '').trim();
              if (key && !randMap.has(key)) {
                randMap.set(key, { album: t.album || t.title, artist: t.artist, track: t, pathId, tracks: [t] });
              }
            });
            this.newReleases = Array.from(randMap.values()).slice(0, 10);
          },
          error: () => {}
        });
      },
      error: () => {
        this.music.getRandomTracks(14).subscribe({
          next: tracks => {
            if (tracks[0]) this.featured = { track: tracks[0], pathId };
            const m = new Map<string, AlbumCard>();
            tracks.forEach(t => {
              const k = (t.album || t.title).trim();
              if (!m.has(k)) m.set(k, { album: t.album || t.title, artist: t.artist, track: t, pathId, tracks: [t] });
            });
            this.listenNow = Array.from(m.values()).slice(0, 8);
            this.newReleases = Array.from(m.values()).slice(0, 10);
            this.loading = false;
          },
          error: () => { this.loading = false; }
        });
      }
    });
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

    this.music.search(artist.pathId, undefined, artist.artist, 500).subscribe({
      next: tracks => {
        const artistKey = this.key(artist.artist);
        this.selectedArtistTracks = tracks.filter(t => this.artistParts(t.artist || '').includes(artistKey));
        if (!this.selectedArtistTracks.length) this.selectedArtistTracks = [artist.track];
        this.artistLoading = false;
      },
      error: () => {
        this.selectedArtistTracks = [artist.track];
        this.artistError = 'No se pudieron cargar todas las canciones.';
        this.artistLoading = false;
      }
    });
  }

  closeArtist() {
    this.selectedArtist = null;
    this.selectedArtistTracks = [];
    this.artistError = '';
  }

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

  private key(value: string): string {
    return (value || '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }
}
