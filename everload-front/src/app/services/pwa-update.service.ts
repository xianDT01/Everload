import { Injectable } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { BehaviorSubject } from 'rxjs';
import { filter } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class PwaUpdateService {
  private _updateAvailable = new BehaviorSubject<boolean>(false);
  readonly updateAvailable$ = this._updateAvailable.asObservable();

  constructor(private swUpdate: SwUpdate) {}

  init(): void {
    if (!this.swUpdate.isEnabled) return;

    // Detect when a new version is downloaded and ready to activate
    this.swUpdate.versionUpdates
      .pipe(filter((evt): evt is VersionReadyEvent => evt.type === 'VERSION_READY'))
      .subscribe(() => {
        this._updateAvailable.next(true);
      });

    // Check for updates immediately on startup, then every 6 hours
    this.checkForUpdate();
    setInterval(() => this.checkForUpdate(), 6 * 60 * 60 * 1000);
  }

  checkForUpdate(): void {
    if (this.swUpdate.isEnabled) {
      this.swUpdate.checkForUpdate().catch(() => {});
    }
  }

  applyUpdate(): void {
    this.swUpdate.activateUpdate().then(() => {
      window.location.reload();
    }).catch(() => {
      window.location.reload();
    });
  }

  dismiss(): void {
    this._updateAvailable.next(false);
  }
}
