import { NgModule, LOCALE_ID } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppRoutingModule } from './app-routing.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpClientModule, HttpClient, HTTP_INTERCEPTORS } from '@angular/common/http';
import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';

import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
registerLocaleData(localeEs);

import { AppComponent } from './app.component';
import { HomeComponent } from './components/home/home.component';
import { YoutubeDownloadsComponent } from './components/youtube-downloads/youtube-downloads.component';
import { TwitterDownloadsComponent } from './components/twitter-downloads/twitter-downloads.component';
import { FacebookDownloadsComponent } from './components/facebook-downloads/facebook-downloads.component';
import { InstagramDownloadsComponent } from './components/instagram-downloads/instagram-downloads.component';
import { SafeUrlPipe } from './components/youtube-downloads/safe-url.pipe';
import { SpotifyDownloadsComponent } from './components/spotify-downloads/spotify-downloads.component';
import { TiktokDownloadsComponent } from './components/tiktok-downloads/tiktok-downloads.component';
import { AdminConfigComponent } from './components/admin-config/admin-config.component';
import { AboutAppComponent } from './components/about-app/about-app.component';

// Nuevos componentes de auth
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { PendingApprovalComponent } from './components/pending-approval/pending-approval.component';
import { NasBrowserComponent } from './components/nas-browser/nas-browser.component';
import { UserProfileComponent } from './components/user-profile/user-profile.component';

// Notificaciones
import { NotificationToastComponent } from './components/notification-toast/notification-toast.component';
import { NotificationCenterComponent } from './components/notification-center/notification-center.component';

// Chat
import { ChatComponent } from './components/chat/chat.component';
import { CreateGroupModalComponent } from './components/chat/create-group-modal/create-group-modal.component';

// Interceptor JWT
import { AuthInterceptor } from './interceptors/auth.interceptor';

export function HttpLoaderFactory(http: HttpClient) {
  return new TranslateHttpLoader(http, './assets/i18n/', '.json');
}

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    YoutubeDownloadsComponent,
    TwitterDownloadsComponent,
    FacebookDownloadsComponent,
    InstagramDownloadsComponent,
    SafeUrlPipe,
    SpotifyDownloadsComponent,
    TiktokDownloadsComponent,
    AdminConfigComponent,
    AboutAppComponent,
    LoginComponent,
    RegisterComponent,
    PendingApprovalComponent,
    NasBrowserComponent,
    UserProfileComponent,
    NotificationToastComponent,
    NotificationCenterComponent,
    ChatComponent,
    CreateGroupModalComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    FormsModule,
    ReactiveFormsModule,
    HttpClientModule,
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: HttpLoaderFactory,
        deps: [HttpClient]
      }
    })
  ],
  providers: [
    { provide: LOCALE_ID, useValue: 'es-ES' },
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }