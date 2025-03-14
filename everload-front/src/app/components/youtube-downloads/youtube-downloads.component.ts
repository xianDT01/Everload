import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-youtube-downloads',
  templateUrl: './youtube-downloads.component.html',
  styleUrls: ['./youtube-downloads.component.css']
})
export class YoutubeDownloadsComponent {
  videoUrl: string = '';
  resolution: string = '720'; // Resolución predeterminada
  backendUrl: string = 'http://localhost:8080'; // URL do backend

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
      responseType: 'text'
    }).subscribe(response => {
      alert(response);
    }, error => {
      alert('Error al descargar el video.');
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
      responseType: 'text'
    }).subscribe(response => {
      alert(response);
    }, error => {
      alert('Error al descargar la música.');
    });
  }

  // Función para que o extraer o ID do video de un enlace de YouTube
  private extractVideoId(url: string): string | null {
    const match = url.match(/(?:https?:\/\/)?(?:www\.)?youtube\.com\/watch\?v=([a-zA-Z0-9_-]{11})|(?:https?:\/\/)?youtu\.be\/([a-zA-Z0-9_-]{11})/);
    return match ? (match[1] || match[2]) : null;
  }
}
