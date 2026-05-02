import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { HomeComponent } from './components/home/home.component';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { PendingApprovalComponent } from './components/pending-approval/pending-approval.component';
import { AndroidAppComponent } from './components/android-app/android-app.component';

import { AuthGuard } from './guards/auth.guard';
import { AdminGuard } from './guards/admin.guard';

const routes: Routes = [
  // ── Public ────────────────────────────────────────────────────────────────
  { path: 'login',            component: LoginComponent },
  { path: 'register',         component: RegisterComponent },
  { path: 'pending-approval', component: PendingApprovalComponent },

  // ── Home (always-loaded, shown first after login) ─────────────────────────
  { path: '', component: HomeComponent, canActivate: [AuthGuard] },
  { path: 'android-app', component: AndroidAppComponent, canActivate: [AuthGuard] },

  // ── Downloads (lazy) ──────────────────────────────────────────────────────
  {
    path: 'radio',
    canActivate: [AuthGuard],
    loadChildren: () =>
      import('./components/radio/radio.module').then(m => m.RadioModule),
  },

  {
    path: '',
    canActivate: [AuthGuard],
    loadChildren: () =>
      import('./components/downloads/downloads.module').then(m => m.DownloadsModule),
  },

  // ── Chat (lazy) ───────────────────────────────────────────────────────────
  {
    path: 'chat',
    canActivate: [AuthGuard],
    loadChildren: () =>
      import('./components/chat/chat.module').then(m => m.ChatModule),
  },

  // ── NAS Music (lazy) ──────────────────────────────────────────────────────
  {
    path: 'nas-music',
    canActivate: [AuthGuard],
    loadChildren: () =>
      import('./components/nas-music/nas-music.module').then(m => m.NasMusicModule),
  },

  // ── Audio Tools (lazy) ────────────────────────────────────────────────────
  {
    path: 'audio-tools',
    canActivate: [AuthGuard],
    loadChildren: () =>
      import('./components/audio-tools/audio-tools.module').then(m => m.AudioToolsModule),
  },

  // ── User / About (lazy) ───────────────────────────────────────────────────
  {
    path: '',
    canActivate: [AuthGuard],
    loadChildren: () =>
      import('./components/user/user.module').then(m => m.UserModule),
  },

  // ── Admin (lazy, admin-only) ───────────────────────────────────────────────
  {
    path: 'admin-config',
    canActivate: [AdminGuard],
    loadChildren: () =>
      import('./components/admin-config/admin.module').then(m => m.AdminModule),
  },

  // ── Fallback ──────────────────────────────────────────────────────────────
  { path: '**', redirectTo: '' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
