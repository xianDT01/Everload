import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-tiktok-downloads',
  templateUrl: './tiktok-downloads.component.html',
  styleUrls: ['./tiktok-downloads.component.css']
})
export class TiktokDownloadsComponent {
  tiktokUrl: string = '';
  cargando: boolean = false;
  error: string | null = null;

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

  descargar() {
    if (!this.tiktokUrl.trim()) {
      this.error = this.translate.instant('EMPTY_URL_ERROR');
      return;
    }

    this.cargando = true;
    this.error = null;

  //  const apiUrl = 'http://localhost:8080/api/downloadTikTok';
   const apiUrl = '/api/downloadTikTok';
    const params = { url: this.tiktokUrl };

    this.http.get(apiUrl, {
      params,
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (res) => {
        const filename = this.obtenerNombreArchivo(res.headers.get('Content-Disposition'));
        const blob = new Blob([res.body!], { type: 'application/octet-stream' });
        const link = document.createElement('a');
        link.href = window.URL.createObjectURL(blob);
        link.download = filename || 'video_tiktok.mp4';
        link.click();

        this.tiktokUrl = '';
        this.cargando = false;
      },
      error: (err) => {
        console.error('❌ Error al descargar:', err);
        this.error = this.translate.instant('DOWNLOAD_FAILED');
        this.cargando = false;
      }
    });
  }

  private obtenerNombreArchivo(contentDisposition: string | null): string | null {
    if (!contentDisposition) return null;

    const match = contentDisposition.match(/filename="?([^"]+)"?/);
    return match ? match[1] : null;
  }
}
