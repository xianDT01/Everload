import { Component, HostListener, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { MusicService } from '../../services/music.service';
import { ModernStateService } from './modern-state.service';

export const THEMES = [
  { id: 'default',           label: 'Default',          dark: true },
  { id: 'catppuccin',        label: 'Catppuccin Mocha',  dark: true },
  { id: 'dracula',           label: 'Dracula',           dark: true },
  { id: 'nord',              label: 'Nord',              dark: true },
  { id: 'gruvbox',           label: 'Gruvbox',           dark: true },
  { id: 'gruvbox-classic',   label: 'Gruvbox Classic',   dark: true },
  { id: 'gruvbox-dark-soft', label: 'Gruvbox Soft',      dark: true },
  { id: 'rosepine',          label: 'Rosé Pine',         dark: true },
  { id: 'onedarkpro',        label: 'One Dark Pro',      dark: true },
  { id: 'ayu-dark',          label: 'Ayu Dark',          dark: true },
  { id: 'ayu-mirage',        label: 'Ayu Mirage',        dark: true },
  { id: 'kanagawa-dragon',   label: 'Kanagawa Dragon',   dark: true },
  { id: 'everforest',        label: 'Everforest Dark',   dark: true },
  { id: 'ef-night',          label: 'Ef Night',          dark: true },
  { id: 'midnight',          label: 'Midnight',          dark: true },
  { id: 'osmium',            label: 'Osmium',            dark: true },
  { id: 'vague',             label: 'Vague',             dark: true },
  { id: 'kettek16',          label: 'kettek16',          dark: true },
  { id: 'default-light',     label: 'Default Light',     dark: false },
  { id: 'catppuccin-latte',  label: 'Catppuccin Latte',  dark: false },
  { id: 'rosepine-dawn',     label: 'Rosé Pine Dawn',    dark: false },
  { id: 'everforest-light',  label: 'Everforest Light',  dark: false },
  { id: 'gruvbox-light',     label: 'Gruvbox Light',     dark: false },
  { id: 'ayu-light',         label: 'Ayu Light',         dark: false },
  { id: 'onelight',          label: 'One Light',         dark: false },
];

const DEFAULT_MODERN_THEME = 'gruvbox-classic';

@Component({
  selector: 'app-modern-layout',
  templateUrl: './modern-layout.component.html',
  styleUrls: ['./modern-layout.component.css']
})
export class ModernLayoutComponent implements OnInit, OnDestroy {
  currentTheme = DEFAULT_MODERN_THEME;
  showQueue = false;
  showFullscreen = false;
  private subs: Subscription[] = [];

  constructor(private music: MusicService, public modState: ModernStateService) {}

  ngOnInit() {
    this.currentTheme = localStorage.getItem('modern_theme') || DEFAULT_MODERN_THEME;
    this.subs.push(
      this.modState.showQueue$.subscribe(v => this.showQueue = v),
      this.modState.showFullscreen$.subscribe(v => this.showFullscreen = v),
    );
  }

  ngOnDestroy() { this.subs.forEach(s => s.unsubscribe()); }

  setTheme(id: string) {
    this.currentTheme = id;
    localStorage.setItem('modern_theme', id);
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(e: KeyboardEvent) {
    const tag = (e.target as HTMLElement)?.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
    if (e.code === 'Space') { e.preventDefault(); this.music.mainPlayer.togglePlay(); }
    if (e.code === 'Escape') { this.modState.closeFullscreen(); }
    if (e.code === 'ArrowRight' && e.altKey) { e.preventDefault(); this.music.playNextMain(); }
    if (e.code === 'ArrowLeft' && e.altKey) { e.preventDefault(); this.music.playPrevMain(); }
  }
}
