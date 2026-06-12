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
  selected: AlbumGroup | null = null;
  playlists: any[] = [];
  playlistPickerTrack: MusicMetadataDto | null = null;
  loading = false;
  pathId: number | null = null;
  private sub!: Subscription;

  constructor(public music: MusicService, private state: ModernStateService) {}

  ngOnInit() {
    this.sub = this.state.pathId$.subscribe(pid => {
      this.pathId = pid;
      this.selected = null;
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
        const map = new Map<string, AlbumGroup>();
        tracks.forEach(t => {
          const key = (t.album || '').trim();
          if (!key) return;
          if (!map.has(key)) {
            map.set(key, { album: t.album, artist: t.artist, tracks: [t], cover: t, pathId });
          } else {
            const g = map.get(key)!;
            g.tracks.push(t);
            if (!g.cover.hasCover && t.hasCover) g.cover = t;
          }
        });
        this.albums = Array.from(map.values()).sort((a, b) => a.album.localeCompare(b.album));
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  openDetail(g: AlbumGroup) {
    this.selected = g;
  }

  back() {
    this.selected = null;
  }

  play(g: AlbumGroup) {
    this.music.setQueue(g.pathId, g.tracks, 0);
  }

  playFrom(index: number) {
    if (!this.selected) return;
    this.music.setQueue(this.selected.pathId, this.selected.tracks, index);
  }

  appendTrack(t: MusicMetadataDto) {
    if (!this.selected) return;
    const q = this.music.queueSnapshot;
    const pathId = this.selected.pathId;
    const tracks = q.pathId === pathId ? [...q.tracks, t] : [t];
    const index = q.pathId === pathId && q.index >= 0 ? q.index : 0;
    if (q.pathId === pathId && q.tracks.length) {
      this.music.updateQueue(pathId, tracks, index);
    } else {
      this.music.setQueue(pathId, [t], 0);
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
    if (!this.selected || !this.playlistPickerTrack || this.isTrackInPlaylist(pl, this.playlistPickerTrack)) return;
    const track = this.playlistPickerTrack;
    this.music.addTrackToPlaylist(pl.id, track, track.nasPathId ?? this.selected.pathId).subscribe({
      next: () => {
        this.closePlaylistPicker();
        this.loadPlaylists();
      }
    });
  }

  isTrackInPlaylist(pl: any, track: MusicMetadataDto | null = this.playlistPickerTrack): boolean {
    if (!this.selected || !pl || !track) return false;
    const pid = track.nasPathId ?? this.selected.pathId;
    return (pl.tracks ?? []).some((t: any) => t.trackPath === track.path && t.nasPathId === pid);
  }

  cover(g: AlbumGroup): string {
    return this.music.getCoverUrlWithCache(g.pathId, g.cover.path, g.cover.source);
  }

  trackCover(t: MusicMetadataDto): string {
    if (!this.selected) return '';
    return this.music.getCoverUrlWithCache(this.selected.pathId, t.path, t.source);
  }

  fmt(seconds: number | undefined): string {
    const s = Number(seconds || 0);
    if (!s || !isFinite(s)) return '';
    return `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;
  }

  trackByPath(_: number, t: MusicMetadataDto): string { return t.path; }
}
