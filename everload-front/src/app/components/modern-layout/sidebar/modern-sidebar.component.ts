import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ModernStateService } from '../modern-state.service';
import { NasPath } from '../../../services/nas.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  exact?: boolean;
}

@Component({
  selector: 'app-modern-sidebar',
  templateUrl: './modern-sidebar.component.html',
  styleUrls: ['./modern-sidebar.component.css']
})
export class ModernSidebarComponent implements OnInit, OnDestroy {
  navItems: NavItem[] = [
    { label: 'Home',      icon: '🏠', route: '/modern',           exact: true },
    { label: 'Search',    icon: '🔍', route: '/modern/search' },
    { label: 'Library',   icon: '🎵', route: '/modern/library' },
    { label: 'Albums',    icon: '💿', route: '/modern/albums' },
    { label: 'Artists',   icon: '🎤', route: '/modern/artists' },
    { label: 'Playlists', icon: '📋', route: '/modern/playlists' },
    { label: 'Favorites', icon: '❤️', route: '/modern/favorites' },
    { label: 'Activity',  icon: '📊', route: '/modern/activity' },
    { label: 'Downloads', icon: '⬇️', route: '/modern/downloads' },
  ];

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

  onPathChange(id: string) {
    this.state.selectPath(+id);
  }

  goClassic() {
    this.router.navigate(['/']);
  }
}
