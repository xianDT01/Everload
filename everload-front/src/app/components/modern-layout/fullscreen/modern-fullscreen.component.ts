import { Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicService, PlayerState, MusicMetadataDto } from '../../../services/music.service';
import { ModernStateService } from '../modern-state.service';

interface LrcLine { time: number; text: string; }

interface HistoryEntry {
  trackPath: string;
  title: string;
  artist: string;
  album: string;
  nasPathId: number;
}

@Component({
  selector: 'app-modern-fullscreen',
  templateUrl: './modern-fullscreen.component.html',
  styleUrls: ['./modern-fullscreen.component.css']
})
export class ModernFullscreenComponent implements OnInit, OnDestroy {
  @ViewChild('lyricsScroll') lyricsScrollRef?: ElementRef<HTMLElement>;

  tab: 'queue' | 'lyrics' | 'history' = 'queue';
  state: PlayerState | null = null;
  shuffle = false;
  repeat: 'none' | 'one' | 'all' = 'none';

  // Queue
  queueTracks: MusicMetadataDto[] = [];
  queueIndex = -1;
  queuePathId = 0;

  // Lyrics
  lyricsLoading = false;
  lrcLines: LrcLine[] = [];
  plainLyrics = '';
  lyricsSource = '';
  activeLyricIndex = -1;
  private lastLyricsTrackPath = '';

  // History
  history: HistoryEntry[] = [];
  historyLoading = false;

  private subs: Subscription[] = [];
  private prevVolume = 1;

  constructor(public music: MusicService, public state$: ModernStateService) {}

  ngOnInit() {
    this.subs.push(
      this.music.mainPlayer.state$.subscribe(s => {
        this.state = s;
        this.syncActiveLyric();
        if (this.tab === 'lyrics' && s?.currentTrack?.path !== this.lastLyricsTrackPath) {
          this.loadLyrics();
        }
      }),
      this.music.shuffle$.subscribe(v => this.shuffle = v),
      this.music.repeat$.subscribe(v => this.repeat = v),
      this.music.queue$.subscribe(q => {
        this.queueTracks = q.tracks;
        this.queueIndex = q.index;
        this.queuePathId = q.pathId;
      }),
    );
    this.loadHistory();
  }

  ngOnDestroy() { this.subs.forEach(s => s.unsubscribe()); }

  get track(): MusicMetadataDto | null { return this.state?.currentTrack ?? null; }
  get playing(): boolean { return this.state?.playing ?? false; }
  get currentTime(): number { return this.state?.currentTime ?? 0; }
  get duration(): number { return this.state?.duration ?? 0; }
  get volume(): number { return this.state?.volume ?? 1; }
  get progress(): number { return this.duration > 0 ? (this.currentTime / this.duration) * 100 : 0; }

  get coverUrl(): string {
    const t = this.track;
    if (!t) return '';
    const pathId = this.state?.pathId ?? t.nasPathId ?? 0;
    return this.music.getCoverUrlWithCache(pathId, t.path, t.source);
  }

  fmt(s: number): string {
    if (!s || !isFinite(s)) return '0:00';
    return `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;
  }

  toggle() { this.music.mainPlayer.togglePlay(); }
  prev() { this.music.playPrevMain(); }
  next() { this.music.playNextMain(); }
  toggleShuffle() { this.music.toggleShuffle(); }
  toggleRepeat() { this.music.toggleRepeat(); }

  onSeek(e: Event) {
    const v = +(e.target as HTMLInputElement).value;
    this.music.mainPlayer.seek((v / 100) * this.duration);
  }

  onVolume(e: Event) {
    const v = +(e.target as HTMLInputElement).value / 100;
    this.prevVolume = v > 0 ? v : this.prevVolume;
    this.music.mainPlayer.setVolume(v);
  }

  onMuteToggle() {
    if (this.volume > 0) { this.prevVolume = this.volume; this.music.mainPlayer.setVolume(0); }
    else { this.music.mainPlayer.setVolume(this.prevVolume || 1); }
  }

  // ── Queue ────────────────────────────────────────────────────────────────

  playAt(index: number) {
    const q = this.music.queueSnapshot;
    this.music.setQueue(q.pathId, q.tracks, index);
  }

  coverUrlFor(t: MusicMetadataDto): string {
    return this.music.getCoverUrlWithCache(this.queuePathId, t.path, t.source);
  }

  // ── Lyrics ───────────────────────────────────────────────────────────────

  selectTab(t: 'queue' | 'lyrics' | 'history') {
    this.tab = t;
    if (t === 'lyrics' && this.track?.path !== this.lastLyricsTrackPath) this.loadLyrics();
    if (t === 'history' && !this.history.length) this.loadHistory();
  }

  private loadLyrics() {
    const t = this.track;
    if (!t) return;
    this.lastLyricsTrackPath = t.path;
    this.lyricsLoading = true;
    this.lrcLines = [];
    this.plainLyrics = '';
    const pathId = this.state?.pathId ?? t.nasPathId ?? 0;
    this.music.getLyrics(pathId, t.path, t.title || t.name, t.artist, t.duration)
      .subscribe({
        next: res => {
          this.lyricsSource = res.source;
          if (res.lrc) this.lrcLines = this.parseLrc(res.lrc);
          else if (res.plain) this.plainLyrics = res.plain;
          this.lyricsLoading = false;
          this.syncActiveLyric();
        },
        error: () => { this.lyricsLoading = false; }
      });
  }

  private parseLrc(lrc: string): LrcLine[] {
    const lines: LrcLine[] = [];
    for (const raw of lrc.split('\n')) {
      const m = raw.match(/^\[(\d+):(\d+(?:\.\d+)?)\](.*)/);
      if (!m) continue;
      const time = +m[1] * 60 + +m[2];
      const text = m[3].trim();
      if (text) lines.push({ time, text });
    }
    return lines.sort((a, b) => a.time - b.time);
  }

  private syncActiveLyric() {
    if (!this.lrcLines.length) return;
    const t = this.currentTime;
    let idx = -1;
    for (let i = 0; i < this.lrcLines.length; i++) {
      if (this.lrcLines[i].time <= t) idx = i; else break;
    }
    if (idx !== this.activeLyricIndex) {
      this.activeLyricIndex = idx;
      this.scrollToActiveLyric();
    }
  }

  private scrollToActiveLyric() {
    if (!this.lyricsScrollRef || this.activeLyricIndex < 0) return;
    const container = this.lyricsScrollRef.nativeElement;
    const el = container.querySelector<HTMLElement>('.mfs-lyric-active');
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  seekToLyric(line: LrcLine) {
    this.music.mainPlayer.seek(line.time);
  }

  lyricClass(index: number): string {
    if (index === this.activeLyricIndex) return 'mfs-lyric-active';
    if (this.activeLyricIndex < 0) return '';
    const distance = Math.abs(index - this.activeLyricIndex);
    if (distance === 1) return 'mfs-lyric-near';
    if (distance === 2) return 'mfs-lyric-mid';
    return '';
  }

  // ── History ──────────────────────────────────────────────────────────────

  private loadHistory() {
    this.historyLoading = true;
    this.music.getHistory(30).subscribe({
      next: (items: any[]) => {
        this.history = items.map(h => ({
          trackPath: h.trackPath,
          title: h.title,
          artist: h.artist,
          album: h.album,
          nasPathId: h.nasPathId,
        }));
        this.historyLoading = false;
      },
      error: () => { this.historyLoading = false; }
    });
  }
}
