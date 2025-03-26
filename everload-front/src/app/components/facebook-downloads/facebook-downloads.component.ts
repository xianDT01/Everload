import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-facebook-downloads',
  templateUrl: './facebook-downloads.component.html',
  styleUrls: ['./facebook-downloads.component.css']
})
export class FacebookDownloadsComponent {
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
    if (!this.videoUrl.includes('facebook.com') && !this.videoUrl.includes('fb.watch')) {
      this.error = this.translate.instant('INVALID_FACEBOOK_URL');
      return;
    }

    this.error = null;
    this.loading = true;

    console.log('🔗 Enviando URL:', this.videoUrl);
    const endpoint = `http://localhost:8080/api/downloadFacebook?url=${this.videoUrl}`;


    this.http.get(endpoint, {
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (response) => {
        const contentDisposition = response.headers.get('Content-Disposition');
        let fileName = 'facebook-video.mp4';
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
