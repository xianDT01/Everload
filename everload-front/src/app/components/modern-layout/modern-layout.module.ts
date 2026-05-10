import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

import { ModernLayoutComponent } from './modern-layout.component';
import { ModernSidebarComponent } from './sidebar/modern-sidebar.component';
import { ModernBottombarComponent } from './bottombar/modern-bottombar.component';
import { ModernHomeComponent } from './pages/home/modern-home.component';
import { ModernLibraryComponent } from './pages/library/modern-library.component';
import { ModernAlbumsComponent } from './pages/albums/modern-albums.component';
import { ModernArtistsComponent } from './pages/artists/modern-artists.component';
import { ModernPlaylistsComponent } from './pages/playlists/modern-playlists.component';
import { ModernFavoritesComponent } from './pages/favorites/modern-favorites.component';
import { ModernActivityComponent } from './pages/activity/modern-activity.component';
import { ModernSearchComponent } from './pages/search/modern-search.component';
import { ModernDownloadsComponent } from './pages/downloads/modern-downloads.component';

const routes: Routes = [
  {
    path: '',
    component: ModernLayoutComponent,
    children: [
      { path: '',          component: ModernHomeComponent },
      { path: 'library',   component: ModernLibraryComponent },
      { path: 'albums',    component: ModernAlbumsComponent },
      { path: 'artists',   component: ModernArtistsComponent },
      { path: 'playlists', component: ModernPlaylistsComponent },
      { path: 'favorites', component: ModernFavoritesComponent },
      { path: 'activity',  component: ModernActivityComponent },
      { path: 'search',    component: ModernSearchComponent },
      { path: 'downloads', component: ModernDownloadsComponent },
    ]
  }
];

@NgModule({
  declarations: [
    ModernLayoutComponent,
    ModernSidebarComponent,
    ModernBottombarComponent,
    ModernHomeComponent,
    ModernLibraryComponent,
    ModernAlbumsComponent,
    ModernArtistsComponent,
    ModernPlaylistsComponent,
    ModernFavoritesComponent,
    ModernActivityComponent,
    ModernSearchComponent,
    ModernDownloadsComponent,
  ],
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    RouterModule.forChild(routes),
  ]
})
export class ModernLayoutModule {}
