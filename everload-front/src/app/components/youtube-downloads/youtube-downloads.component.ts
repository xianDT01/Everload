import { Component, NgZone } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';

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

  constructor(private http: HttpClient, private ngZone: NgZone) {}

  downloadVideo() {
    if (!this.videoUrl.trim()) {
      alert('Por favor, ingresa un enlace de YouTube.');
      return;
    }

    const videoId = this.extractVideoId(this.videoUrl);
    if (!videoId) {
      alert('Enlace de YouTube inválido.');
      return;
    }

    this.ngZone.run(() => this.isLoading = true);  // 🟡 Forzar detección de cambios

    this.http.get(`${this.backendUrl}/downloadVideo`, {
      params: { videoId, resolution: this.resolution },
      responseType: 'blob',
      observe: 'events', // 🟢 Permite monitorear el progreso
      reportProgress: true
    }).subscribe({
      next: (event: HttpEvent<any>) => {
        if (event.type === HttpEventType.Response) {
          this.ngZone.run(() => {
            this.isLoading = false;
            this.triggerDownload(event.body, `${videoId}.webm`);
          });
        }
      },
      error: (error) => {
        this.ngZone.run(() => this.isLoading = false);
        alert('Error al descargar el video.');
      }
    });
  }

  downloadMusic() {
    if (!this.videoUrl.trim()) {
      alert('Por favor, ingresa un enlace de YouTube.');
      return;
    }

    const videoId = this.extractVideoId(this.videoUrl);
    if (!videoId) {
      alert('Enlace de YouTube inválido.');
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
            this.triggerDownload(event.body, `${videoId}.mp3`);
          });
        }
      },
      error: (error) => {
        this.ngZone.run(() => this.isLoading = false);
        alert('Error al descargar la música.');
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
}
