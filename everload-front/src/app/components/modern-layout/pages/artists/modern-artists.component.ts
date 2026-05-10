import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicService, MusicMetadataDto } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';

interface ArtistGroup {
  artist: string;
  tracks: MusicMetadataDto[];
  cover: MusicMetadataDto;
  pathId: number;
  albumCount: number;
}

@Component({
  selector: 'app-modern-artists',
  templateUrl: './modern-artists.component.html',
  styleUrls: ['./modern-artists.component.css']
})
export class ModernArtistsComponent implements OnInit, OnDestroy {
  artists: ArtistGroup[] = [];
  loading = false;
  pathId: number | null = null;
  private sub!: Subscription;

  constructor(public music: MusicService, private state: ModernStateService) {}

  ngOnInit() {
    this.sub = this.state.pathId$.subscribe(pid => {
      this.pathId = pid;
      if (pid != null) this.load(pid);
    });
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }

  private load(pathId: number) {
    this.loading = true;
    this.music.search(pathId, undefined, ' ', 500).subscribe({
      next: tracks => {
        const map = new Map<string, ArtistGroup>();
        tracks.forEach(t => {
          const key = (t.artist || '').trim() || 'Desconocido';
          if (!map.has(key)) {
            map.set(key, { artist: key, tracks: [t], cover: t, pathId, albumCount: 1 });
          } else {
            const g = map.get(key)!;
            g.tracks.push(t);
            if (t.album && !g.tracks.some(x => x.album === t.album)) g.albumCount++;
          }
        });
        this.artists = Array.from(map.values()).sort((a, b) => a.artist.localeCompare(b.artist));
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  cover(g: ArtistGroup): string {
    return this.music.getCoverUrlWithCache(g.pathId, g.cover.path, g.cover.source);
  }

  play(g: ArtistGroup) {
    this.music.setQueue(g.pathId, g.tracks, 0);
  }
}
