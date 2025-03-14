import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { YoutubeDownloadsComponent } from './components/youtube-downloads/youtube-downloads.component';

const routes: Routes = [
  { path: '', component: HomeComponent }, // Página principal
  { path: 'youtube-downloads', component: YoutubeDownloadsComponent }, 
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
