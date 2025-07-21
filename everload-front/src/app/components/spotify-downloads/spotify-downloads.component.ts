import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-spotify-downloads',
  templateUrl: './spotify-downloads.component.html',
  styleUrls: ['./spotify-downloads.component.css']
})
export class SpotifyDownloadsComponent {
  playlistUrl: string = '';
  cargando: boolean = false;
  resultado: any = null;

  constructor(private http: HttpClient) { }

  descargarListaCanciones() {
    if (!this.playlistUrl) return;

    this.cargando = true;
    this.resultado = null;

    const body = { url: this.playlistUrl };

    this.http.post<any[]>('http://localhost:8080/api/spotify/playlist', body)
      .subscribe({
        next: (res) => {
          this.resultado = res;
          this.cargando = false;
        },
        error: (err) => {
          console.error('❌ Error al obtener canciones:', err);
          this.resultado = [];
          this.cargando = false;
        }
      });
  }
  descargarTodas() {
  for (let i = 0; i < this.resultado.length; i++) {
    const url = this.resultado[i].youtubeUrl;
    const videoId = this.extraerId(url);
    const delay = i * 1500; // pequeña pausa entre descargas

    setTimeout(() => {
      const enlace = document.createElement('a');
      enlace.href = `http://localhost:8080/api/downloadMusic?videoId=${videoId}&format=mp3`;
      enlace.target = '_blank';
      enlace.click();
    }, delay);
  }
}


  descargarCancion(url: string) {
    const videoId = this.extraerId(url);
    window.open(`http://localhost:8080/api/downloadMusic?videoId=${videoId}&format=mp3`, '_blank');
  }

  extraerId(url: string): string {
    const v = new URL(url).searchParams.get('v');
    return v || '';
  }

}
