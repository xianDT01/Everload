import { Injectable } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { BehaviorSubject } from 'rxjs';
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
export class PwaUpdateService {
  private _updateAvailable = new BehaviorSubject<boolean>(false);
  readonly updateAvailable$ = this._updateAvailable.asObservable();
  private _updatePrompt = new BehaviorSubject<AppUpdatePrompt | null>(null);
  readonly updatePrompt$ = this._updatePrompt.asObservable();
  private androidCheckInFlight = false;
  private checkTimer?: number;

  constructor(
    private swUpdate: SwUpdate,
    private androidRelease: AndroidReleaseService,
    private apiBase: ApiBaseService
  ) {}

  init(): void {
    if (this.swUpdate.isEnabled) {
      // Detect when a new version is downloaded and ready to activate
      this.swUpdate.versionUpdates
        .pipe(filter((evt): evt is VersionReadyEvent => evt.type === 'VERSION_READY'))
        .subscribe(() => {
          this.showWebUpdate();
        });

      this.checkForUpdate();
    }

    this.checkAndroidUpdate();

    if (!this.checkTimer) {
      this.checkTimer = window.setInterval(() => {
        this.checkForUpdate();
        this.checkAndroidUpdate();
      }, 6 * 60 * 60 * 1000);
    }
  }

  checkForUpdate(): void {
    if (this.swUpdate.isEnabled) {
      this.swUpdate.checkForUpdate().catch(() => {});
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
