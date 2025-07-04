import { Component, NgZone } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

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
// backendUrl: string = '/api';

  searchResults: any[] = [];
  searchQuery: string = '';

  constructor(
    private http: HttpClient,
    private ngZone: NgZone,
    private translate: TranslateService,
    private sanitizer: DomSanitizer
  ) {
    // idioma por defecto
    translate.setDefaultLang('gl');
    const savedLang = localStorage.getItem('language');
    if (savedLang) {
      translate.use(savedLang);
    }
  }
 // Cambia el idioma de la aplicación
    changeLanguage(lang: string) {
    this.translate.use(lang);
    localStorage.setItem('language', lang);
  }
 // Función para descargar el video de YouTube
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
            const filename = match ? match[1] : `${videoId}.webm`; 
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
  // Función para descargar el canciones de YouTube
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
 // Función para extraer el ID del video de la URL
  private extractVideoId(url: string): string | null {
    const match = url.match(/(?:https?:\/\/)?(?:www\.)?youtube\.com\/watch\?v=([a-zA-Z0-9_-]{11})|(?:https?:\/\/)?youtu\.be\/([a-zA-Z0-9_-]{11})/);
    return match ? (match[1] || match[2]) : null;
  }
  // Función para iniciar la descarga del archivo
  private triggerDownload(blob: Blob, filename: string) {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }

 // Función para buscar videos en YouTube
  noResults: boolean = false;
  searchVideos() {
    if (!this.searchQuery.trim()) {
      this.searchResults = [];
      this.noResults = false;
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
  
  // Función para extraer el ID del video de la URL para la reproducción de YouTube
  getEmbedUrl(videoUrl: string): SafeResourceUrl | null {
    const videoId = this.extractVideoId(videoUrl);
    return videoId
      ? this.sanitizer.bypassSecurityTrustResourceUrl(`https://www.youtube.com/embed/${videoId}`)
      : null;
  }
  
  // Función para extraer el ID del video de la URL para la descarga
  playlistVideos: any[] = [];
  selectedVideos: Set<string> = new Set();
  isLoadingPlaylist: boolean = false;

  //  Función para manejar la selección de videos de la lista de reproducción
  loadPlaylistVideos() {
    const playlistRegex = /[?&]list=([a-zA-Z0-9_-]+)/;
    if (!playlistRegex.test(this.videoUrl)) return;

    this.http.get<any[]>(`${this.backendUrl}/playlistVideos`, {
      params: { playlistUrl: this.videoUrl }
    }).subscribe({
      next: response => {
        this.playlistVideos = response;
      },
      error: () => {
        alert(this.translate.instant('ERROR_LOADING_PLAYLIST'));
      }
    });
  }
  // Función para alternar la selección de un video
  toggleVideo(videoId: string) {
    if (this.selectedVideos.has(videoId)) {
      this.selectedVideos.delete(videoId);
    } else {
      this.selectedVideos.add(videoId);
    }
  }
  // Función para descargar todos los videos seleccionados de la lista de reproducción
  async downloadSelectedVideos() {
    if (this.selectedVideos.size === 0) return;

    this.ngZone.run(() => this.isLoading = true);

    const selectedIds = Array.from(this.selectedVideos);

    for (let i = 0; i < selectedIds.length; i++) {
      const id = selectedIds[i];

      await this.delay(500); // Reducido para mejor UX entre vídeos

      this.http.get(`${this.backendUrl}/downloadMusic`, {
        params: { videoId: id, format: 'mp3' },
        responseType: 'blob',
        observe: 'events',
        reportProgress: true
      }).subscribe({
        next: (event: HttpEvent<any>) => {
          if (event.type === HttpEventType.Response) {
            const contentDisposition = event.headers?.get('content-disposition');
            const match = contentDisposition?.match(/filename="(.+)"/);
            const filename = match ? match[1] : `${id}.mp3`;
            if (event.body) this.triggerDownload(event.body, filename);

            // Ocultar barra solo al final del último archivo
            if (i === selectedIds.length - 1) {
              this.ngZone.run(() => this.isLoading = false);
            }
          }
        },
        error: () => {
          alert(this.translate.instant('ERROR_DOWNLOADING_MUSIC'));
          this.ngZone.run(() => this.isLoading = false);
        }
      });
    }
  }

// Función para crear una promesa de retraso
  delay(ms: number) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  // Función para obtener la URL de la miniatura del video
  getThumbnailUrl(videoId: string): string {
    return `https://img.youtube.com/vi/${videoId}/hqdefault.jpg`;
  }

  // Función para manejar el cambio de URL del video
  onVideoUrlChange() {
    const playlistRegex = /[?&]list=([a-zA-Z0-9_-]+)/;
    if (playlistRegex.test(this.videoUrl)) {
      this.loadPlaylistVideos();
    } else {
      this.playlistVideos = [];
      this.selectedVideos.clear();
    }
  }
  allSelected = false;

  toggleSelectAll() {
    if (this.allSelected) {
      this.selectedVideos.clear();
    } else {
      this.playlistVideos.forEach(v => this.selectedVideos.add(v.id));
    }
    this.allSelected = !this.allSelected;
  }
  // Función para extraer el ID del video de la URL (versión alternativa)
  getVideoId(url: string): string | null {
    const match = url.match(/(?:v=|\/)([0-9A-Za-z_-]{11})(?:&|$)/);
    return match ? match[1] : null;
  }

}