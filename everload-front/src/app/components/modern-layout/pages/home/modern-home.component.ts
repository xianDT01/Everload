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
  count: number;
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
  recentTracks: MusicMetadataDto[] = [];
  loading = true;

  private sub!: Subscription;

  constructor(public music: MusicService, private state: ModernStateService) {}

  ngOnInit() {
    this.sub = this.state.pathId$.subscribe(pid => {
      if (pid != null) this.load(pid);
    });
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }

  private load(pathId: number) {
    this.loading = true;

    this.music.getHistory(30).subscribe({
      next: (items: any[]) => {
        this.recentTracks = items.slice(0, 10).map(i => ({
          name: i.title, path: i.trackPath, directory: false, size: 0,
          lastModified: '', title: i.title, artist: i.artist, album: i.album,
          duration: 0, format: '', hasCover: false, bpm: 0, source: 'nas' as const,
          nasPathId: i.nasPathId ?? pathId
        }));

        if (items.length > 0) {
          const fi = items[0];
          const ft: MusicMetadataDto = {
            name: fi.title, path: fi.trackPath, directory: false, size: 0,
            lastModified: '', title: fi.title, artist: fi.artist, album: fi.album,
            duration: 0, format: '', hasCover: false, bpm: 0, source: 'nas',
            nasPathId: fi.nasPathId ?? pathId
          };
          this.featured = { track: ft, pathId: fi.nasPathId ?? pathId };
        }

        const byAlbum = new Map<string, AlbumCard>();
        items.forEach((i: any) => {
          const key = (i.album || i.title || '').trim();
          if (key && !byAlbum.has(key)) {
            const t: MusicMetadataDto = {
              name: i.title, path: i.trackPath, directory: false, size: 0,
              lastModified: '', title: i.title, artist: i.artist, album: i.album,
              duration: 0, format: '', hasCover: false, bpm: 0, source: 'nas',
              nasPathId: i.nasPathId ?? pathId
            };
            byAlbum.set(key, { album: i.album || i.title, artist: i.artist, track: t, pathId: i.nasPathId ?? pathId, tracks: [t] });
          }
        });
        this.listenNow = Array.from(byAlbum.values()).slice(0, 8);

        const byArtist = new Map<string, ArtistCard>();
        items.forEach((i: any) => {
          const a = (i.artist || '').trim();
          if (!a) return;
          if (!byArtist.has(a)) {
            const t: MusicMetadataDto = {
              name: i.title, path: i.trackPath, directory: false, size: 0,
              lastModified: '', title: i.title, artist: i.artist, album: i.album,
              duration: 0, format: '', hasCover: false, bpm: 0, source: 'nas',
              nasPathId: i.nasPathId ?? pathId
            };
            byArtist.set(a, { artist: a, track: t, pathId: i.nasPathId ?? pathId, count: 1 });
          } else {
            byArtist.get(a)!.count++;
          }
        });
        this.topArtists = Array.from(byArtist.values()).slice(0, 8);
        this.loading = false;
      },
      error: () => {
        this.music.getRandomTracks(8).subscribe({
          next: tracks => {
            this.listenNow = tracks.map(t => ({
              album: t.album || t.title, artist: t.artist, track: t, pathId, tracks: [t]
            }));
            if (tracks[0]) this.featured = { track: tracks[0], pathId };
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
    this.music.mainPlayer.load(card.track, card.pathId);
    this.music.setQueue(card.pathId, card.tracks, 0);
  }

  playFeatured() {
    if (!this.featured) return;
    this.music.mainPlayer.load(this.featured.track, this.featured.pathId);
  }
}
