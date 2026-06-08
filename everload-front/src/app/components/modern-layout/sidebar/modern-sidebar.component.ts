import { Component, ElementRef, Input, Output, EventEmitter, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import anime from 'animejs/lib/anime.es.js';
import { ModernStateService } from '../modern-state.service';
import { NasPath } from '../../../services/nas.service';
import { THEMES } from '../modern-layout.component';
import { DEFAULT_SIDEBAR_ORDER } from '../pages/settings/modern-settings.component';

interface NavItem { labelKey: string; icon: string; route: string; exact?: boolean; }

const ALL_NAV: NavItem[] = [
  { labelKey: 'MUSIC.MODERN_SIDEBAR_HOME',      icon: 'home',     route: '/modern',              exact: true },
  { labelKey: 'MUSIC.MODERN_SIDEBAR_SEARCH',    icon: 'search',   route: '/modern/search' },
  { labelKey: 'MUSIC.MODERN_SIDEBAR_LIBRARY',  icon: 'library',  route: '/modern/library' },
  { labelKey: 'MUSIC.MODERN_SIDEBAR_ALBUMS',     icon: 'album',    route: '/modern/albums' },
  { labelKey: 'MUSIC.MODERN_SIDEBAR_ARTISTS',    icon: 'artist',   route: '/modern/artists' },
  { labelKey: 'MUSIC.MODERN_SIDEBAR_PLAYLISTS',   icon: 'playlist', route: '/modern/playlists' },
  { labelKey: 'MUSIC.MODERN_SIDEBAR_FAVORITES',   icon: 'heart',    route: '/modern/favorites' },
  { labelKey: 'MUSIC.MODERN_SIDEBAR_ACTIVITY',   icon: 'activity', route: '/modern/activity' },
  { labelKey: 'MUSIC.MODERN_SIDEBAR_YTMUSIC',    icon: 'wave',     route: '/modern/ytmusic' },
  { labelKey: 'MUSIC.MODERN_SIDEBAR_DOWNLOADS',   icon: 'download', route: '/modern/downloads' },
  { labelKey: 'MUSIC.MODERN_SIDEBAR_SETTINGS',     icon: 'settings', route: '/modern/settings' },
];

const KEY_MAP: Record<string, string> = {
  home: '/modern', search: '/modern/search', library: '/modern/library',
  albums: '/modern/albums', artists: '/modern/artists', playlists: '/modern/playlists',
  favorites: '/modern/favorites', activity: '/modern/activity',
  ytmusic: '/modern/ytmusic', downloads: '/modern/downloads', settings: '/modern/settings',
};

@Component({
  selector: 'app-modern-sidebar',
  templateUrl: './modern-sidebar.component.html',
  styleUrls: ['./modern-sidebar.component.css']
})
export class ModernSidebarComponent implements OnInit, OnDestroy {
  @Input() currentTheme = 'default';
  @Output() themeChange = new EventEmitter<string>();

  navItems: NavItem[] = this.buildOrder(DEFAULT_SIDEBAR_ORDER);

  themes = THEMES;
  showThemes = false;
  paths: NasPath[] = [];
  selectedPathId: number | null = null;
  private sub!: Subscription;
  private orderListener = (e: Event) => {
    const order = (e as CustomEvent<string[]>).detail;
    this.navItems = this.buildOrder(order);
  };

  constructor(
    public state: ModernStateService,
    private router: Router,
    private host: ElementRef<HTMLElement>
  ) {}

  ngOnInit() {
    this.sub = this.state.pathId$.subscribe(id => {
      this.selectedPathId = id;
      this.paths = this.state.paths;
    });
    document.addEventListener('mpl-sidebar-order', this.orderListener);
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
    document.removeEventListener('mpl-sidebar-order', this.orderListener);
  }

  private buildOrder(keys: string[]): NavItem[] {
    return keys
      .map(k => ALL_NAV.find(n => n.route === KEY_MAP[k]))
      .filter((n): n is NavItem => !!n);
  }

  onPathChange(id: string) { this.state.selectPath(+id); }
  selectTheme(id: string) { this.themeChange.emit(id); this.showThemes = false; }
  goHome(event?: Event) { this.animateExit(event, '/'); }
  goLibrary(event?: Event) { this.animateExit(event, '/nas-music'); }

  private animateExit(event: Event | undefined, target: string): void {
    const button = event?.currentTarget as HTMLElement | undefined;
    const shell = this.host.nativeElement.closest('.ml-shell') as HTMLElement | null;
    const reduced = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (!button || reduced) {
      this.router.navigate([target]);
      return;
    }

    anime.remove([button, shell]);
    anime({
      targets: button,
      scale: [1, 0.95, 1.02],
      duration: 210,
      easing: 'easeOutQuad'
    });
    anime({
      targets: shell,
      opacity: [1, 0.88],
      scale: [1, 0.995],
      duration: 180,
      easing: 'easeOutQuad',
      complete: () => this.router.navigate([target])
    });
  }
}

