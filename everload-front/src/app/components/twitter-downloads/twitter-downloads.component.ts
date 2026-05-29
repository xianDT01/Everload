import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { NasService, NasPath } from '../../services/nas.service';
import { MusicService } from '../../services/music.service';

@Component({
  selector: 'app-twitter-downloads',
  templateUrl: './twitter-downloads.component.html',
  styleUrls: ['./twitter-downloads.component.css']
})
export class TwitterDownloadsComponent {
  tweetUrl = '';
  error: string | null = null;
  loading = false;

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

  download() {
    if (!this.tweetUrl.includes('/status/')) { this.error = this.translate.instant('INVALID_TWITTER_URL'); return; }
    this.error = null;
    this.loading = true;
    this.http.get(`/api/downloadTwitter?url=${encodeURIComponent(this.tweetUrl)}`, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (response) => {
        const cd = response.headers.get('Content-Disposition');
        let fileName = 'video.mp4';
        if (cd) { const m = cd.match(/filename="(.+)"/); if (m) fileName = m[1]; }
        const url = window.URL.createObjectURL(new Blob([response.body!], { type: 'application/octet-stream' }));
        const a = document.createElement('a'); a.href = url; a.download = fileName; a.click();
        window.URL.revokeObjectURL(url);
        this.loading = false;
      },
      error: () => { this.loading = false; this.error = this.translate.instant('DOWNLOAD_FAILED'); }
    });
  }

  openNasPicker() {
    this.nas.getPaths().subscribe({ next: paths => { this.nasPaths = paths.filter(p => p.writable); this.selectedNasPathId = this.nasPaths[0]?.id ?? null; this.showNasPicker = true; }, error: () => {} });
  }

  saveToNas() {
    if (!this.tweetUrl || !this.selectedNasPathId) return;
    this.showNasPicker = false;
    this.nasJob = { status: 'QUEUED', progress: 0 };
    this.music.ytDlpQueueUrl(this.tweetUrl, '', +this.selectedNasPathId, this.nasSubPath.trim()).subscribe({
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
