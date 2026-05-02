import { Component, NgZone, OnDestroy, OnInit } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { ChatService, ChatGroupDto, ActiveUser } from '../../services/chat.service';
import { ApiBaseService } from '../../services/api-base.service';

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
  downloadJobId?: string;
}

interface DirectDownloadJob {
  jobId: string;
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'ERROR';
  progress: number;
  filename?: string;
  error?: string;
}

@Component({
  selector: 'app-youtube-downloads',
  templateUrl: './youtube-downloads.component.html',
  styleUrls: ['./youtube-downloads.component.css']
})
export class YoutubeDownloadsComponent implements OnInit, OnDestroy {
  videoUrl: string = '';
  resolution: string = '720';
  get backendUrl(): string {
    return `${this.apiBase.backendUrl || ''}/api`;
  }

  // Queue
  queue: QueueItem[] = [];
  showQueue = false;
  private processingQueue = false;
  private cancelActiveDownload: (() => void) | null = null;

  // NAS
  showNasBrowser = false;
  nasDownloadType: 'video' | 'music' = 'video';
  get hasNasAccess(): boolean { return this.authService.hasNasAccess(); }

  searchResults: any[] = [];
  searchQuery: string = '';

  // Share modal state
  showShareModal = false;
  shareGroups: ChatGroupDto[] = [];
  shareUsers: ActiveUser[] = [];
  shareSearch = '';
  shareTab: 'groups' | 'users' = 'groups';
  sharingPayload: { videoId: string; videoTitle: string; thumbnailUrl: string; channelTitle: string } | null = null;
  shareLoading = false;
  shareSent = false;

  get isLoading(): boolean {
    return this.queue.some(i => i.status === 'downloading');
  }

  constructor(
    private http: HttpClient,
    private ngZone: NgZone,
    private translate: TranslateService,
    private sanitizer: DomSanitizer,
    private route: ActivatedRoute,
    private authService: AuthService,
    private notificationService: NotificationService,
    private chatService: ChatService,
    private apiBase: ApiBaseService
  ) {
    translate.setDefaultLang('gl');
    const savedLang = localStorage.getItem('language');
    if (savedLang) {
      translate.use(savedLang);
    }
  }

  ngOnInit(): void {
    // Support deep-link from chat card: /youtube-downloads?v=VIDEO_ID
    this.route.queryParamMap.subscribe(params => {
      const v = params.get('v');
      if (v) {
        this.videoUrl = `https://www.youtube.com/watch?v=${v}`;
        this.onVideoUrlChange();
        this.updateEmbedUrl();
      }
    });
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

    try {
      while (true) {
        const next = this.queue.find(i => i.status === 'pending');
        if (!next) break;
        await this.processItem(next);
      }
    } finally {
      this.processingQueue = false;
    }
  }

