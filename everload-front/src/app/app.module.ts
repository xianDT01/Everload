import { NgModule, LOCALE_ID } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppRoutingModule } from './app-routing.module';
import { FormsModule } from '@angular/forms';
import { HttpClientModule, HttpClient } from '@angular/common/http';
import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';

// IMPORTS NECESARIOS PARA CAMBIAR EL FORMATO DE FECHA
import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';

// Registrar español como locale
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

// Función para cargar archivos de traducción
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
    InstagramDownloadsComponent,
    SafeUrlPipe,
    SpotifyDownloadsComponent,
    TiktokDownloadsComponent,
    AdminConfigComponent,
    AboutAppComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    FormsModule,
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
    { provide: LOCALE_ID, useValue: 'es-ES' }   // 👈 ESTA LÍNEA ES LA CLAVE
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
