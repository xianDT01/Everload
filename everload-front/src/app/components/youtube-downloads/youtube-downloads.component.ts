import { Component, NgZone, OnDestroy } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';

interface QueueItem {
  id: string;
  videoId: string;
  title: string;
  type: 'video' | 'music';
  resolution?: string;
  status: 'pending' | 'downloading' | 'completed' | 'failed' | 'cancelled';
  progress: number;
  startedAt?: Date;
  completedAt?: Date;
  filename?: string;
  error?: string;
  nasPathId?: number;
  nasSubPath?: string;
}

@Component({
  selector: 'app-youtube-downloads',
  templateUrl: './youtube-downloads.component.html',
  styleUrls: ['./youtube-downloads.component.css']
})
export class YoutubeDownloadsComponent implements OnDestroy {
  videoUrl: string = '';
  resolution: string = '720';
  backendUrl: string = (() => {
    const host = typeof window !== 'undefined' ? window.location.hostname : '';
    return (host === 'localhost' || host === '127.0.0.1') ? 'http://localhost:8080/api' : '/api';
  })();

  // Queue
  queue: QueueItem[] = [];
  showQueue = false;
  private processingQueue = false;
  private cancelledIds = new Set<string>();

  // NAS
  showNasBrowser = false;
  nasDownloadType: 'video' | 'music' = 'video';
  get hasNasAccess(): boolean { return this.authService.hasNasAccess(); }

  searchResults: any[] = [];
  searchQuery: string = '';

  get isLoading(): boolean {
    return this.queue.some(i => i.status === 'downloading');
  }

  constructor(
    private http: HttpClient,
    private ngZone: NgZone,
    private translate: TranslateService,
    private sanitizer: DomSanitizer,
    private authService: AuthService,
    private notificationService: NotificationService
  ) {
    translate.setDefaultLang('gl');
    const savedLang = localStorage.getItem('language');
    if (savedLang) {
      translate.use(savedLang);
    }
  }

  ngOnDestroy(): void {}

  changeLanguage(lang: string) {
    this.translate.use(lang);
    localStorage.setItem('language', lang);
  }

  private generateId(): string {
    return Math.random().toString(36).substring(2) + Date.now().toString(36);
  }

  addToQueue(type: 'video' | 'music') {
    if (!this.videoUrl.trim()) {
      alert(this.translate.instant('PLEASE_ENTER_YOUTUBE_LINK'));
      return;
    }
    const videoId = this.extractVideoId(this.videoUrl);
    if (!videoId) {
      alert(this.translate.instant('INVALID_YOUTUBE_LINK'));
      return;
    }

    const item: QueueItem = {
      id: this.generateId(),
      videoId,
      title: videoId,
      type,
      resolution: type === 'video' ? this.resolution : undefined,
      status: 'pending',
      progress: 0
    };

    this.ngZone.run(() => {
      this.queue.push(item);
      if (!this.showQueue) this.showQueue = true;
    });

    this.processQueue();
  }

  private async processQueue(): Promise<void> {
    if (this.processingQueue) return;
    this.processingQueue = true;

    while (true) {
      const next = this.queue.find(i => i.status === 'pending');
      if (!next) break;

      await this.processItem(next);
    }

    this.processingQueue = false;
  }

  private processItem(item: QueueItem): Promise<void> {
    return new Promise<void>((resolve) => {
      this.ngZone.run(() => {
        item.status = 'downloading';
        item.startedAt = new Date();
        item.progress = 0;
      });

      if (this.cancelledIds.has(item.id)) {
        this.ngZone.run(() => {
          item.status = 'cancelled';
          item.completedAt = new Date();
        });
        resolve();
        return;
      }

      const endpoint = item.type === 'video' ? 'downloadVideo' : 'downloadMusic';
      const params: any = item.type === 'video'
        ? { videoId: item.videoId, resolution: item.resolution || '720' }
        : { videoId: item.videoId, format: 'mp3' };

      this.http.get(`${this.backendUrl}/${endpoint}`, {
        params,
        responseType: 'blob',
        observe: 'events',
        reportProgress: true
      }).subscribe({
        next: (event: HttpEvent<any>) => {
          if (this.cancelledIds.has(item.id)) {
            this.ngZone.run(() => {
              item.status = 'cancelled';
              item.completedAt = new Date();
            });
            resolve();
            return;
          }
          if (event.type === HttpEventType.DownloadProgress) {
            this.ngZone.run(() => {
              if (event.total) {
                item.progress = Math.round((event.loaded / event.total) * 100);
              } else {
                item.progress = Math.min(item.progress + 5, 90);
              }
            });
          } else if (event.type === HttpEventType.Response) {
            this.ngZone.run(() => {
              const contentDisposition = event.headers?.get('content-disposition');
              const match = contentDisposition?.match(/filename="(.+)"/);
              const filename = match ? match[1] : `${item.videoId}.${item.type === 'video' ? 'webm' : 'mp3'}`;
              item.filename = filename;
              item.status = 'completed';
              item.progress = 100;
              item.completedAt = new Date();
              this.triggerDownload(event.body, filename);
              this.notificationService.showToast('success', 'Descarga completada', `${filename} listo`);
            });
            resolve();
          }
        },
        error: (err) => {
          this.ngZone.run(() => {
            item.status = 'failed';
            item.error = 'Error al descargar';
            item.completedAt = new Date();
            this.notificationService.showToast('error', 'Error de descarga', `No se pudo descargar ${item.videoId}`);
          });
          resolve();
        }
      });
    });
  }

