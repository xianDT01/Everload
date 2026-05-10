import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicService, MusicMetadataDto } from '../../../services/music.service';
import { ModernStateService } from '../modern-state.service';

@Component({
  selector: 'app-modern-queue',
  templateUrl: './modern-queue.component.html',
  styleUrls: ['./modern-queue.component.css']
})
export class ModernQueueComponent implements OnInit, OnDestroy {
  tracks: MusicMetadataDto[] = [];
  currentIndex = -1;
  pathId = 0;
  private sub!: Subscription;

  constructor(public music: MusicService, public state: ModernStateService) {}

  ngOnInit() {
    this.sub = this.music.queue$.subscribe(q => {
      this.tracks = q.tracks;
      this.currentIndex = q.index;
      this.pathId = q.pathId;
    });
  }

  ngOnDestroy() { this.sub.unsubscribe(); }

  playAt(index: number) {
    const q = this.music.queueSnapshot;
    this.music.setQueue(q.pathId, q.tracks, index);
  }

  coverUrl(t: MusicMetadataDto): string {
    return this.music.getCoverUrlWithCache(this.pathId, t.path, t.source);
  }

  fmt(s: number): string {
    if (!s || !isFinite(s)) return '';
    return `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;
  }
}