  private processItem(item: QueueItem): Promise<void> {
    // NAS save: send to server, no browser download
    if (item.nasPathId) {
      return this.processItemToNas(item);
    }
    if (item.type === 'music') {
      return this.processMusicItemAsJob(item);
    }
    // Normal: download to browser
    return new Promise<void>((resolve) => {
      this.ngZone.run(() => {
        item.status = 'downloading';
        item.startedAt = new Date();
        item.progress = 0;
      });

      const endpoint = item.type === 'video' ? 'downloadVideo' : 'downloadMusic';
      const params: any = item.type === 'video'
        ? { videoId: item.videoId, resolution: item.resolution || '720' }
        : { videoId: item.videoId, format: 'mp3' };

      const sub = this.http.get(`${this.backendUrl}/${endpoint}`, {
        params,
        responseType: 'blob',
        observe: 'events',
        reportProgress: true
      }).subscribe({
        next: (event: HttpEvent<any>) => {
          if (event.type === HttpEventType.DownloadProgress) {
            this.ngZone.run(() => {
              if (event.total) {
                item.progress = Math.round((event.loaded / event.total) * 100);
              } else {
                item.progress = Math.min(item.progress + 5, 90);
              }
            });
          } else if (event.type === HttpEventType.Response) {
            this.cancelActiveDownload = null;
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
        error: () => {
          this.cancelActiveDownload = null;
          this.ngZone.run(() => {
            item.status = 'failed';
            item.error = 'Error al descargar';
            item.completedAt = new Date();
            this.notificationService.showToast('error', 'Error de descarga', `No se pudo descargar ${item.videoId}`);
          });
          resolve();
        }
      });

      this.cancelActiveDownload = () => {
        sub.unsubscribe();
        this.cancelActiveDownload = null;
        this.ngZone.run(() => {
          item.status = 'cancelled';
          item.completedAt = new Date();
        });
        resolve();
      };
    });
  }

  private processMusicItemAsJob(item: QueueItem): Promise<void> {
    return new Promise<void>((resolve) => {
      let pollTimer: any = null;
      let startSub: any = null;
      let statusSub: any = null;
      let fileSub: any = null;
      let settled = false;

      const cleanup = () => {
        if (pollTimer) clearInterval(pollTimer);
        startSub?.unsubscribe?.();
        statusSub?.unsubscribe?.();
        fileSub?.unsubscribe?.();
        this.cancelActiveDownload = null;
      };

      const fail = (message: string) => {
        if (settled) return;
        settled = true;
        cleanup();
        this.ngZone.run(() => {
          item.status = 'failed';
          item.error = message || 'Error al descargar';
          item.completedAt = new Date();
          this.notificationService.showToast('error', 'Error de descarga', item.error);
        });
        resolve();
      };

      const completeWithBlob = (blob: Blob, filename: string) => {
        if (settled) return;
        settled = true;
        cleanup();
        this.ngZone.run(() => {
          item.filename = filename;
          item.status = 'completed';
          item.progress = 100;
          item.completedAt = new Date();
          this.triggerDownload(blob, filename);
          this.notificationService.showToast('success', 'Descarga completada', `${filename} listo`);
        });
        resolve();
      };

      const downloadPreparedFile = (job: DirectDownloadJob) => {
        if (pollTimer) clearInterval(pollTimer);
        this.ngZone.run(() => {
          item.progress = Math.max(item.progress, 95);
          item.filename = job.filename;
        });
        fileSub = this.http.get(`${this.backendUrl}/downloadMusic/jobs/${job.jobId}/file`, {
          responseType: 'blob',
          observe: 'events',
          reportProgress: true
        }).subscribe({
          next: (event: HttpEvent<any>) => {
            if (event.type === HttpEventType.DownloadProgress) {
              this.ngZone.run(() => {
                const transferProgress = event.total ? Math.round((event.loaded / event.total) * 5) : 1;
                item.progress = Math.min(99, 95 + transferProgress);
              });
            } else if (event.type === HttpEventType.Response) {
              const contentDisposition = event.headers?.get('content-disposition');
              const match = contentDisposition?.match(/filename="(.+)"/);
              const filename = match ? match[1] : (job.filename || `${item.videoId}.mp3`);
              completeWithBlob(event.body, filename);
            }
          },
          error: () => fail('El archivo se preparó, pero no se pudo descargar al navegador')
        });
      };

      const pollJob = () => {
        if (!item.downloadJobId || settled) return;
        statusSub = this.http.get<DirectDownloadJob>(`${this.backendUrl}/downloadMusic/jobs/${item.downloadJobId}`).subscribe({
          next: job => {
            this.ngZone.run(() => {
              item.progress = Math.max(item.progress, Math.min(job.progress || 0, 94));
              item.filename = job.filename || item.filename;
            });
            if (job.status === 'DONE') {
              downloadPreparedFile(job);
            } else if (job.status === 'ERROR') {
              fail(job.error || 'yt-dlp no pudo preparar la canción');
            }
          },
          error: () => fail('No se pudo consultar el progreso de la descarga')
        });
      };

      this.ngZone.run(() => {
        item.status = 'downloading';
        item.startedAt = new Date();
        item.progress = 3;
        item.error = undefined;
      });

      startSub = this.http.post<DirectDownloadJob>(`${this.backendUrl}/downloadMusic/jobs`, null, {
        params: { videoId: item.videoId, format: 'mp3' }
      }).subscribe({
        next: job => {
          this.ngZone.run(() => {
            item.downloadJobId = job.jobId;
            item.progress = Math.max(item.progress, job.progress || 5);
          });
          pollJob();
          pollTimer = setInterval(pollJob, 2500);
        },
        error: err => fail(err?.error?.error || 'No se pudo iniciar la descarga')
      });

      this.cancelActiveDownload = () => {
        if (settled) return;
        settled = true;
        cleanup();
        this.ngZone.run(() => {
          item.status = 'cancelled';
          item.completedAt = new Date();
        });
        resolve();
      };
    });
  }

  private processItemToNas(item: QueueItem): Promise<void> {
    return new Promise<void>((resolve) => {
      this.ngZone.run(() => {
        item.status = 'downloading';
        item.startedAt = new Date();
        item.progress = 10;
      });

      // Simulate progress while yt-dlp runs on the server
      const progressInterval = setInterval(() => {
        this.ngZone.run(() => {
          if (item.progress < 85) item.progress += 5;
        });
      }, 3000);

      const params: any = {
        videoId: item.videoId,
        format: 'mp3',
        nasPathId: item.nasPathId,
        subPath: item.nasSubPath || ''
      };

      this.http.post<{ filename: string; path: string; error?: string }>(
        `${this.backendUrl}/saveMusicToNas`, null, { params }
      ).subscribe({
        next: (result) => {
          clearInterval(progressInterval);
          this.ngZone.run(() => {
            item.filename = result.filename;
            item.status = 'completed';
            item.progress = 100;
            item.completedAt = new Date();
            this.notificationService.showToast('success', '💾 Guardado en NAS', result.filename);
          });
          resolve();
        },
        error: (err) => {
          clearInterval(progressInterval);
          this.ngZone.run(() => {
            item.status = 'failed';
            item.error = err.error?.error || 'Error al guardar en NAS';
            item.completedAt = new Date();
            this.notificationService.showToast('error', 'Error NAS', item.error!);
          });
          resolve();
        }
      });
    });
  }

  retryItem(id: string): void {
    const item = this.queue.find(i => i.id === id);
    if (!item) return;
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

    if (item.status === 'pending') {
      this.ngZone.run(() => {
        item.status = 'cancelled';
        item.completedAt = new Date();
      });
    } else if (item.status === 'downloading') {
      // Cancela el HTTP request activo y resuelve la promesa del bucle
      this.cancelActiveDownload?.();
    }
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

  embedUrl: SafeResourceUrl | null = null;

  private updateEmbedUrl(): void {
    const videoId = this.extractVideoId(this.videoUrl);
    this.embedUrl = videoId
      ? this.sanitizer.bypassSecurityTrustResourceUrl(`https://www.youtube.com/embed/${videoId}`)
      : null;
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
    this.updateEmbedUrl();
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

  // ── SHARE MODAL ──────────────────────────────────────────────────────────

  openShareModal(videoId: string, videoTitle: string, thumbnailUrl: string, channelTitle: string) {
    this.sharingPayload = { videoId, videoTitle, thumbnailUrl, channelTitle };
    this.shareSearch = '';
    this.shareTab = 'groups';
    this.shareSent = false;
    this.shareLoading = true;
    this.showShareModal = true;

    this.chatService.getGroups().subscribe({
      next: groups => {
        this.shareGroups = groups.filter(g => g.type !== 'ANNOUNCEMENT');
        this.shareLoading = false;
      },
      error: () => { this.shareLoading = false; }
    });

    this.chatService.getActiveUsers().subscribe({
      next: users => { this.shareUsers = users; },
      error: () => {}
    });
  }

  openShareModalFromUrl() {
    const videoId = this.extractVideoId(this.videoUrl);
    if (!videoId) { alert('Introduce primero un enlace válido de YouTube'); return; }
    const thumbnailUrl = this.getThumbnailUrl(videoId);
    this.openShareModal(videoId, videoId, thumbnailUrl, '');
  }

  openShareModalFromSearch(video: any) {
    const videoId = video.id.videoId;
    const title = video.snippet.title;
    const thumbnail = video.snippet.thumbnails.high.url;
    const channel = video.snippet.channelTitle;
    this.openShareModal(videoId, title, thumbnail, channel);
  }

  closeShareModal() {
    this.showShareModal = false;
    this.sharingPayload = null;
  }

  get filteredShareGroups(): ChatGroupDto[] {
    if (!this.shareSearch.trim()) return this.shareGroups;
    const q = this.shareSearch.toLowerCase();
    return this.shareGroups.filter(g => g.name.toLowerCase().includes(q));
  }

  get filteredShareUsers(): ActiveUser[] {
    if (!this.shareSearch.trim()) return this.shareUsers;
    const q = this.shareSearch.toLowerCase();
    return this.shareUsers.filter(u => u.username.toLowerCase().includes(q));
  }

  shareToGroup(group: ChatGroupDto) {
    if (!this.sharingPayload) return;
    this.chatService.sendYoutubeShare(group.id, this.sharingPayload).subscribe({
      next: () => {
        this.shareSent = true;
        this.notificationService.showToast('success', 'Compartido', `Vídeo enviado a "${group.name}"`);
        setTimeout(() => this.closeShareModal(), 1200);
      },
      error: () => this.notificationService.showToast('error', 'Error', 'No se pudo compartir el vídeo')
    });
  }

  shareToUser(user: ActiveUser) {
    if (!this.sharingPayload) return;
    const payload = this.sharingPayload;
    this.chatService.startPrivateChat(user.username).subscribe({
      next: group => {
        this.chatService.sendYoutubeShare(group.id, payload).subscribe({
          next: () => {
            this.shareSent = true;
            this.notificationService.showToast('success', 'Compartido', `Vídeo enviado a ${user.username}`);
            setTimeout(() => this.closeShareModal(), 1200);
          },
          error: () => this.notificationService.showToast('error', 'Error', 'No se pudo compartir el vídeo')
        });
      },
      error: () => this.notificationService.showToast('error', 'Error', 'No se pudo abrir el chat')
    });
  }

  // ─────────────────────────────────────────────────────────────────────────

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