  retryItem(id: string): void {
    const item = this.queue.find(i => i.id === id);
    if (!item) return;
    this.cancelledIds.delete(id);
    this.ngZone.run(() => {
      item.status = 'pending';
      item.progress = 0;
      item.error = undefined;
      item.startedAt = undefined;
      item.completedAt = undefined;
    });
    this.processQueue();
  }

  cancelItem(id: string): void {
    const item = this.queue.find(i => i.id === id);
    if (!item) return;
    this.cancelledIds.add(id);
    if (item.status === 'pending') {
      this.ngZone.run(() => {
        item.status = 'cancelled';
        item.completedAt = new Date();
      });
    }
    // If downloading, the subscription will detect cancellation on next event
  }

  clearCompleted(): void {
    this.ngZone.run(() => {
      this.queue = this.queue.filter(i => i.status === 'pending' || i.status === 'downloading');
    });
  }

  getQueueCount(status?: string): number {
    if (!status) {
      return this.queue.filter(i => i.status === 'pending' || i.status === 'downloading').length;
    }
    return this.queue.filter(i => i.status === status).length;
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'pending': return '⏳';
      case 'downloading': return '⬇️';
      case 'completed': return '✅';
      case 'failed': return '❌';
      case 'cancelled': return '🚫';
      default: return '❓';
    }
  }

  formatTime(date?: Date): string {
    if (!date) return '';
    return date.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  // Original download methods kept for compatibility (now delegate to queue)
  downloadVideo() {
    this.addToQueue('video');
  }

  downloadMusic() {
    this.addToQueue('music');
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
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }

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

  getEmbedUrl(videoUrl: string): SafeResourceUrl | null {
    const videoId = this.extractVideoId(videoUrl);
    return videoId
      ? this.sanitizer.bypassSecurityTrustResourceUrl(`https://www.youtube.com/embed/${videoId}`)
      : null;
  }

  playlistVideos: any[] = [];
  selectedVideos: Set<string> = new Set();
  isLoadingPlaylist: boolean = false;

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

  toggleVideo(videoId: string) {
    if (this.selectedVideos.has(videoId)) {
      this.selectedVideos.delete(videoId);
    } else {
      this.selectedVideos.add(videoId);
    }
  }

  async downloadSelectedVideos() {
    if (this.selectedVideos.size === 0) return;
    const selectedIds = Array.from(this.selectedVideos);
    for (const id of selectedIds) {
      this.queue.push({
        id: this.generateId(),
        videoId: id,
        title: id,
        type: 'music',
        status: 'pending',
        progress: 0
      });
    }
    if (!this.showQueue) this.showQueue = true;
    this.processQueue();
  }

  delay(ms: number) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  getThumbnailUrl(videoId: string): string {
    return `https://img.youtube.com/vi/${videoId}/hqdefault.jpg`;
  }

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

  getVideoId(url: string): string | null {
    const match = url.match(/(?:v=|\/)([0-9A-Za-z_-]{11})(?:&|$)/);
    return match ? match[1] : null;
  }

  openNasBrowser(type: 'video' | 'music') {
    if (!this.videoUrl.trim()) {
      alert('Introduce primero un enlace de YouTube');
      return;
    }
    this.nasDownloadType = type;
    this.showNasBrowser = true;
  }

  onNasPathSelected(dest: { pathId: number; subPath: string }) {
    this.showNasBrowser = false;
    const videoId = this.extractVideoId(this.videoUrl);
    if (!videoId) { alert('URL no válida'); return; }

    const item: QueueItem = {
      id: this.generateId(),
      videoId,
      title: videoId,
      type: this.nasDownloadType,
      resolution: this.nasDownloadType === 'video' ? this.resolution : undefined,
      status: 'pending',
      progress: 0,
      nasPathId: dest.pathId,
      nasSubPath: dest.subPath
    };
    this.queue.push(item);
    if (!this.showQueue) this.showQueue = true;
    this.processQueue();
  }
}
