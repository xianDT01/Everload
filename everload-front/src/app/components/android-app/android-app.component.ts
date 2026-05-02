import { Component, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { AndroidRelease, AndroidReleaseService } from '../../services/android-release.service';

@Component({
  selector: 'app-android-app',
  templateUrl: './android-app.component.html',
  styleUrls: ['./android-app.component.css']
})
export class AndroidAppComponent implements OnInit {
  release: AndroidRelease | null = null;
  loading = true;
  downloading = false;
  error = '';

  constructor(
    private androidReleaseService: AndroidReleaseService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.loadRelease();
  }

  loadRelease(): void {
    this.loading = true;
    this.error = '';
    this.androidReleaseService.getRelease().subscribe({
      next: release => {
        this.release = release;
        this.loading = false;
      },
      error: () => {
        this.error = this.translate.instant('ANDROID.DOWNLOAD_LOAD_ERROR');
        this.loading = false;
      }
    });
  }

  download(): void {
    if (!this.release?.available || this.downloading) return;
    this.downloading = true;
    this.androidReleaseService.downloadApk().subscribe({
      next: blob => {
        const fileName = this.release?.fileName || 'everload.apk';
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        link.style.display = 'none';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
        this.downloading = false;
      },
      error: () => {
        this.error = this.translate.instant('ANDROID.DOWNLOAD_ERROR');
        this.downloading = false;
      }
    });
  }

  get uploadedDate(): Date | null {
    return this.release?.uploadedAt ? new Date(this.release.uploadedAt) : null;
  }
}
