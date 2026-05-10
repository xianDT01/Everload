import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicService, PlayerState, MusicMetadataDto } from '../../../services/music.service';

@Component({
  selector: 'app-modern-bottombar',
  templateUrl: './modern-bottombar.component.html',
  styleUrls: ['./modern-bottombar.component.css']
})
export class ModernBottombarComponent implements OnInit, OnDestroy {
  state: PlayerState | null = null;
  shuffle = false;
  repeat: 'none' | 'one' | 'all' = 'none';
  private subs: Subscription[] = [];

  constructor(public music: MusicService) {}

  ngOnInit() {
    this.subs.push(
      this.music.mainPlayer.state$.subscribe(s => this.state = s),
      this.music.shuffle$.subscribe(v => this.shuffle = v),
      this.music.repeat$.subscribe(v => this.repeat = v),
    );
  }

  ngOnDestroy() { this.subs.forEach(s => s.unsubscribe()); }

  get track(): MusicMetadataDto | null { return this.state?.currentTrack ?? null; }
  get playing(): boolean { return this.state?.playing ?? false; }
  get currentTime(): number { return this.state?.currentTime ?? 0; }
  get duration(): number { return this.state?.duration ?? 0; }
  get volume(): number { return this.state?.volume ?? 1; }

  get coverUrl(): string {
    const t = this.track;
    const pathId = this.state?.pathId ?? t?.nasPathId ?? 0;
    if (!t) return '';
    return this.music.getCoverUrlWithCache(pathId, t.path, t.source);
  }

  get progress(): number {
    return this.duration > 0 ? (this.currentTime / this.duration) * 100 : 0;
  }

  fmt(s: number): string {
    if (!s || !isFinite(s)) return '0:00';
    const m = Math.floor(s / 60);
    const sec = Math.floor(s % 60);
    return `${m}:${sec.toString().padStart(2, '0')}`;
  }

  onSeek(e: Event) {
    const v = +(e.target as HTMLInputElement).value;
    this.music.mainPlayer.seek((v / 100) * this.duration);
  }

  onVolume(e: Event) {
    this.music.mainPlayer.setVolume(+(e.target as HTMLInputElement).value / 100);
  }

  toggle() { this.music.mainPlayer.togglePlay(); }
  prev() { this.music.playPrevMain(); }
  next() { this.music.playNextMain(); }
  toggleShuffle() { this.music.toggleShuffle(); }
  toggleRepeat() { this.music.toggleRepeat(); }
}
