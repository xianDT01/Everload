import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { NasPath, NasService } from '../../../services/nas.service';
import { MusicMetadataDto, MusicService, PlayerState } from '../../../services/music.service';

@Component({
  selector: 'app-library-mode',
  templateUrl: './library-mode.component.html',
  styleUrls: ['./library-mode.component.css']
})
export class LibraryModeComponent implements OnInit, OnDestroy {

  paths: NasPath[] = [];
  selectedPathId: number | null = null;
  currentSubPath = '';
  items: MusicMetadataDto[] = [];
  state: PlayerState | null = null;

  private sub!: Subscription;

  constructor(public musicService: MusicService, private nasService: NasService) {}

  ngOnInit(): void {
    this.nasService.getPaths().subscribe(paths => {
      this.paths = paths;
      if (paths.length > 0) this.selectPath(paths[0].id);
    });
    this.sub = this.musicService.mainPlayer.state$.subscribe(s => this.state = s);
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  // ── Navigation ────────────────────────────────────────────────────────────

  selectPath(id: number) {
    this.selectedPathId = id;
    this.currentSubPath = '';
    this.load();
  }

  load() {
    if (this.selectedPathId === null) return;
    this.musicService.browse(this.selectedPathId, this.currentSubPath).subscribe(items => {
      this.items = items;
    });
  }

  navigate(item: MusicMetadataDto) {
    if (!item.directory) return;
    this.currentSubPath = item.path;
    this.load();
  }

  goUp() {
    if (!this.currentSubPath) return;
    const parts = this.currentSubPath.split(/[/\\]/).filter(Boolean);
    parts.pop();
    this.currentSubPath = parts.join('/');
    this.load();
  }

  get isRoot() { return !this.currentSubPath; }

  get folders(): MusicMetadataDto[] { return this.items.filter(i => i.directory); }
  get tracks():  MusicMetadataDto[] { return this.items.filter(i => !i.directory); }

  get currentFolderName(): string {
    if (!this.currentSubPath) {
      return this.paths.find(p => p.id === this.selectedPathId)?.name ?? 'Biblioteca';
    }
    const parts = this.currentSubPath.split(/[/\\]/).filter(Boolean);
    return parts[parts.length - 1] || 'Biblioteca';
  }

  // ── Playback ──────────────────────────────────────────────────────────────

  playTrack(track: MusicMetadataDto) {
    if (!this.selectedPathId) return;
    const idx = this.tracks.findIndex(t => t.path === track.path);
    this.musicService.setQueue(this.selectedPathId, this.tracks, idx);
  }

  isCurrentTrack(track: MusicMetadataDto): boolean {
    return this.state?.currentTrack?.path === track.path;
  }

  togglePlay()  { this.musicService.mainPlayer.togglePlay(); }
  next()        { this.musicService.playNextMain(); }
  prev()        { this.musicService.playPrevMain(); }
  onSeek(e: Event)   { this.musicService.mainPlayer.seek(+(e.target as HTMLInputElement).value); }
  onVolume(e: Event) { this.musicService.mainPlayer.setVolume(+(e.target as HTMLInputElement).value); }

  // ── Helpers ───────────────────────────────────────────────────────────────

  coverUrl(track: MusicMetadataDto): string {
    if (!track.hasCover || !this.selectedPathId) return '';
    // Use pathId from player state for the currently-playing track
    const pid = (this.state?.currentTrack?.path === track.path && this.state?.pathId)
                ? this.state.pathId
                : this.selectedPathId;
    return this.musicService.getCoverUrl(pid, track.path);
  }

  playerCoverUrl(): string {
    const t = this.state?.currentTrack;
    if (!t?.hasCover || !this.state?.pathId) return '';
    return this.musicService.getCoverUrl(this.state.pathId, t.path);
  }

  fmt(seconds: number): string {
    if (!seconds || seconds <= 0) return '—';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  }

  fmtClass(format: string): string {
    return (format || '').toLowerCase();
  }

  progressPct(): number {
    if (!this.state?.duration) return 0;
    return (this.state.currentTime / this.state.duration) * 100;
  }
}
