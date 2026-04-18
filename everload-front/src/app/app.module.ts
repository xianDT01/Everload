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
// Interceptors
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { MaintenanceInterceptor } from './interceptors/maintenance.interceptor';

export function HttpLoaderFactory(http: HttpClient) {
  return new TranslateHttpLoader(http, './assets/i18n/', '.json');
}

/**
 * Pre-loads translations before Angular renders any component.
 * Eliminates the intermittent "missing text" bug caused by the race between
 * Angular's first render and the async HTTP load of the i18n JSON.
 */
export function initTranslations(translate: TranslateService): () => Promise<void> {
  return (): Promise<void> => {
    translate.setDefaultLang('es');
    const lang = (typeof localStorage !== 'undefined' && localStorage.getItem('language')) || 'es';
    return firstValueFrom(translate.use(lang))
      .then(() => undefined)
      .catch(async () => {
        // Translation load failed — probably corrupted SW cache. Reset SW and reload once.
        if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator
            && typeof sessionStorage !== 'undefined' && !sessionStorage.getItem('sw_reset')) {
          sessionStorage.setItem('sw_reset', '1');
          try {
            const regs = await navigator.serviceWorker.getRegistrations();
            await Promise.all(regs.map(r => r.unregister()));
          } catch {}
          window.location.reload();
          return;
        }
        // Already reset once — continue without translations rather than loop
      });
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