import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';

import { NasMusicComponent } from './nas-music.component';
import { NasBrowserModule } from '../nas-browser/nas-browser.module';
import { LibraryModeComponent } from './library-mode/library-mode.component';
import { DeckModeComponent } from './deck-mode/deck-mode.component';
import { LibraryHomeComponent } from './library-home/library-home.component';
import { PlaylistArtworkComponent } from './playlist-artwork/playlist-artwork.component';

const routes: Routes = [
  { path: '', component: NasMusicComponent },
];

@NgModule({
  declarations: [
    NasMusicComponent,
    LibraryModeComponent,
    LibraryHomeComponent,
    DeckModeComponent,
  ],
  imports: [
    PlaylistArtworkComponent,
    CommonModule,
    FormsModule,
    HttpClientModule,
    TranslateModule,
    NasBrowserModule,
    RouterModule.forChild(routes),
  ],
})
export class NasMusicModule {}
