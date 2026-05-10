import { Component, OnInit } from '@angular/core';
import { MusicService } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';

@Component({
  selector: 'app-modern-playlists',
  templateUrl: './modern-playlists.component.html',
  styleUrls: ['./modern-playlists.component.css']
})
export class ModernPlaylistsComponent implements OnInit {
  playlists: any[] = [];
  loading = false;
  newName = '';
  creating = false;

  constructor(public music: MusicService, private state: ModernStateService) {}

  ngOnInit() { this.load(); }

  load() {
    this.loading = true;
    this.music.getPlaylists().subscribe({
      next: p => { this.playlists = p; this.loading = false; },
      error: () => { this.loading = false; }
    });
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

  play(pl: any) {
    const pid = this.state.pathId;
    if (!pl.tracks?.length || pid == null) return;
    const tracks = pl.tracks.map((t: any) => ({
      name: t.title, path: t.trackPath, directory: false, size: 0,
      lastModified: '', title: t.title, artist: t.artist, album: t.album,
      duration: 0, format: '', hasCover: false, bpm: 0, source: 'nas' as const,
      nasPathId: t.nasPathId ?? pid
    }));
    this.music.setQueue(pid, tracks, 0);
  }
}
