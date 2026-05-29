import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { NasService, NasPath } from '../../services/nas.service';
import { MusicService } from '../../services/music.service';

@Component({
  selector: 'app-tiktok-downloads',
  templateUrl: './tiktok-downloads.component.html',
  styleUrls: ['./tiktok-downloads.component.css']
})
export class TiktokDownloadsComponent {
  tiktokUrl = '';
  cargando = false;
  error: string | null = null;

  nasPaths: NasPath[] = [];
  selectedNasPathId: number | null = null;
  nasSubPath = '';
  showNasPicker = false;
  nasJob: any = null;
  private nasJobPollRef: any;

  get hasNasAccess(): boolean { return this.auth.hasNasAccess(); }

  constructor(
    private http: HttpClient,
    private translate: TranslateService,
    private auth: AuthService,
    private nas: NasService,
    private music: MusicService
  ) {
    const savedLang = localStorage.getItem('language');
    if (savedLang) translate.use(savedLang);
  }

  changeLanguage(lang: string) {
    this.translate.use(lang);
    localStorage.setItem('language', lang);
  }

  descargar() {
    if (!this.tiktokUrl.trim()) { this.error = this.translate.instant('EMPTY_URL_ERROR'); return; }
    this.cargando = true;
    this.error = null;
    this.http.get('/api/downloadTikTok', { params: { url: this.tiktokUrl }, responseType: 'blob', observe: 'response' }).subscribe({
      next: (res) => {
        const cd = res.headers.get('Content-Disposition');
        const m = cd?.match(/filename="?([^"]+)"?/);
        const filename = m ? m[1] : 'video_tiktok.mp4';
        const link = document.createElement('a');
        link.href = window.URL.createObjectURL(new Blob([res.body!], { type: 'application/octet-stream' }));
        link.download = filename; link.click();
        this.tiktokUrl = '';
        this.cargando = false;
      },
      error: () => { this.error = this.translate.instant('DOWNLOAD_FAILED'); this.cargando = false; }
    });
  }

  openNasPicker() {
    this.nas.getPaths().subscribe({ next: paths => { this.nasPaths = paths.filter(p => p.writable); this.selectedNasPathId = this.nasPaths[0]?.id ?? null; this.showNasPicker = true; }, error: () => {} });
  }

  saveToNas() {
    if (!this.tiktokUrl || !this.selectedNasPathId) return;
    this.showNasPicker = false;
    this.nasJob = { status: 'QUEUED', progress: 0 };
    this.music.ytDlpQueueUrl(this.tiktokUrl, '', +this.selectedNasPathId, this.nasSubPath.trim()).subscribe({
      next: (r) => this.startNasPoll(r.jobId),
      error: () => { this.nasJob = { status: 'ERROR', progress: 0, error: 'No se pudo encolar la descarga' }; }
    });
  }

  private startNasPoll(jobId: string) {
    clearInterval(this.nasJobPollRef);
    this.nasJobPollRef = setInterval(() => {
      this.music.ytDlpJobStatus(jobId).subscribe({ next: (j) => { this.nasJob = j; if (j.status === 'DONE' || j.status === 'ERROR') clearInterval(this.nasJobPollRef); }, error: () => {} });
    }, 3000);
  }
}
