import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-instagram-downloads',
  templateUrl: './instagram-downloads.component.html',
  styleUrls: ['./instagram-downloads.component.css']
})
export class InstagramDownloadsComponent {
  videoUrl: string = '';
  error: string | null = null;
  loading: boolean = false;

  constructor(private http: HttpClient, private translate: TranslateService) {
    const savedLang = localStorage.getItem('language');
    if (savedLang) {
      translate.use(savedLang);
    }
  }

  changeLanguage(lang: string) {
    this.translate.use(lang);
    localStorage.setItem('language', lang);
  }

  download() {
    if (!this.videoUrl.includes('instagram.com')) {
      this.error = this.translate.instant('INVALID_INSTAGRAM_URL');
      return;
    }

    this.error = null;
    this.loading = true;

    const encodedUrl = encodeURIComponent(this.videoUrl);
    const endpoint = `http://localhost:8080/api/downloadInstagram?url=${encodedUrl}`;

    this.http.get(endpoint, {
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (response) => {
        const contentDisposition = response.headers.get('Content-Disposition');
        let fileName = 'instagram-video.mp4';
        if (contentDisposition) {
          const match = contentDisposition.match(/filename="(.+)"/);
          if (match) {
            fileName = match[1];
          }
        }

        const blob = new Blob([response.body!], { type: 'application/octet-stream' });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        a.click();
        window.URL.revokeObjectURL(url);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.error = this.translate.instant('DOWNLOAD_FAILED');
      }
    });
  }
}
