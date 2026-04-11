import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { NasPath, NasService } from '../../../services/nas.service';
import { MusicMetadataDto, MusicService, PlayerState } from '../../../services/music.service';

@Component({
  selector: 'app-deck-mode',
  templateUrl: './deck-mode.component.html',
  styleUrls: ['./deck-mode.component.css']
})
export class DeckModeComponent implements OnInit, OnDestroy {

  paths: NasPath[] = [];
  selectedPathId: number | null = null;
  currentSubPath = '';
  items: MusicMetadataDto[] = [];

  stateA: PlayerState | null = null;
  stateB: PlayerState | null = null;

  crossValue = 0;          // -1 … 0 … +1
  volA = 1;
  volB = 1;

  private subs: Subscription[] = [];

  constructor(public musicService: MusicService, private nasService: NasService) {}

  ngOnInit(): void {
    this.musicService.crossfade(0);

    this.nasService.getPaths().subscribe(paths => {
      this.paths = paths;
      if (paths.length > 0) this.selectPath(paths[0].id);
    });

    this.subs.push(
      this.musicService.deckAPlayer.state$.subscribe(s => this.stateA = s),
      this.musicService.deckBPlayer.state$.subscribe(s => this.stateB = s)
    );
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    // Pause both decks when leaving
    this.musicService.deckAPlayer.pause();
    this.musicService.deckBPlayer.pause();
  }

  // ── Browser ───────────────────────────────────────────────────────────────

  selectPath(id: number) {
    this.selectedPathId = id;
    this.currentSubPath = '';
    this.loadDir();
  }

  loadDir() {
    if (this.selectedPathId === null) return;
    this.musicService.browse(this.selectedPathId, this.currentSubPath).subscribe(items => {
      this.items = items;
    });
  }

  navigate(item: MusicMetadataDto) {
    if (!item.directory) return;
    this.currentSubPath = item.path;
    this.loadDir();
  }

  goUp() {
    const parts = this.currentSubPath.split(/[/\\]/).filter(Boolean);
    parts.pop();
    this.currentSubPath = parts.join('/');
    this.loadDir();
  }

  get isRoot() { return !this.currentSubPath; }
  get folders() { return this.items.filter(i => i.directory); }
  get tracks()  { return this.items.filter(i => !i.directory); }

  // ── Deck controls ─────────────────────────────────────────────────────────

  load(deck: 'A' | 'B', track: MusicMetadataDto) {
    if (!this.selectedPathId) return;
    const player = deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer;
    player.load(track, this.selectedPathId);
  }

  togglePlay(deck: 'A' | 'B') {
    const player = deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer;
    player.togglePlay();
  }

  cue(deck: 'A' | 'B') {
    const player = deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer;
    player.cue();
  }

  onSeek(deck: 'A' | 'B', e: Event) {
    const t = +(e.target as HTMLInputElement).value;
    (deck === 'A' ? this.musicService.deckAPlayer : this.musicService.deckBPlayer).seek(t);
  }

  onVolA(e: Event) {
    this.volA = +(e.target as HTMLInputElement).value;
    this.musicService.deckAPlayer.setVolume(this.volA);
  }

  onVolB(e: Event) {
    this.volB = +(e.target as HTMLInputElement).value;
    this.musicService.deckBPlayer.setVolume(this.volB);
  }

  onCrossfade(e: Event) {
    this.crossValue = +(e.target as HTMLInputElement).value;
    this.musicService.crossfade(this.crossValue);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  coverUrl(state: PlayerState | null): string {
    if (!state?.currentTrack?.hasCover || !state.pathId) return '';
    return this.musicService.getCoverUrl(state.pathId, state.currentTrack.path);
  }

  progressPct(state: PlayerState | null): number {
    if (!state?.duration) return 0;
    return (state.currentTime / state.duration) * 100;
  }

  fmt(seconds: number | undefined): string {
    if (!seconds || seconds <= 0) return '0:00';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  }

  remaining(state: PlayerState | null): string {
    if (!state) return '0:00';
    return this.fmt((state.duration || 0) - (state.currentTime || 0));
  }

  crossPctA(): number { return Math.round(Math.cos(((this.crossValue + 1) / 2) * Math.PI / 2) * 100); }
  crossPctB(): number { return Math.round(Math.cos((1 - (this.crossValue + 1) / 2) * Math.PI / 2) * 100); }
}