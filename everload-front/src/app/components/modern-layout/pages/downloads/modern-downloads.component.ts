import { Component, OnInit, OnDestroy } from '@angular/core';
import { MusicService } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';
import { AuthService } from '../../../../services/auth.service';
import { NasPath } from '../../../../services/nas.service';

interface YtResult {
  videoId: string;
  title: string;
  channelTitle: string;
  thumbnail: string;
  duration?: string;
}

interface ActiveJob {
  jobId: string;
  videoId: string;
  title: string;
  status: string;
  progress: number;
  format: string;
  error?: string;
}

@Component({
  selector: 'app-modern-downloads',
  templateUrl: './modern-downloads.component.html',
  styleUrls: ['./modern-downloads.component.css']
})
export class ModernDownloadsComponent implements OnInit, OnDestroy {
  query = '';
  results: YtResult[] = [];
  searching = false;
  activeJobs: ActiveJob[] = [];
  queuedIds = new Set<string>();
  format: 'mp3' | 'm4a' | 'flac' | 'opus' = 'mp3';
  selectedPathId: number | null = null;
  subPath = '';
  private pollRef: any;
  private debounce: any;

  constructor(
    private music: MusicService,
    public state: ModernStateService,
    private auth: AuthService
  ) {}

  get isNasUser(): boolean { return this.auth.hasNasAccess(); }

  get writablePaths(): NasPath[] {
    return this.state.paths.filter(p => p.writable);
  }

  ngOnInit() {
    this.selectedPathId = this.state.pathId;
    if (this.isNasUser) this.pollJobs();
  }

  ngOnDestroy() {
    clearInterval(this.pollRef);
    clearTimeout(this.debounce);
  }

  onInput() {
    clearTimeout(this.debounce);
    if (!this.query.trim()) { this.results = []; return; }
    this.debounce = setTimeout(() => this.doSearch(), 500);
  }

  doSearch() {
    if (!this.query.trim()) return;
    this.searching = true;
    this.music.searchYouTube(this.query, 10).subscribe({
      next: (res: any) => {
        this.results = (res.items || []).map((item: any) => ({
          videoId: item.id?.videoId || item.videoId,
          title: item.snippet?.title || item.title,
          channelTitle: item.snippet?.channelTitle || item.channelTitle,
          thumbnail: item.snippet?.thumbnails?.medium?.url || item.thumbnail,
        }));
        this.searching = false;
      },
      error: () => { this.searching = false; }
    });
  }

  download(r: YtResult) {
    const pid = this.selectedPathId ?? this.state.pathId;
    if (pid == null) return;
    this.queuedIds.add(r.videoId);
    this.music.ytDlpQueue(r.videoId, r.title, pid, this.subPath.trim(), this.format).subscribe({
      next: () => this.pollJobs(),
      error: () => this.queuedIds.delete(r.videoId)
    });
  }

  private pollJobs() {
    clearInterval(this.pollRef);
    this.loadJobs();
    this.pollRef = setInterval(() => this.loadJobs(), 4000);
  }

  private loadJobs() {
    this.music.ytDlpActiveJobs().subscribe({
      next: (jobs: any[]) => {
        this.activeJobs = jobs;
        jobs.forEach(j => {
          if (j.status === 'DONE' || j.status === 'ERROR') this.queuedIds.delete(j.videoId);
        });
      },
      error: () => {}
    });
  }
}
