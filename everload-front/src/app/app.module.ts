import { NgModule, LOCALE_ID, APP_INITIALIZER, isDevMode } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppRoutingModule } from './app-routing.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpClientModule, HttpClient, HTTP_INTERCEPTORS } from '@angular/common/http';
import { TranslateLoader, TranslateModule, TranslateService } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { ServiceWorkerModule } from '@angular/service-worker';
import { firstValueFrom } from 'rxjs';

import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
registerLocaleData(localeEs);

// Core — always loaded
import { AppComponent } from './app.component';
import { HomeComponent } from './components/home/home.component';
import { LoginComponent } from './components/login/login.component';
import { RegisterComponent } from './components/register/register.component';
import { PendingApprovalComponent } from './components/pending-approval/pending-approval.component';
import { NotificationToastComponent } from './components/notification-toast/notification-toast.component';
import { NotificationCenterComponent } from './components/notification-center/notification-center.component';
import { GlobalPlayerComponent } from './components/global-player/global-player.component';
import { PwaUpdateBannerComponent } from './components/pwa-update-banner/pwa-update-banner.component';
import { OfflineBannerComponent } from './components/offline-banner/offline-banner.component';
import { NowPlayingPanelComponent } from './components/now-playing-panel/now-playing-panel.component';
// Interceptors
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { MaintenanceInterceptor } from './interceptors/maintenance.interceptor';

export function HttpLoaderFactory(http: HttpClient) {
  return new TranslateHttpLoader(http, './assets/i18n/', '.json');
}

// Pre-loads translations before Angular renders any component to avoid flash of untranslated content.
export function initTranslations(translate: TranslateService): () => Promise<void> {
  return (): Promise<void> => {
    translate.setDefaultLang('es');
    const lang = (typeof localStorage !== 'undefined' && localStorage.getItem('language')) || 'es';

    const translationLoad = firstValueFrom(translate.use(lang));
    const fallback = new Promise<void>(resolve => setTimeout(resolve, 5000));

    return Promise.race([translationLoad.then(() => {}), fallback]).catch(() => {});
  };
}

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    LoginComponent,
    RegisterComponent,
    PendingApprovalComponent,
    NotificationToastComponent,
    NotificationCenterComponent,
    GlobalPlayerComponent,
    PwaUpdateBannerComponent,
    OfflineBannerComponent,
    NowPlayingPanelComponent,
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    FormsModule,
    ReactiveFormsModule,
    HttpClientModule,
    TranslateModule.forRoot({
      defaultLanguage: 'es',
      loader: {
        provide: TranslateLoader,
        useFactory: HttpLoaderFactory,
        deps: [HttpClient]
      }
    }),
    ServiceWorkerModule.register('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000'
    }),
  ],
  providers: [
    { provide: LOCALE_ID, useValue: 'es-ES' },
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: MaintenanceInterceptor, multi: true },
    {
      provide: APP_INITIALIZER,
      useFactory: initTranslations,
      deps: [TranslateService],
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }