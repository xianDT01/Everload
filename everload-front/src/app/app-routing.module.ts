import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { YoutubeDownloadsComponent } from './components/youtube-downloads/youtube-downloads.component';
import { TwitterDownloadsComponent } from './components/twitter-downloads/twitter-downloads.component'; 
import { FacebookDownloadsComponent } from './components/facebook-downloads/facebook-downloads.component';
import { InstagramDownloadsComponent } from './components/instagram-downloads/instagram-downloads.component';
const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'youtube-downloads', component: YoutubeDownloadsComponent },
  { path: 'twitter-downloads', component: TwitterDownloadsComponent },
  {path: 'facebook-downloads', component: FacebookDownloadsComponent},
  {path : 'instagram-downloads', component: InstagramDownloadsComponent} 
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
