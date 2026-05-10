import { Component, Input, Output, EventEmitter, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ModernStateService } from '../modern-state.service';
import { NasPath } from '../../../services/nas.service';
import { THEMES } from '../modern-layout.component';

interface NavItem { label: string; icon: string; route: string; exact?: boolean; }

@Component({
  selector: 'app-modern-sidebar',
  templateUrl: './modern-sidebar.component.html',
  styleUrls: ['./modern-sidebar.component.css']
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

  constructor(public state: ModernStateService, private router: Router) {}

  ngOnInit() {
    this.sub = this.state.pathId$.subscribe(id => {
      this.selectedPathId = id;
      this.paths = this.state.paths;
    });
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }

  onPathChange(id: string) { this.state.selectPath(+id); }
  selectTheme(id: string) { this.themeChange.emit(id); this.showThemes = false; }
  goClassic() { this.router.navigate(['/']); }
}
