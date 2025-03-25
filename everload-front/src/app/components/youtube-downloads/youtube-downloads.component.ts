import { Component, NgZone } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-youtube-downloads',
  templateUrl: './youtube-downloads.component.html',
  styleUrls: ['./youtube-downloads.component.css']
})
export class YoutubeDownloadsComponent {
  videoUrl: string = '';
  resolution: string = '720';
  isLoading: boolean = false;
  backendUrl: string = 'http://localhost:8080/api';
//  backendUrl: string = '/api';

  searchResults: any[] = [];
  searchQuery: string = '';

  constructor(private http: HttpClient, private ngZone: NgZone, private translate: TranslateService) {
    // Establecer idioma por defecto
    translate.setDefaultLang('gl');
    const savedLang = localStorage.getItem('language');
    if (savedLang) {
      translate.use(savedLang);
    }
  }

  changeLanguage(lang: string) {
    this.translate.use(lang);
    localStorage.setItem('language', lang);
  }

  downloadVideo() {
    if (!this.videoUrl.trim()) {
      alert(this.translate.instant('PLEASE_ENTER_YOUTUBE_LINK'));
      return;
    }

    const videoId = this.extractVideoId(this.videoUrl);
    if (!videoId) {
      alert(this.translate.instant('INVALID_YOUTUBE_LINK'));
      return;
    }

    this.ngZone.run(() => this.isLoading = true);

    this.http.get(`${this.backendUrl}/downloadVideo`, {
      params: { videoId, resolution: this.resolution },
      responseType: 'blob',
      observe: 'events',
      reportProgress: true
    }).subscribe({
      next: (event: HttpEvent<any>) => {
        if (event.type === HttpEventType.Response) {
          this.ngZone.run(() => {
            this.isLoading = false;
            const contentDisposition = event.headers?.get('content-disposition');
            const match = contentDisposition?.match(/filename="(.+)"/);
            const filename = match ? match[1] : `${videoId}.webm`; // fallback si no viene el header
            this.triggerDownload(event.body, filename);

          });
        }
      },
      error: () => {
        this.ngZone.run(() => this.isLoading = false);
        alert(this.translate.instant('ERROR_DOWNLOADING_VIDEO'));
      }
    });
  }

  downloadMusic() {
    if (!this.videoUrl.trim()) {
      alert(this.translate.instant('PLEASE_ENTER_YOUTUBE_LINK'));
      return;
    }

    const videoId = this.extractVideoId(this.videoUrl);
    if (!videoId) {
      alert(this.translate.instant('INVALID_YOUTUBE_LINK'));
      return;
    }

    this.ngZone.run(() => this.isLoading = true);

    this.http.get(`${this.backendUrl}/downloadMusic`, {
      params: { videoId, format: 'mp3' },
      responseType: 'blob',
      observe: 'events',
      reportProgress: true
    }).subscribe({
      next: (event: HttpEvent<any>) => {
        if (event.type === HttpEventType.Response) {
          this.ngZone.run(() => {
            this.isLoading = false;
        
            // Acceder al header con un cast
            const response = event as any;
            const contentDisposition = response.headers?.get('content-disposition');
            const match = contentDisposition?.match(/filename="(.+)"/);
            const filename = match ? match[1] : `${videoId}.webm`;
        
            this.triggerDownload(event.body, filename);
          });
        }
      },
      error: () => {
        this.ngZone.run(() => this.isLoading = false);
        alert(this.translate.instant('ERROR_DOWNLOADING_MUSIC'));
      }
    });
  }

  private extractVideoId(url: string): string | null {
    const match = url.match(/(?:https?:\/\/)?(?:www\.)?youtube\.com\/watch\?v=([a-zA-Z0-9_-]{11})|(?:https?:\/\/)?youtu\.be\/([a-zA-Z0-9_-]{11})/);
    return match ? (match[1] || match[2]) : null;
  }

  private triggerDownload(blob: Blob, filename: string) {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }

  noResults: boolean = false;

  searchVideos() {
    if (!this.searchQuery.trim()) {
      alert(this.translate.instant('PLEASE_ENTER_SEARCH_QUERY'));
      return;
    }
  
    this.http.get<any>(`${this.backendUrl}/youtube/search`, {
      params: { query: this.searchQuery }
    }).subscribe({
      next: response => {
        this.searchResults = response.items;
        this.noResults = this.searchResults.length === 0;
      },
      error: () => {
        alert(this.translate.instant('ERROR_SEARCHING_YOUTUBE'));
        this.noResults = false;
      }
    });
  }
}