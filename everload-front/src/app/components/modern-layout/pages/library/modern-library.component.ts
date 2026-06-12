import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicService, MusicMetadataDto } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';

type SortCol = 'title' | 'artist' | 'album' | 'duration';

@Component({
  selector: 'app-modern-library',
  templateUrl: './modern-library.component.html',
  styleUrls: ['./modern-library.component.css']
})
export class ModernLibraryComponent implements OnInit, OnDestroy {
  tracks: MusicMetadataDto[] = [];
  playlists: any[] = [];
  playlistPickerTrack: MusicMetadataDto | null = null;
  loading = false;
  query = '';
  pathId: number | null = null;

  sortCol: SortCol | null = null;
  sortDir: 'asc' | 'desc' = 'asc';

  private allTracks: MusicMetadataDto[] = [];
  private sub!: Subscription;

  constructor(public music: MusicService, private state: ModernStateService) {}

  ngOnInit() {
    this.sub = this.state.pathId$.subscribe(pid => {
      this.pathId = pid;
      this.sortCol = null;
      this.sortDir = 'asc';
      this.query = '';
      if (pid != null) {
        this.load(pid);
        this.loadPlaylists();
      }
    });
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }

  private load(pathId: number) {
    this.loading = true;
    this.state.getOverview(pathId).subscribe({
      next: ({ tracks }) => {
        this.allTracks = tracks;
        this.applyFilterSort();
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  onSearch() { this.applyFilterSort(); }

  sortBy(col: SortCol) {
    if (this.sortCol === col) {
      this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortCol = col;
      this.sortDir = 'asc';
    }
    this.applyFilterSort();
  }

  private applyFilterSort() {
    let result = this.allTracks;
    const q = this.query.trim().toLowerCase();
    if (q) {
      result = result.filter(t =>
        (t.title || t.name || '').toLowerCase().includes(q) ||
        (t.artist || '').toLowerCase().includes(q) ||
        (t.album || '').toLowerCase().includes(q)
      );
    }
    if (this.sortCol) {
      const col = this.sortCol;
      const dir = this.sortDir === 'asc' ? 1 : -1;
      result = [...result].sort((a, b) => {
        let va: string | number, vb: string | number;
        switch (col) {
          case 'title':    va = (a.title || a.name || '').toLowerCase(); vb = (b.title || b.name || '').toLowerCase(); break;
          case 'artist':   va = (a.artist || '').toLowerCase();          vb = (b.artist || '').toLowerCase();          break;
          case 'album':    va = (a.album || '').toLowerCase();           vb = (b.album || '').toLowerCase();           break;
          case 'duration': va = a.duration || 0;                         vb = b.duration || 0;                         break;
        }
        return va! < vb! ? -dir : va! > vb! ? dir : 0;
      });
    }
    this.tracks = result;
  }

  play(index: number) {
    if (this.pathId == null) return;
    this.music.setQueue(this.pathId, this.tracks, index);
  }

  playAll() {
    if (this.pathId == null || !this.tracks.length) return;
    this.music.setQueue(this.pathId, this.tracks, 0);
  }

  shuffle() {
    if (this.pathId == null || !this.tracks.length) return;
    const shuffled = [...this.tracks].sort(() => Math.random() - 0.5);
    this.music.setQueue(this.pathId, shuffled, 0);
  }

  appendTrack(t: MusicMetadataDto) {
    if (this.pathId == null) return;
    const q = this.music.queueSnapshot;
    const pid = this.pathId;
    if (q.pathId === pid && q.tracks.length) {
      this.music.updateQueue(pid, [...q.tracks, t], q.index);
    } else {
      this.music.setQueue(pid, [t], 0);
    }
  }

  loadPlaylists() {
    this.music.getPlaylists().subscribe({ next: playlists => { this.playlists = playlists || []; } });
  }

  openPlaylistPicker(t: MusicMetadataDto, event: Event) {
    event.stopPropagation();
    this.playlistPickerTrack = t;
    if (!this.playlists.length) this.loadPlaylists();
  }

  closePlaylistPicker() {
    this.playlistPickerTrack = null;
  }

  addToPlaylist(pl: any) {
    if (!this.playlistPickerTrack || this.pathId == null || this.isTrackInPlaylist(pl, this.playlistPickerTrack)) return;
    const track = this.playlistPickerTrack;
    this.music.addTrackToPlaylist(pl.id, track, track.nasPathId ?? this.pathId).subscribe({
      next: () => {
        this.closePlaylistPicker();
        this.loadPlaylists();
      }
    });
  }

  isTrackInPlaylist(pl: any, track: MusicMetadataDto | null = this.playlistPickerTrack): boolean {
    if (!pl || !track) return false;
    const pid = track.nasPathId ?? this.pathId;
    return (pl.tracks ?? []).some((t: any) => t.trackPath === track.path && (pid == null || t.nasPathId === pid));
  }

  fmt(s: number): string {
    if (!s || !isFinite(s)) return '';
    return `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;
  }

  cover(t: MusicMetadataDto): string {
    return this.music.getCoverUrlWithCache(this.pathId ?? 0, t.path, t.source);
  }

  isPlaying(t: MusicMetadataDto): boolean {
    return this.music.mainPlayer.state.currentTrack?.path === t.path;
  }

  trackByPath(_: number, t: MusicMetadataDto): string { return t.path; }
}
