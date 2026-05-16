import { Component, OnInit } from '@angular/core';
import { MusicService } from '../../../../services/music.service';

const LS = {
  BACK_BEHAVIOR:      'mpl_back_behavior',
  CROSSFADE:          'mpl_crossfade_seconds',
  BAR_POSITION:       'mpl_bar_position',
  REDUCE_ANIMATIONS:  'mpl_reduce_animations',
  SORT_ORDER:         'mpl_sort_order',
  SIDEBAR_ORDER:      'mpl_sidebar_order',
  VOLUME:             'mpl_volume',
  ARTIST_VIEW:        'mpl_artist_view',
};

export type BackBehavior = 'rewind-then-prev' | 'always-prev';
export type BarPosition = 'bottom' | 'top';
export type SortOrder = 'title' | 'artist' | 'album';

export const DEFAULT_SIDEBAR_ORDER = [
  'home', 'search', 'library', 'albums', 'artists',
  'playlists', 'favorites', 'activity', 'downloads', 'settings',
];

@Component({
  selector: 'app-modern-settings',
  templateUrl: './modern-settings.component.html',
  styleUrls: ['./modern-settings.component.css']
})
export class ModernSettingsComponent implements OnInit {

  backBehavior: BackBehavior = 'rewind-then-prev';
  crossfade = 0;
  barPosition: BarPosition = 'bottom';
  reduceAnimations = false;
  sortOrder: SortOrder = 'title';
  sidebarOrder: string[] = [...DEFAULT_SIDEBAR_ORDER];
  volume = 1;
  artistView: 'tracks' | 'albums' = 'tracks';

  readonly SIDEBAR_LABELS: Record<string, string> = {
    home: 'Home', search: 'Search', library: 'Library',
    albums: 'Albums', artists: 'Artists', playlists: 'Playlists',
    favorites: 'Favorites', activity: 'Activity',
    downloads: 'Downloads', settings: 'Settings',
  };

  constructor(public music: MusicService) {}

  ngOnInit() { this.load(); }

  load() {
    this.backBehavior  = (localStorage.getItem(LS.BACK_BEHAVIOR) as BackBehavior) || 'rewind-then-prev';
    this.crossfade     = Number(localStorage.getItem(LS.CROSSFADE) ?? '0');
    this.barPosition   = (localStorage.getItem(LS.BAR_POSITION) as BarPosition) || 'bottom';
    this.reduceAnimations = localStorage.getItem(LS.REDUCE_ANIMATIONS) === 'true';
    this.sortOrder     = (localStorage.getItem(LS.SORT_ORDER) as SortOrder) || 'title';
    this.volume        = parseFloat(localStorage.getItem(LS.VOLUME) ?? '1');
    if (!isFinite(this.volume)) this.volume = 1;
    this.artistView    = (localStorage.getItem(LS.ARTIST_VIEW) as any) || 'tracks';
    const savedOrder   = localStorage.getItem(LS.SIDEBAR_ORDER);
    if (savedOrder) {
      try {
        const parsed: string[] = JSON.parse(savedOrder);
        // merge: keep saved order, append any new items
        const all = new Set([...parsed, ...DEFAULT_SIDEBAR_ORDER]);
        this.sidebarOrder = Array.from(all).filter(k => DEFAULT_SIDEBAR_ORDER.includes(k));
      } catch { this.sidebarOrder = [...DEFAULT_SIDEBAR_ORDER]; }
    }
    this.applyAll();
  }

  // ── Back behavior ──────────────────────────────────────────────────────────

  setBackBehavior(v: BackBehavior) {
    this.backBehavior = v;
    localStorage.setItem(LS.BACK_BEHAVIOR, v);
    this.applyBackBehavior();
  }

  private applyBackBehavior() {
    this.music.backBehavior = this.backBehavior;
  }

  // ── Crossfade ──────────────────────────────────────────────────────────────

  setCrossfade(v: number) {
    this.crossfade = v;
    localStorage.setItem(LS.CROSSFADE, String(v));
    this.music.crossfadeDuration = v;
  }

  // ── Bar position ───────────────────────────────────────────────────────────

  setBarPosition(v: BarPosition) {
    this.barPosition = v;
    localStorage.setItem(LS.BAR_POSITION, v);
    this.applyBarPosition();
  }

  private applyBarPosition() {
    const shell = document.querySelector('.ml-shell') as HTMLElement | null;
    if (!shell) return;
    shell.setAttribute('data-bar', this.barPosition);
  }

  // ── Reduce animations ─────────────────────────────────────────────────────

  setReduceAnimations(v: boolean) {
    this.reduceAnimations = v;
    localStorage.setItem(LS.REDUCE_ANIMATIONS, String(v));
    this.applyReduceAnimations();
  }

  private applyReduceAnimations() {
    document.body.classList.toggle('reduce-animations', this.reduceAnimations);
  }

  // ── Sort order ────────────────────────────────────────────────────────────

  setSortOrder(v: SortOrder) {
    this.sortOrder = v;
    localStorage.setItem(LS.SORT_ORDER, v);
  }

  // ── Volume ────────────────────────────────────────────────────────────────

  setVolume(v: number) {
    this.volume = v;
    localStorage.setItem(LS.VOLUME, String(v));
    this.music.mainPlayer.setVolume(v);
  }

  // ── Artist view order ─────────────────────────────────────────────────────

  setArtistView(v: 'tracks' | 'albums') {
    this.artistView = v;
    localStorage.setItem(LS.ARTIST_VIEW, v);
  }

  // ── Sidebar order ─────────────────────────────────────────────────────────

  moveItem(index: number, dir: -1 | 1) {
    const target = index + dir;
    if (target < 0 || target >= this.sidebarOrder.length) return;
    const tmp = this.sidebarOrder[index];
    this.sidebarOrder[index] = this.sidebarOrder[target];
    this.sidebarOrder[target] = tmp;
    this.saveSidebarOrder();
  }

  resetSidebarOrder() {
    this.sidebarOrder = [...DEFAULT_SIDEBAR_ORDER];
    this.saveSidebarOrder();
  }

  private saveSidebarOrder() {
    localStorage.setItem(LS.SIDEBAR_ORDER, JSON.stringify(this.sidebarOrder));
    this.applySidebarOrder();
  }

  private applySidebarOrder() {
    document.dispatchEvent(new CustomEvent('mpl-sidebar-order', { detail: this.sidebarOrder }));
  }

  // ── Apply all on init ─────────────────────────────────────────────────────

  private applyAll() {
    this.applyBackBehavior();
    this.music.crossfadeDuration = this.crossfade;
    this.applyBarPosition();
    this.applyReduceAnimations();
    this.applySidebarOrder();
  }
}
