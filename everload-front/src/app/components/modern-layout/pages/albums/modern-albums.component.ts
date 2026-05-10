import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicService, MusicMetadataDto } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';

export interface AlbumGroup {
  album: string;
  artist: string;
  tracks: MusicMetadataDto[];
  cover: MusicMetadataDto;
  pathId: number;
}

@Component({
  selector: 'app-modern-albums',
  templateUrl: './modern-albums.component.html',
  styleUrls: ['./modern-albums.component.css']
})
export class ModernAlbumsComponent implements OnInit, OnDestroy {
  albums: AlbumGroup[] = [];
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
        const map = new Map<string, AlbumGroup>();
        tracks.forEach(t => {
          const key = (t.album || t.title || '').trim();
          if (!map.has(key)) {
            map.set(key, { album: t.album || t.title, artist: t.artist, tracks: [t], cover: t, pathId });
          } else {
            map.get(key)!.tracks.push(t);
          }
        });
        this.albums = Array.from(map.values()).sort((a, b) => a.album.localeCompare(b.album));
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  cover(g: AlbumGroup): string {
    return this.music.getCoverUrlWithCache(g.pathId, g.cover.path, g.cover.source);
  }

  play(g: AlbumGroup) {
    this.music.setQueue(g.pathId, g.tracks, 0);
  }
}
