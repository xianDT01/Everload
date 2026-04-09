import {
  Component, OnInit, OnDestroy, ViewChild, ElementRef, HostListener
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';

export interface AudioInfoDto {
  filename: string;
  formatName: string;
  extension: string;
  durationSeconds: number;
  fileSizeBytes: number;
  bitrateKbps: number;
  sampleRate: number;
  channels: number;
}

@Component({
  selector: 'app-audio-tools',
  templateUrl: './audio-tools.component.html',
  styleUrls: ['./audio-tools.component.css']
})
export class AudioToolsComponent implements OnInit, OnDestroy {

  // ── Tab ──────────────────────────────────────────────────────────────────
  activeTab: 'convert' | 'trim' = 'convert';

  // ── Convert ──────────────────────────────────────────────────────────────
  convertFile: File | null = null;
  convertInfo: AudioInfoDto | null = null;
  loadingInfo = false;
  converting = false;
  convertError = '';
  convertDragOver = false;
  targetFormat = 'mp3';
  bitrate = '192k';

  readonly formats = [
    { id: 'mp3',  label: 'MP3',       lossy: true,  desc: '~3-4 MB/min' },
    { id: 'm4a',  label: 'M4A',       lossy: true,  desc: 'AAC · Apple' },
    { id: 'ogg',  label: 'OGG',       lossy: true,  desc: 'Open source' },
    { id: 'aac',  label: 'AAC',       lossy: true,  desc: 'Amplio soporte' },
    { id: 'wav',  label: 'WAV',       lossy: false, desc: 'Sin pérdidas' },
    { id: 'flac', label: 'FLAC',      lossy: false, desc: 'Sin pérdidas' },
  ];
  readonly bitrates = ['64k', '96k', '128k', '192k', '256k', '320k'];

  // ── Trim ─────────────────────────────────────────────────────────────────
  trimFile: File | null = null;
  audioBlobUrl: string | null = null;
  audioDuration = 0;
  trimStart = 0;
  trimEnd = 0;
  trimStartDisplay = '0:00';
  trimEndDisplay   = '0:00';
  currentTime = 0;
  isPlaying = false;
  trimming = false;
  trimError = '';
  trimDragOver = false;
  trimLoading = false;

  @ViewChild('audioPlayer') audioPlayerRef!: ElementRef<HTMLAudioElement>;

  // ── Shared ────────────────────────────────────────────────────────────────
  readonly ALLOWED_EXTS = ['mp3', 'm4a', 'wav', 'ogg', 'aac', 'flac', 'opus', 'wma', 'mp4'];
  readonly MAX_SIZE_MB = 500;

  readonly BASE: string = (() => {
    const h = typeof window !== 'undefined' ? window.location.hostname : '';
    return (h === 'localhost' || h === '127.0.0.1') ? 'http://localhost:8080' : '';
  })();

  constructor(
    private http: HttpClient,
    private translate: TranslateService,
    private authService: AuthService,
    private router: Router
  ) {
    translate.setDefaultLang('gl');
    const lang = localStorage.getItem('language');
    if (lang) translate.use(lang);
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.revokeAudioUrl();
  }

  // ── Tab ───────────────────────────────────────────────────────────────────

  setTab(tab: 'convert' | 'trim'): void {
    this.activeTab = tab;
  }

  goHome(): void {
    this.router.navigate(['/']);
  }

  // ══════════════════════════════════════════════════════════════════════════
  // CONVERT TAB
  // ══════════════════════════════════════════════════════════════════════════

  onConvertDragOver(e: DragEvent): void {
    e.preventDefault();
    this.convertDragOver = true;
  }
  onConvertDragLeave(): void { this.convertDragOver = false; }

  onConvertDrop(e: DragEvent): void {
    e.preventDefault();
    this.convertDragOver = false;
    const file = e.dataTransfer?.files?.[0];
    if (file) this.setConvertFile(file);
  }

  onConvertFileSelected(e: Event): void {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (file) this.setConvertFile(file);
    (e.target as HTMLInputElement).value = '';
  }

  private setConvertFile(file: File): void {
    this.convertError = '';
    if (!this.validateFile(file)) return;
    this.convertFile = file;
    this.convertInfo = null;
    this.loadConvertInfo(file);
  }

  private loadConvertInfo(file: File): void {
    this.loadingInfo = true;
    const fd = new FormData();
    fd.append('file', file);
    this.http.post<AudioInfoDto>(`${this.BASE}/api/audio/info`, fd).subscribe({
      next: info => {
        this.convertInfo = info;
        this.loadingInfo = false;
      },
      error: () => {
        this.loadingInfo = false;
        // Info is optional — continue without it
      }
    });
  }

  get bitrateEnabled(): boolean {
    return !['wav', 'flac'].includes(this.targetFormat);
  }

  convertAudio(): void {
    if (!this.convertFile || this.converting) return;
    this.converting = true;
    this.convertError = '';

    const fd = new FormData();
    fd.append('file', this.convertFile);
    fd.append('format', this.targetFormat);
    if (this.bitrateEnabled) fd.append('bitrate', this.bitrate);

    this.http.post(`${this.BASE}/api/audio/convert`, fd, {
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (response) => {
        this.converting = false;
        const blob = response.body!;
        const cd = response.headers.get('Content-Disposition') || '';
        const filename = this.extractFilename(cd) ||
                         (this.getBaseName(this.convertFile!.name) + '_converted.' + this.targetFormat);
        this.triggerDownload(blob, filename);
      },
      error: () => {
        this.converting = false;
        this.convertError = this.translate.instant('AUDIO.CONVERT_ERROR');
      }
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // TRIM TAB
  // ══════════════════════════════════════════════════════════════════════════

  onTrimDragOver(e: DragEvent): void {
    e.preventDefault();
    this.trimDragOver = true;
  }
  onTrimDragLeave(): void { this.trimDragOver = false; }

  onTrimDrop(e: DragEvent): void {
    e.preventDefault();
    this.trimDragOver = false;
    const file = e.dataTransfer?.files?.[0];
    if (file) this.setTrimFile(file);
  }

  onTrimFileSelected(e: Event): void {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (file) this.setTrimFile(file);
    (e.target as HTMLInputElement).value = '';
  }

  private setTrimFile(file: File): void {
    this.trimError = '';
    if (!this.validateFile(file)) return;
    this.trimFile = file;
    this.isPlaying = false;
    this.trimStart = 0;
    this.trimEnd = 0;
    this.audioDuration = 0;
    this.currentTime = 0;
    this.revokeAudioUrl();
    this.audioBlobUrl = URL.createObjectURL(file);
    this.trimLoading = true;
  }

  onAudioLoaded(): void {
    const audio = this.audioPlayerRef?.nativeElement;
    if (!audio) return;
    this.trimLoading = false;
    this.audioDuration = audio.duration || 0;
    this.trimEnd = this.audioDuration;
    this.updateTrimDisplays();
  }

  onTimeUpdate(): void {
    const audio = this.audioPlayerRef?.nativeElement;
    if (!audio) return;
    this.currentTime = audio.currentTime;
    // Stop playback if it goes past trim end
    if (audio.currentTime >= this.trimEnd) {
      audio.pause();
      this.isPlaying = false;
    }
  }

  togglePlay(): void {
    const audio = this.audioPlayerRef?.nativeElement;
    if (!audio) return;
    if (this.isPlaying) {
      audio.pause();
      this.isPlaying = false;
    } else {
      if (audio.currentTime >= this.trimEnd || audio.currentTime < this.trimStart) {
        audio.currentTime = this.trimStart;
      }
      audio.play().then(() => this.isPlaying = true).catch(() => {});
    }
  }

  previewFromStart(): void {
    const audio = this.audioPlayerRef?.nativeElement;
    if (!audio) return;
    audio.currentTime = this.trimStart;
    audio.play().then(() => this.isPlaying = true).catch(() => {});
  }

  seekFromProgress(e: MouseEvent): void {
    const audio = this.audioPlayerRef?.nativeElement;
    if (!audio || !this.audioDuration) return;
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const pct = (e.clientX - rect.left) / rect.width;
    audio.currentTime = pct * this.audioDuration;
  }

  onTrimStartRange(e: Event): void {
    const val = +(e.target as HTMLInputElement).value;
    this.trimStart = Math.min(val, this.trimEnd - 0.1);
    this.updateTrimDisplays();
  }

  onTrimEndRange(e: Event): void {
    const val = +(e.target as HTMLInputElement).value;
    this.trimEnd = Math.max(val, this.trimStart + 0.1);
    this.updateTrimDisplays();
  }

  onTrimStartInput(e: Event): void {
    const secs = this.parseTimeInput((e.target as HTMLInputElement).value);
    if (secs !== null && secs >= 0 && secs < this.trimEnd) {
      this.trimStart = secs;
      this.updateTrimDisplays();
    }
  }

  onTrimEndInput(e: Event): void {
    const secs = this.parseTimeInput((e.target as HTMLInputElement).value);
    if (secs !== null && secs > this.trimStart && secs <= this.audioDuration) {
      this.trimEnd = secs;
      this.updateTrimDisplays();
    }
  }

  private updateTrimDisplays(): void {
    this.trimStartDisplay = this.formatTime(this.trimStart);
    this.trimEndDisplay   = this.formatTime(this.trimEnd);
  }

  get trimStartPct(): number {
    return this.audioDuration ? (this.trimStart / this.audioDuration) * 100 : 0;
  }
  get trimEndPct(): number {
    return this.audioDuration ? (this.trimEnd / this.audioDuration) * 100 : 100;
  }
  get trimWidthPct(): number {
    return this.trimEndPct - this.trimStartPct;
  }
  get trimResultDuration(): number {
    return Math.max(0, this.trimEnd - this.trimStart);
  }
  get currentTimePct(): number {
    return this.audioDuration ? (this.currentTime / this.audioDuration) * 100 : 0;
  }

  trimAudio(): void {
    if (!this.trimFile || this.trimming) return;
    if (this.trimEnd <= this.trimStart) {
      this.trimError = this.translate.instant('AUDIO.TRIM_INVALID_RANGE');
      return;
    }
    this.trimming = true;
    this.trimError = '';

    const fd = new FormData();
    fd.append('file', this.trimFile);
    fd.append('start', String(this.trimStart));
    fd.append('end', String(this.trimEnd));

    this.http.post(`${this.BASE}/api/audio/trim`, fd, {
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (response) => {
        this.trimming = false;
        const blob = response.body!;
        const cd = response.headers.get('Content-Disposition') || '';
        const filename = this.extractFilename(cd) ||
                         (this.getBaseName(this.trimFile!.name) + '_trim.' + this.getExtension(this.trimFile!.name));
        this.triggerDownload(blob, filename);
      },
      error: () => {
        this.trimming = false;
        this.trimError = this.translate.instant('AUDIO.TRIM_ERROR');
      }
    });
  }

  // ── Shared helpers ────────────────────────────────────────────────────────

  private validateFile(file: File): boolean {
    const ext = this.getExtension(file.name);
    if (!this.ALLOWED_EXTS.includes(ext)) {
      if (this.activeTab === 'convert') this.convertError = this.translate.instant('AUDIO.ERROR_INVALID_FORMAT');
      else this.trimError = this.translate.instant('AUDIO.ERROR_INVALID_FORMAT');
      return false;
    }
    if (file.size > this.MAX_SIZE_MB * 1024 * 1024) {
      if (this.activeTab === 'convert') this.convertError = this.translate.instant('AUDIO.ERROR_TOO_LARGE');
      else this.trimError = this.translate.instant('AUDIO.ERROR_TOO_LARGE');
      return false;
    }
    return true;
  }

  private revokeAudioUrl(): void {
    if (this.audioBlobUrl) {
      URL.revokeObjectURL(this.audioBlobUrl);
      this.audioBlobUrl = null;
    }
  }

  private triggerDownload(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  private extractFilename(cd: string): string {
    const match = cd.match(/filename="?([^"]+)"?/);
    return match ? decodeURIComponent(match[1]) : '';
  }

  formatTime(sec: number): string {
    if (!isFinite(sec) || sec < 0) return '0:00';
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  formatBytes(bytes: number): string {
    if (!bytes) return '';
    if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    return (bytes / 1024).toFixed(0) + ' KB';
  }

  private parseTimeInput(value: string): number | null {
    const [minStr, secStr] = value.split(':');
    const min = parseInt(minStr, 10);
    const sec = parseFloat(secStr ?? '0');
    if (isNaN(min) || isNaN(sec)) return null;
    return min * 60 + sec;
  }

  getExtension(name: string): string {
    const dot = name?.lastIndexOf('.');
    return dot >= 0 ? name.substring(dot + 1).toLowerCase() : '';
  }

  private getBaseName(name: string): string {
    const dot = name?.lastIndexOf('.');
    return dot >= 0 ? name.substring(0, dot) : name;
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(e: KeyboardEvent): void {
    if (e.code === 'Space' && this.activeTab === 'trim' && this.audioBlobUrl) {
      const tag = (e.target as HTMLElement).tagName;
      if (tag !== 'INPUT' && tag !== 'TEXTAREA') {
        e.preventDefault();
        this.togglePlay();
      }
    }
  }
}
