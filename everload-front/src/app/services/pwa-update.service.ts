import { Injectable, OnDestroy } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { AndroidRelease, AndroidReleaseService } from './android-release.service';
import { ApiBaseService } from './api-base.service';

export type AppUpdatePromptType = 'web' | 'android';

export interface AppUpdatePrompt {
  type: AppUpdatePromptType;
  titleKey: string;
  messageKey: string;
  actionKey: string;
  versionName?: string;
  versionCode?: number;
  releaseNotes?: string;
  downloadUrl?: string;
}

@Injectable({ providedIn: 'root' })
export class PwaUpdateService implements OnDestroy {
  private _updateAvailable = new BehaviorSubject<boolean>(false);
  readonly updateAvailable$ = this._updateAvailable.asObservable();
  private _updatePrompt = new BehaviorSubject<AppUpdatePrompt | null>(null);
  readonly updatePrompt$ = this._updatePrompt.asObservable();
  private androidCheckInFlight = false;
  private checkTimer?: number;
  private initialized = false;
  private webCheckInFlight = false;
  private versionSub?: Subscription;
  private readonly onResume = () => {
    if (document.hidden) return;
    this.checkForUpdate();
    this.checkAndroidUpdate();
  };

  constructor(
    private swUpdate: SwUpdate,
    private androidRelease: AndroidReleaseService,
    private apiBase: ApiBaseService
  ) {}

  init(): void {
    if (this.initialized) return;
    this.initialized = true;
    if (this.swUpdate.isEnabled) {
      // Detect when a new version is downloaded and ready to activate
      this.versionSub = this.swUpdate.versionUpdates
        .pipe(filter((evt): evt is VersionReadyEvent => evt.type === 'VERSION_READY'))
        .subscribe(() => {
          this.showWebUpdate();
        });

      this.checkForUpdate();
      navigator.serviceWorker?.ready.then(() => {
        if (this.initialized) this.checkForUpdate();
      }).catch(() => {});
    }

    this.checkAndroidUpdate();

    if (!this.checkTimer) {
      this.checkTimer = window.setInterval(() => {
        this.checkForUpdate();
        this.checkAndroidUpdate();
      }, 15 * 60 * 1000);
    }
    document.addEventListener('visibilitychange', this.onResume);
    window.addEventListener('online', this.onResume);
  }

  ngOnDestroy(): void {
    this.initialized = false;
    if (this.checkTimer) window.clearInterval(this.checkTimer);
    this.checkTimer = undefined;
    this.versionSub?.unsubscribe();
    document.removeEventListener('visibilitychange', this.onResume);
    window.removeEventListener('online', this.onResume);
  }

  checkForUpdate(): void {
    if (this.swUpdate.isEnabled && !this.webCheckInFlight) {
      this.webCheckInFlight = true;
      this.swUpdate.checkForUpdate().catch(() => {}).finally(() => { this.webCheckInFlight = false; });
    }
  }

  checkAndroidUpdate(): void {
    if (!this.apiBase.isNativePlatform() || this.androidCheckInFlight) return;

    this.androidCheckInFlight = true;
    this.androidRelease.getRelease().subscribe({
      next: release => {
        this.androidCheckInFlight = false;
        if (this.isNewerAndroidRelease(release)) {
          const versionCode = Number(release.versionCode);
          this.showPrompt({
            type: 'android',
            titleKey: 'UPDATE.ANDROID_TITLE',
            messageKey: 'UPDATE.ANDROID_MESSAGE',
            actionKey: 'UPDATE.ANDROID_ACTION',
            versionName: release.versionName,
            versionCode,
            releaseNotes: release.releaseNotes,
            downloadUrl: this.androidRelease.absoluteDownloadUrl(release.downloadUrl)
          });
        }
      },
      error: () => {
        this.androidCheckInFlight = false;
      }
    });
  }

  applyUpdate(): void {
    const prompt = this._updatePrompt.value;
    if (prompt?.type === 'android') {
      this.openAndroidDownload(prompt);
      return;
    }

    this.swUpdate.activateUpdate().then(() => {
      window.location.reload();
    }).catch(() => {
      window.location.reload();
    });
  }

  dismiss(): void {
    this._updateAvailable.next(false);
    this._updatePrompt.next(null);
  }

  private showWebUpdate(): void {
    this.showPrompt({
      type: 'web',
      titleKey: 'UPDATE.WEB_TITLE',
      messageKey: 'UPDATE.WEB_MESSAGE',
      actionKey: 'UPDATE.WEB_ACTION'
    });
  }

  private showPrompt(prompt: AppUpdatePrompt): void {
    this._updatePrompt.next(prompt);
    this._updateAvailable.next(true);
  }

  private isNewerAndroidRelease(release: AndroidRelease): boolean {
    const latestVersionCode = Number(release.versionCode);
    return !!release.available
      && Number.isFinite(latestVersionCode)
      && latestVersionCode > this.apiBase.androidVersionCode
      && !!release.downloadUrl;
  }

  private openAndroidDownload(prompt: AppUpdatePrompt): void {
    const url = prompt.downloadUrl || '';
    if (!url) return;

    const opened = window.open(url, this.apiBase.isNativePlatform() ? '_system' : '_blank');
    if (!opened) {
      window.location.href = url;
    }
  }
}
