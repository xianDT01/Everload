import { Component, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { ApiBaseService } from '../../services/api-base.service';
import { NotificationService } from '../../services/notification.service';

interface SpotifyTrackItem {
  title: string;
  youtubeUrl: string | null;
  status: 'idle' | 'downloading' | 'completed' | 'failed';
  progress: number;
  jobId?: string;
  filename?: string;
  error?: string;
}

interface DirectDownloadJob {
  jobId: string;
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'ERROR';
  progress: number;
  filename?: string;
  error?: string;
}

@Component({
  selector: 'app-spotify-downloads',
  templateUrl: './spotify-downloads.component.html',
  styleUrls: ['./spotify-downloads.component.css']
})
export class SpotifyDownloadsComponent {
  playlistUrl: string = '';
  cargando: boolean = false;
  error: string | null = null;
  tracks: SpotifyTrackItem[] = [];
  buscado: boolean = false;
  showNasBrowser = false;

  get hasNasAccess(): boolean { return this.authService.hasNasAccess(); }
  get resultado(): SpotifyTrackItem[] { return this.tracks; }

  private get backendUrl(): string {
    return `${this.apiBase.backendUrl || ''}/api`;
  }

  constructor(
    private http: HttpClient,
    private ngZone: NgZone,
    private translate: TranslateService,
    private authService: AuthService,
    private apiBase: ApiBaseService,
    private notificationService: NotificationService
  ) {}

  changeLanguage(lang: string) {
    this.translate.use(lang);
    localStorage.setItem('language', lang);
  }

  descargarListaCanciones() {
    if (!this.playlistUrl.trim()) {
      this.error = this.translate.instant('EMPTY_URL_ERROR_Spotify');
      return;
    }

    this.cargando = true;
    this.error = null;
    this.tracks = [];
    this.buscado = false;

    this.http.post<any[]>('/api/spotify/playlist', { url: this.playlistUrl })
      .subscribe({
        next: (res) => {
          this.tracks = (res || []).map(item => ({
            title: item.title,
            youtubeUrl: item.youtubeUrl || null,
            status: 'idle' as const,
            progress: 0,
          }));
          this.buscado = true;
          this.cargando = false;
        },
        error: (err) => {
          const serverMsg = err?.error?.error;
          this.error = serverMsg || this.translate.instant('DOWNLOAD_Spotify_FAILED');
          this.buscado = true;
          this.cargando = false;
        }
      });
  }

  descargarTodas() {
    this.tracks
      .filter(t => t.youtubeUrl && t.status === 'idle')
      .forEach(t => this.descargarCancion(t));
  }

  descargarCancion(track: SpotifyTrackItem) {
    if (!track.youtubeUrl) return;
    const videoId = this.extraerId(track.youtubeUrl);
    if (!videoId) return;

    track.status = 'downloading';
    track.progress = 3;
    track.error = undefined;

    let pollTimer: any = null;
    let pollErrors = 0;
    let pollCount = 0;
    const MAX_POLL_ERRORS = 3;
    const MAX_POLL_ATTEMPTS = 480;

    const fail = (message: string) => {
      if (pollTimer) clearInterval(pollTimer);
      this.ngZone.run(() => {
        track.status = 'failed';
        track.error = message;
        this.notificationService.showToast('error', 'Error de descarga', message);
      });
    };

    const downloadFile = (job: DirectDownloadJob) => {
      if (pollTimer) clearInterval(pollTimer);
      this.ngZone.run(() => { track.progress = Math.max(track.progress, 95); });

      this.http.get(`${this.backendUrl}/downloadMusic/jobs/${job.jobId}/file`, {
        responseType: 'blob',
        observe: 'response'
      }).subscribe({
        next: (response) => {
          const cd = response.headers?.get('content-disposition');
          const match = cd?.match(/filename\*?=(?:UTF-8'')?["']?([^"';\n]+)/i);
          const filename = match ? decodeURIComponent(match[1].trim()) : (job.filename || `${videoId}.mp3`);
          this.ngZone.run(() => {
            track.filename = filename;
            track.status = 'completed';
            track.progress = 100;
            this.triggerDownload(response.body!, filename);
            this.notificationService.showToast('success', 'Descarga completada', `${track.title} listo`);
          });
        },
        error: (err) => fail(`Archivo listo pero no se pudo descargar (${err?.status ?? 'red'})`)
      });
    };

    const pollJob = () => {
      if (!track.jobId) return;
      pollCount++;
      if (pollCount > MAX_POLL_ATTEMPTS) {
        fail('La descarga tardó demasiado. Inténtalo de nuevo.');
        return;
      }

      this.http.get<DirectDownloadJob>(`${this.backendUrl}/downloadMusic/jobs/${track.jobId}`).subscribe({
        next: job => {
          pollErrors = 0;
          this.ngZone.run(() => {
            track.progress = Math.max(track.progress, Math.min(job.progress || 0, 94));
            track.filename = job.filename || track.filename;
          });
          if (job.status === 'DONE') {
            downloadFile(job);
          } else if (job.status === 'ERROR') {
            fail(job.error || 'No se pudo preparar la canción');
          }
        },
        error: (err) => {
          pollErrors++;
          if (pollErrors >= MAX_POLL_ERRORS) {
            fail(err?.status === 404
              ? 'La descarga fue interrumpida (el servidor se reinició)'
              : 'No se pudo consultar el progreso');
          }
        }
      });
    };

    this.http.post<DirectDownloadJob>(`${this.backendUrl}/downloadMusic/jobs`, null, {
      params: { videoId, format: 'mp3' }
    }).subscribe({
      next: job => {
        this.ngZone.run(() => {
          track.jobId = job.jobId;
          track.progress = Math.max(track.progress, job.progress || 5);
        });
        pollJob();
        pollTimer = setInterval(pollJob, 2500);
      },
      error: err => fail(err?.error?.error || 'No se pudo iniciar la descarga')
    });
  }

  reintentarCancion(track: SpotifyTrackItem) {
    track.status = 'idle';
    track.progress = 0;
    track.error = undefined;
    track.jobId = undefined;
    this.descargarCancion(track);
  }

  private extraerId(url: string): string {
    try {
      return new URL(url).searchParams.get('v') || '';
    } catch {
      return '';
    }
  }

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
}
