import { Component, OnInit } from '@angular/core';
import { MusicService, MusicMetadataDto } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';

@Component({
  selector: 'app-modern-favorites',
  templateUrl: './modern-favorites.component.html',
  styleUrls: ['./modern-favorites.component.css']
})
export class ModernFavoritesComponent implements OnInit {
  tracks: MusicMetadataDto[] = [];
  loading = false;

  constructor(public music: MusicService, private state: ModernStateService) {}

  ngOnInit() { this.load(); }

  load() {
    this.loading = true;
    this.music.getFavorites().subscribe({
      next: (items: any[]) => {
        this.tracks = items.map(i => ({
          name: i.title, path: i.trackPath, directory: false, size: 0,
          lastModified: '', title: i.title, artist: i.artist, album: i.album,
          duration: 0, format: '', hasCover: false, bpm: 0, source: 'nas' as const,
          nasPathId: i.nasPathId
        }));
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  play(i: number) {
    const t = this.tracks[i];
    const pid = t.nasPathId ?? this.state.pathId ?? 0;
    this.music.setQueue(pid, this.tracks, i);
  }

  unfavorite(t: MusicMetadataDto) {
    const pid = t.nasPathId ?? this.state.pathId ?? 0;
    this.music.toggleFavorite(t.path, t.title, t.artist, t.album, pid).subscribe(() => this.load());
  }

  cover(t: MusicMetadataDto): string {
    return this.music.getCoverUrlWithCache(t.nasPathId ?? 0, t.path, t.source);
  }
}
