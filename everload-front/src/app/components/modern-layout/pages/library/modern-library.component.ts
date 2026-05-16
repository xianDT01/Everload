import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicService, MusicMetadataDto } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';

@Component({
  selector: 'app-modern-library',
  templateUrl: './modern-library.component.html',
  styleUrls: ['./modern-library.component.css']
})
export class ModernLibraryComponent implements OnInit, OnDestroy {
  tracks: MusicMetadataDto[] = [];
  loading = false;
  query = '';
  pathId: number | null = null;
  private sub!: Subscription;

  constructor(public music: MusicService, private state: ModernStateService) {}

  ngOnInit() {
    this.sub = this.state.pathId$.subscribe(pid => {
      this.pathId = pid;
      if (pid != null) this.search('');
    });
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }

  search(q: string) {
    if (this.pathId == null) return;
    this.loading = true;
    const pid = this.pathId;
    if (!q.trim()) {
      this.music.getLibraryOverview(pid, 3000).subscribe({
        next: ({ tracks }) => { this.tracks = tracks; this.loading = false; },
        error: () => { this.loading = false; }
      });
    } else {
      this.music.search(pid, undefined, q, 300).subscribe({
        next: tracks => { this.tracks = tracks; this.loading = false; },
        error: () => { this.loading = false; }
      });
    }
  }

  onSearch() { this.search(this.query); }

  play(index: number) {
    if (this.pathId == null) return;
    const t = this.tracks[index];
    this.music.setQueue(this.pathId, this.tracks, index);
  }

  fmt(s: number): string {
    if (!s || !isFinite(s)) return '';
    const m = Math.floor(s / 60);
    const sec = Math.floor(s % 60);
    return `${m}:${sec.toString().padStart(2, '0')}`;
  }

  cover(t: MusicMetadataDto): string {
    return this.music.getCoverUrlWithCache(this.pathId ?? 0, t.path, t.source);
  }

  get isPlaying(): (t: MusicMetadataDto) => boolean {
    return (t) => this.music.mainPlayer.state.currentTrack?.path === t.path;
  }
}
