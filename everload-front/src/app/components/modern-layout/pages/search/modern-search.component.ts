import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicService, MusicMetadataDto } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';

@Component({
  selector: 'app-modern-search',
  templateUrl: './modern-search.component.html',
  styleUrls: ['./modern-search.component.css']
})
export class ModernSearchComponent implements OnInit, OnDestroy {
  query = '';
  results: MusicMetadataDto[] = [];
  loading = false;
  pathId: number | null = null;
  private sub!: Subscription;
  private debounce: any;

  constructor(public music: MusicService, private state: ModernStateService) {}

  ngOnInit() {
    this.sub = this.state.pathId$.subscribe(pid => { this.pathId = pid; });
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
    clearTimeout(this.debounce);
  }

  onInput() {
    clearTimeout(this.debounce);
    if (!this.query.trim()) { this.results = []; return; }
    this.debounce = setTimeout(() => this.doSearch(), 350);
  }

  doSearch() {
    if (!this.query.trim() || this.pathId == null) return;
    this.loading = true;
    this.music.search(this.pathId, undefined, this.query, 100).subscribe({
      next: r => { this.results = r; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  play(i: number) {
    if (this.pathId == null) return;
    this.music.setQueue(this.pathId, this.results, i);
  }

  fmt(s: number): string {
    if (!s || !isFinite(s)) return '';
    return `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;
  }

  cover(t: MusicMetadataDto): string {
    return this.music.getCoverUrlWithCache(this.pathId ?? 0, t.path, t.source);
  }
}
