import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';

import { YoutubeDownloadsComponent } from '../youtube-downloads/youtube-downloads.component';
import { TwitterDownloadsComponent } from '../twitter-downloads/twitter-downloads.component';
import { FacebookDownloadsComponent } from '../facebook-downloads/facebook-downloads.component';
import { InstagramDownloadsComponent } from '../instagram-downloads/instagram-downloads.component';
import { SpotifyDownloadsComponent } from '../spotify-downloads/spotify-downloads.component';
import { TiktokDownloadsComponent } from '../tiktok-downloads/tiktok-downloads.component';
import { NasBrowserComponent } from '../nas-browser/nas-browser.component';
import { SafeUrlPipe } from '../youtube-downloads/safe-url.pipe';

const routes: Routes = [
  { path: 'youtube-downloads',   component: YoutubeDownloadsComponent },
  { path: 'twitter-downloads',   component: TwitterDownloadsComponent },
  { path: 'facebook-downloads',  component: FacebookDownloadsComponent },
  { path: 'instagram-downloads', component: InstagramDownloadsComponent },
  { path: 'spotify-downloads',   component: SpotifyDownloadsComponent },
  { path: 'tiktok-downloads',    component: TiktokDownloadsComponent },
];

@NgModule({
  declarations: [
    YoutubeDownloadsComponent,
    TwitterDownloadsComponent,
    FacebookDownloadsComponent,
    InstagramDownloadsComponent,
    SpotifyDownloadsComponent,
    TiktokDownloadsComponent,
    NasBrowserComponent,
    SafeUrlPipe,
  ],
  imports: [
    CommonModule,
    FormsModule,
    HttpClientModule,
    TranslateModule,
    RouterModule.forChild(routes),
  ],
})
export class DownloadsModule {}