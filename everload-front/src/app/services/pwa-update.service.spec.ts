import { SwUpdate } from '@angular/service-worker';
import { of, Subject, throwError } from 'rxjs';
import { AndroidReleaseService } from './android-release.service';
import { ApiBaseService } from './api-base.service';
import { PwaUpdateService } from './pwa-update.service';

describe('PwaUpdateService', () => {
  let updates: PwaUpdateService;
  let androidRelease: jasmine.SpyObj<AndroidReleaseService>;
  let apiBase: jasmine.SpyObj<ApiBaseService>;

  beforeEach(() => {
    androidRelease = jasmine.createSpyObj<AndroidReleaseService>('AndroidReleaseService', [
      'getRelease', 'absoluteDownloadUrl'
    ]);
    apiBase = jasmine.createSpyObj<ApiBaseService>('ApiBaseService', ['isNativePlatform'], {
      androidVersionCode: 10
    });
    apiBase.isNativePlatform.and.returnValue(false);
    updates = new PwaUpdateService(
      { isEnabled: false, versionUpdates: new Subject() } as unknown as SwUpdate,
      androidRelease,
      apiBase
    );
  });

  it('does not request Android releases in a browser', () => {
    updates.checkAndroidUpdate();
    expect(androidRelease.getRelease).not.toHaveBeenCalled();
  });

  it('prompts for a newer Android release using its absolute URL', () => {
    apiBase.isNativePlatform.and.returnValue(true);
    androidRelease.getRelease.and.returnValue(of({
      available: true,
      versionCode: '11',
      versionName: '1.1',
      releaseNotes: 'Fixes',
      downloadUrl: '/release.apk'
    } as any));
    androidRelease.absoluteDownloadUrl.and.returnValue('https://example.test/release.apk');

    updates.checkAndroidUpdate();

    expect(androidRelease.absoluteDownloadUrl).toHaveBeenCalledWith('/release.apk');
    updates.updatePrompt$.subscribe(prompt => {
      if (prompt) {
        expect(prompt.type).toBe('android');
        expect(prompt.versionCode).toBe(11);
        expect(prompt.downloadUrl).toBe('https://example.test/release.apk');
      }
    });
  });

  it('ignores same-version, unavailable, and malformed releases', () => {
    apiBase.isNativePlatform.and.returnValue(true);
    androidRelease.getRelease.and.returnValue(of({
      available: true, versionCode: '10', downloadUrl: '/same.apk'
    } as any));
    updates.checkAndroidUpdate();
    androidRelease.getRelease.and.returnValue(of({
      available: true, versionCode: 'not-a-number', downloadUrl: '/bad.apk'
    } as any));
    updates.checkAndroidUpdate();
    androidRelease.getRelease.and.returnValue(of({
      available: false, versionCode: '20', downloadUrl: '/unavailable.apk'
    } as any));
    updates.checkAndroidUpdate();

    updates.updatePrompt$.subscribe(prompt => expect(prompt).toBeNull());
  });

  it('clears the in-flight guard after a failed Android release request', () => {
    apiBase.isNativePlatform.and.returnValue(true);
    androidRelease.getRelease.and.returnValues(throwError(() => new Error('offline')), of({
      available: true, versionCode: '12', downloadUrl: '/new.apk'
    } as any));
    androidRelease.absoluteDownloadUrl.and.returnValue('/new.apk');

    updates.checkAndroidUpdate();
    updates.checkAndroidUpdate();

    expect(androidRelease.getRelease).toHaveBeenCalledTimes(2);
  });

  it('prevents duplicate Android checks while a request is pending', () => {
    apiBase.isNativePlatform.and.returnValue(true);
    androidRelease.getRelease.and.returnValue(new Subject<any>());

    updates.checkAndroidUpdate();
    updates.checkAndroidUpdate();

    expect(androidRelease.getRelease).toHaveBeenCalledTimes(1);
  });

  it('dismisses the current update prompt', () => {
    updates.dismiss();
    updates.updatePrompt$.subscribe(prompt => expect(prompt).toBeNull());
    updates.updateAvailable$.subscribe(available => expect(available).toBeFalse());
  });
});
