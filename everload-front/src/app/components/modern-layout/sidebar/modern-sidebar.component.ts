import { Component, ElementRef, Input, Output, EventEmitter, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import anime from 'animejs/lib/anime.es.js';
import { ModernStateService } from '../modern-state.service';
import { NasPath } from '../../../services/nas.service';
import { THEMES } from '../modern-layout.component';

interface NavItem { label: string; icon: string; route: string; exact?: boolean; }

@Component({
    selector: 'app-modern-sidebar',
    templateUrl: './modern-sidebar.component.html',
    styleUrls: ['./modern-sidebar.component.css'],
    standalone: false
})
export class ModernSidebarComponent implements OnInit, OnDestroy {
  @Input() currentTheme = 'default';
  @Output() themeChange = new EventEmitter<string>();

  navItems: NavItem[] = [
    { label: 'Home',      icon: 'home', route: '/modern',           exact: true },
    { label: 'Search',    icon: 'search', route: '/modern/search' },
    { label: 'Library',   icon: 'library', route: '/modern/library' },
    { label: 'Albums',    icon: 'album', route: '/modern/albums' },
    { label: 'Artists',   icon: 'artist', route: '/modern/artists' },
    { label: 'Playlists', icon: 'playlist', route: '/modern/playlists' },
    { label: 'Favorites', icon: 'heart', route: '/modern/favorites' },
    { label: 'Activity',  icon: 'activity', route: '/modern/activity' },
    { label: 'Downloads', icon: 'download', route: '/modern/downloads' },
  ];

  themes = THEMES;
  showThemes = false;
  paths: NasPath[] = [];
  selectedPathId: number | null = null;
  private sub!: Subscription;

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
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }

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
