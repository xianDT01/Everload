import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-youtube-downloads',
  templateUrl: './youtube-downloads.component.html',
  styleUrls: ['./youtube-downloads.component.css']
})
export class YoutubeDownloadsComponent {
  videoUrl: string = '';
  resolution: string = '720';
  backendUrl: string = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

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
  
    this.http.get(`${this.backendUrl}/downloadVideo`, {
      params: { videoId, resolution: this.resolution },
      responseType: 'blob'
    }).subscribe({
      next: (blob) => {
        if (blob.size === 0) {
          console.error('Error: archivo vacío.');
          alert('Error: el archivo descargado está vacío.');
          return;
        }
        this.triggerDownload(blob, `${videoId}.webm`);
      },
      error: (error) => {
        console.error('Error en la descarga:', error);
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

    this.http.get(`${this.backendUrl}/downloadMusic`, {
      params: { videoId, format: 'mp3' },
      responseType: 'blob'  // Importante para recibir archivos
    }).subscribe(blob => {
      this.triggerDownload(blob, `${videoId}.mp3`);
    }, error => {
      alert('Error al descargar la música.');
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
