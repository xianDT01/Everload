import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription, from, of } from 'rxjs';
import { catchError, concatMap, finalize, tap } from 'rxjs/operators';
import { MusicService, MusicMetadataDto } from '../../../../services/music.service';
import { NasService } from '../../../../services/nas.service';
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
  selectedPaths = new Set<string>();
  metadataEditorOpen = false;
  metadataSaving = false;
  metadataProgress = '';
  metadataError = '';
  bulkFields = { title: false, artist: false, album: false };
  bulkValues = { title: '', artist: '', album: '' };

  sortCol: SortCol | null = null;
  sortDir: 'asc' | 'desc' = 'asc';

  private allTracks: MusicMetadataDto[] = [];
  private sub!: Subscription;
  private searchTimer?: ReturnType<typeof setTimeout>;

  constructor(public music: MusicService, private state: ModernStateService, private nas: NasService) {}

  ngOnInit() {
    this.sub = this.state.pathId$.subscribe(pid => {
      this.pathId = pid;
      this.sortCol = null;
      this.sortDir = 'asc';
      this.query = '';
      this.selectedPaths.clear();
      if (pid != null) {
        this.load(pid);
        this.loadPlaylists();
      }
    });
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
    if (this.searchTimer) clearTimeout(this.searchTimer);
  }

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

  onSearch() {
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.applyFilterSort(), 80);
  }

  isSelected(track: MusicMetadataDto): boolean { return this.selectedPaths.has(track.path); }

  toggleSelected(track: MusicMetadataDto, event: Event) {
    event.stopPropagation();
    if (this.selectedPaths.has(track.path)) this.selectedPaths.delete(track.path);
    else this.selectedPaths.add(track.path);
  }

  toggleVisibleSelection(event: Event) {
    const checked = (event.target as HTMLInputElement).checked;
    this.tracks.forEach(track => checked ? this.selectedPaths.add(track.path) : this.selectedPaths.delete(track.path));
  }

  get selectedTracks(): MusicMetadataDto[] {
    return this.allTracks.filter(track => this.selectedPaths.has(track.path));
  }

  get visibleSelectionComplete(): boolean {
    return this.tracks.length > 0 && this.tracks.every(track => this.selectedPaths.has(track.path));
  }

  openMetadataEditor() {
    if (!this.selectedPaths.size || this.selectedPaths.size > 100) return;
    this.bulkFields = { title: false, artist: false, album: false };
    this.bulkValues = { title: '', artist: '', album: '' };
    this.metadataError = '';
    this.metadataProgress = '';
    this.metadataEditorOpen = true;
  }

  saveBulkMetadata() {
    const fields = (Object.keys(this.bulkFields) as Array<keyof typeof this.bulkFields>)
      .filter(field => this.bulkFields[field]);
    if (!fields.length || fields.some(field => !this.bulkValues[field].trim()) || this.pathId == null) return;

    const tracks = this.selectedTracks;
    this.metadataSaving = true;
    let saved = 0;
    let failed = 0;
    from(tracks).pipe(
      concatMap(track => this.nas.updateMetadata(
        track.nasPathId ?? this.pathId!,
        track.path,
        this.bulkFields.title ? this.bulkValues.title.trim() : (track.title || track.name),
        this.bulkFields.artist ? this.bulkValues.artist.trim() : (track.artist || ''),
        this.bulkFields.album ? this.bulkValues.album.trim() : (track.album || '')
      ).pipe(
        tap(() => saved++),
        catchError(() => { failed++; return of(null); }),
        tap(() => this.metadataProgress = `${saved + failed}/${tracks.length}`)
      )),
      finalize(() => {
        this.metadataSaving = false;
        this.metadataEditorOpen = false;
        this.metadataProgress = '';
        this.selectedPaths.clear();
        if (this.pathId != null) this.load(this.pathId);
        if (failed) this.metadataError = `Se actualizaron ${saved} pistas; ${failed} fallaron.`;
      })
    ).subscribe();
  }

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
