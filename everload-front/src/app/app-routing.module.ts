import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { HomeComponent } from './components/home/home.component';
import { YoutubeDownloadsComponent } from './components/youtube-downloads/youtube-downloads.component';
import { TwitterDownloadsComponent } from './components/twitter-downloads/twitter-downloads.component';
import { FacebookDownloadsComponent } from './components/facebook-downloads/facebook-downloads.component';
import { InstagramDownloadsComponent } from './components/instagram-downloads/instagram-downloads.component';
import { SpotifyDownloadsComponent } from './components/spotify-downloads/spotify-downloads.component';
import { TiktokDownloadsComponent } from './components/tiktok-downloads/tiktok-downloads.component';
import { AdminConfigComponent } from './components/admin-config/admin-config.component';
import { AboutAppComponent } from './components/about-app/about-app.component';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { PendingApprovalComponent } from './components/pending-approval/pending-approval.component';
import { UserProfileComponent } from './components/user-profile/user-profile.component';
import { ChatComponent } from './components/chat/chat.component';

import { AuthGuard } from './guards/auth.guard';
import { AdminGuard } from './guards/admin.guard';

const routes: Routes = [
  // Rutas públicas
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'pending-approval', component: PendingApprovalComponent },

  // Rutas protegidas (usuarios activos)
  { path: '', component: HomeComponent, canActivate: [AuthGuard] },
  { path: 'youtube-downloads', component: YoutubeDownloadsComponent, canActivate: [AuthGuard] },
  { path: 'twitter-downloads', component: TwitterDownloadsComponent, canActivate: [AuthGuard] },
  { path: 'facebook-downloads', component: FacebookDownloadsComponent, canActivate: [AuthGuard] },
  { path: 'instagram-downloads', component: InstagramDownloadsComponent, canActivate: [AuthGuard] },
  { path: 'spotify-downloads', component: SpotifyDownloadsComponent, canActivate: [AuthGuard] },
  { path: 'tiktok-downloads', component: TiktokDownloadsComponent, canActivate: [AuthGuard] },
  { path: 'about-app', component: AboutAppComponent, canActivate: [AuthGuard] },
  { path: 'profile', component: UserProfileComponent, canActivate: [AuthGuard] },
  { path: 'chat', component: ChatComponent, canActivate: [AuthGuard] },

  // Solo ADMIN
  { path: 'admin-config', component: AdminConfigComponent, canActivate: [AdminGuard] },

  // Fallback
  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }