import { Component, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
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
  VOL_SCROLL_STEP:    'mpl_vol_scroll_step',
  ARTIST_PHOTO:       'mpl_artist_photo_source',
  LANGUAGE:           'language',
  EQ_BANDS:           'mpl_eq_bands',
  CHANNEL_MODE:       'mpl_channel_mode',
};

export type BackBehavior = 'rewind-then-prev' | 'always-prev';
export type BarPosition = 'bottom' | 'top';
export type SortOrder = 'title' | 'artist' | 'album';
export type ArtistPhotoSource = 'deezer' | 'album_cover';
export type ChannelMode = 'stereo' | 'mono' | 'left' | 'right' | 'swap';

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
  volumeScrollStep = 5;
  artistPhotoSource: ArtistPhotoSource = 'deezer';
  language = 'es';
  eqBands = [0, 0, 0, 0, 0];
  channelMode: ChannelMode = 'stereo';

  readonly eqLabels = ['60Hz', '250Hz', '1kHz', '4kHz', '16kHz'];
  readonly channelModes: ChannelMode[] = ['stereo', 'mono', 'left', 'right', 'swap'];
  readonly LANGUAGES = [
    { code: 'es', label: 'Español' },
    { code: 'en', label: 'English' },
    { code: 'gl', label: 'Galego' },
  ];

  readonly SIDEBAR_LABELS: Record<string, string> = {
    home: 'Home', search: 'Search', library: 'Library',
    albums: 'Albums', artists: 'Artists', playlists: 'Playlists',
    favorites: 'Favorites', activity: 'Activity',
    downloads: 'Downloads', settings: 'Settings',
  };

  constructor(public music: MusicService, private translate: TranslateService) {}

  ngOnInit() { this.load(); }

  load() {
    this.backBehavior      = (localStorage.getItem(LS.BACK_BEHAVIOR) as BackBehavior) || 'rewind-then-prev';
    this.crossfade         = Number(localStorage.getItem(LS.CROSSFADE) ?? '0');
    this.barPosition       = (localStorage.getItem(LS.BAR_POSITION) as BarPosition) || 'bottom';
    this.reduceAnimations  = localStorage.getItem(LS.REDUCE_ANIMATIONS) === 'true';
    this.sortOrder         = (localStorage.getItem(LS.SORT_ORDER) as SortOrder) || 'title';
    this.volume            = parseFloat(localStorage.getItem(LS.VOLUME) ?? '1');
    if (!isFinite(this.volume)) this.volume = 1;
    this.artistView        = (localStorage.getItem(LS.ARTIST_VIEW) as any) || 'tracks';
    this.volumeScrollStep  = parseFloat(localStorage.getItem(LS.VOL_SCROLL_STEP) ?? '5');
    if (!isFinite(this.volumeScrollStep) || this.volumeScrollStep < 1) this.volumeScrollStep = 5;
    this.artistPhotoSource = (localStorage.getItem(LS.ARTIST_PHOTO) as ArtistPhotoSource) || 'deezer';
    this.language          = localStorage.getItem(LS.LANGUAGE) || 'es';
    this.channelMode       = (localStorage.getItem(LS.CHANNEL_MODE) as ChannelMode) || 'stereo';
    const savedEq = localStorage.getItem(LS.EQ_BANDS);
    if (savedEq) {
      try {
        const bands = JSON.parse(savedEq);
        if (Array.isArray(bands) && bands.length === 5) this.eqBands = bands;
      } catch {}
    }
    const savedOrder = localStorage.getItem(LS.SIDEBAR_ORDER);
    if (savedOrder) {
      try {
        const parsed: string[] = JSON.parse(savedOrder);
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

  // ── Volume scroll step ────────────────────────────────────────────────────

  setVolumeScrollStep(v: number) {
    this.volumeScrollStep = v;
    localStorage.setItem(LS.VOL_SCROLL_STEP, String(v));
  }

  // ── Artist photo source ────────────────────────────────────────────────────

  setArtistPhotoSource(v: ArtistPhotoSource) {
    this.artistPhotoSource = v;
    localStorage.setItem(LS.ARTIST_PHOTO, v);
  }

  // ── Language ──────────────────────────────────────────────────────────────

  setLanguage(lang: string) {
    this.language = lang;
    localStorage.setItem(LS.LANGUAGE, lang);
    this.translate.use(lang);
  }

  // ── EQ ────────────────────────────────────────────────────────────────────

  setEqBand(index: number, e: Event) {
    const dB = +(e.target as HTMLInputElement).value;
    this.eqBands[index] = dB;
    localStorage.setItem(LS.EQ_BANDS, JSON.stringify(this.eqBands));
    this.music.mainPlayer.setEqBand(index, dB);
  }

  resetEq() {
    this.eqBands = [0, 0, 0, 0, 0];
    localStorage.setItem(LS.EQ_BANDS, JSON.stringify(this.eqBands));
    this.eqBands.forEach((_, i) => this.music.mainPlayer.setEqBand(i, 0));
  }

  // ── Channel mode ──────────────────────────────────────────────────────────

  setChannelMode(mode: ChannelMode) {
    this.channelMode = mode;
    localStorage.setItem(LS.CHANNEL_MODE, mode);
    this.music.mainPlayer.setChannelMode(mode);
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
    // Apply persisted EQ and channel mode to active player
    this.eqBands.forEach((dB, i) => this.music.mainPlayer.setEqBand(i, dB));
    this.music.mainPlayer.setChannelMode(this.channelMode);
  }
}
