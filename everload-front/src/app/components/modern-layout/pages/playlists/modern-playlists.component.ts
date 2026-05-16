import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicService, MusicMetadataDto } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';

@Component({
  selector: 'app-modern-playlists',
  templateUrl: './modern-playlists.component.html',
  styleUrls: ['./modern-playlists.component.css']
})
export class ModernPlaylistsComponent implements OnInit, OnDestroy {
  playlists: any[] = [];
  loading = false;
  newName = '';
  creating = false;

  view: 'list' | 'detail' = 'list';
  selectedPlaylist: any | null = null;

  searchQuery = '';
  searchResults: MusicMetadataDto[] = [];
  searching = false;
  private searchDebounce: any;
  private searchSub?: Subscription;

  constructor(public music: MusicService, public state: ModernStateService) {}

  ngOnInit() { this.load(); }

  ngOnDestroy() {
    this.searchSub?.unsubscribe();
    clearTimeout(this.searchDebounce);
  }

  load() {
    this.loading = true;
    this.music.getPlaylists().subscribe({
      next: p => {
        this.playlists = p;
        this.loading = false;
        if (this.selectedPlaylist) {
          this.selectedPlaylist = p.find((pl: any) => pl.id === this.selectedPlaylist.id) ?? this.selectedPlaylist;
        }
      },
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

  openPlaylist(pl: any) {
    this.selectedPlaylist = pl;
    this.view = 'detail';
    this.searchQuery = '';
    this.searchResults = [];
  }

  back() {
    this.view = 'list';
    this.selectedPlaylist = null;
  }

  play(pl: any) {
    const pid = this.state.pathId;
    if (!pl.tracks?.length || pid == null) return;
    const tracks = pl.tracks.map((t: any) => ({
      name: t.title, path: t.trackPath, directory: false, size: 0,
      lastModified: '', title: t.title, artist: t.artist, album: t.album,
      duration: t.durationSeconds ?? 0, format: '', hasCover: false, bpm: 0,
      source: 'nas' as const, nasPathId: t.nasPathId ?? pid
    }));
    this.music.setQueue(pid, tracks, 0);
  }

  removeTrack(trackId: number) {
    if (!this.selectedPlaylist) return;
    this.music.removeTrackFromPlaylist(this.selectedPlaylist.id, trackId).subscribe(() => this.load());
  }

  onSearchInput() {
    clearTimeout(this.searchDebounce);
    if (!this.searchQuery.trim()) { this.searchResults = []; return; }
    this.searchDebounce = setTimeout(() => this.doSearch(), 250);
  }

  doSearch() {
    const pid = this.state.pathId;
    if (!this.searchQuery.trim() || pid == null) return;
    this.searchSub?.unsubscribe();
    this.searching = true;
    this.searchSub = this.music.search(pid, undefined, this.searchQuery, 50).subscribe({
      next: r => { this.searchResults = r; this.searching = false; },
      error: () => { this.searching = false; }
    });
  }

  addTrack(track: MusicMetadataDto) {
    const pid = this.state.pathId;
    if (!this.selectedPlaylist || pid == null) return;
    this.music.addTrackToPlaylist(this.selectedPlaylist.id, track, track.nasPathId ?? pid).subscribe(() => this.load());
  }

  trackCoverUrl(t: any): string {
    return this.music.getCoverUrlWithCache(t.nasPathId ?? this.state.pathId ?? 0, t.trackPath, 'nas');
  }

  searchCoverUrl(t: MusicMetadataDto): string {
    return this.music.getCoverUrlWithCache(t.nasPathId ?? this.state.pathId ?? 0, t.path, t.source);
  }

  coverTracks(pl: any): any[] {
    return (pl.tracks ?? []).slice(0, 4);
  }

  isInPlaylist(track: MusicMetadataDto): boolean {
    return (this.selectedPlaylist?.tracks ?? []).some((t: any) => t.trackPath === track.path);
  }

  fmtDur(s: number): string {
    if (!s || !isFinite(s)) return '';
    return `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;
  }
}
