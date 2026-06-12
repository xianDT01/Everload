import { Component, OnInit, OnDestroy } from '@angular/core';
import { MusicService } from '../../../../services/music.service';
import { ModernStateService } from '../../modern-state.service';
import { AuthService } from '../../../../services/auth.service';
import { NasFile, NasPath, NasService } from '../../../../services/nas.service';
import { TranslateService } from '@ngx-translate/core';

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
  private static readonly DESTINATION_KEY = 'everload.downloads.destination';

  query = '';
  results: YtResult[] = [];
  searching = false;
  activeJobs: ActiveJob[] = [];
  queuedIds = new Set<string>();
  format: 'mp3' | 'm4a' | 'flac' | 'opus' = 'mp3';
  selectedPathId: number | null = null;
  subPath = '';
  pickerOpen = false;
  pickerPathId: number | null = null;
  pickerSubPath = '';
  pickerFiles: NasFile[] = [];
  pickerLoading = false;
  pickerError = '';
  pickerNewFolder = '';
  pickerShowNewFolder = false;
  private pollRef: any;
  private debounce: any;

  constructor(
    private music: MusicService,
    public state: ModernStateService,
    private auth: AuthService,
    private nas: NasService,
    private translate: TranslateService
  ) {}

  get isNasUser(): boolean { return this.auth.hasNasAccess(); }

  get writablePaths(): NasPath[] {
    return this.state.paths.filter(p => p.writable);
  }

  ngOnInit() {
    this.restoreDestination();
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
    const targetSubPath = this.normalizeSubPath(this.subPath);
    this.persistDestination(pid, targetSubPath);
    this.queuedIds.add(r.videoId);
    this.music.ytDlpQueue(r.videoId, r.title, pid, targetSubPath, this.format).subscribe({
      next: () => this.pollJobs(),
      error: () => this.queuedIds.delete(r.videoId)
    });
  }

  get destinationLabel(): string {
    const path = this.writablePaths.find(p => p.id === this.selectedPathId);
    if (!path) return this.translate.instant('MUSIC.DOWNLOADS_SELECT_FOLDER');
    const sub = this.normalizeSubPath(this.subPath);
    return sub ? `${path.name}/${sub}` : path.name;
  }

  get pickerBreadcrumbs(): { label: string; subPath: string }[] {
    const path = this.writablePaths.find(p => p.id === this.pickerPathId);
    if (!path) return [];
    const crumbs = [{ label: path.name, subPath: '' }];
    let acc = '';
    for (const part of this.pickerSubPath.split('/').filter(Boolean)) {
      acc = acc ? `${acc}/${part}` : part;
      crumbs.push({ label: part, subPath: acc });
    }
    return crumbs;
  }

  get pickerDestinationLabel(): string {
    const path = this.writablePaths.find(p => p.id === this.pickerPathId);
    if (!path) return this.translate.instant('MUSIC.DOWNLOADS_NOT_SELECTED');
    return this.pickerSubPath ? `${path.name}/${this.pickerSubPath}` : path.name;
  }

  openDestinationPicker() {
    this.pickerOpen = true;
    this.pickerPathId = this.selectedPathId ?? this.writablePaths[0]?.id ?? null;
    this.pickerSubPath = this.normalizeSubPath(this.subPath);
    this.pickerError = '';
    this.pickerNewFolder = '';
    this.pickerShowNewFolder = false;
    this.loadPickerFiles();
  }

  closeDestinationPicker() {
    this.pickerOpen = false;
  }

  selectPickerRoot(pathId: number) {
    if (this.pickerPathId === pathId && !this.pickerSubPath) return;
    this.pickerPathId = pathId;
    this.pickerSubPath = '';
    this.pickerShowNewFolder = false;
    this.loadPickerFiles();
  }

  openPickerFolder(file: NasFile) {
    if (!file.directory) return;
    this.pickerSubPath = this.normalizeSubPath(file.path);
    this.pickerShowNewFolder = false;
    this.loadPickerFiles();
  }

  goPickerUp() {
    if (!this.pickerSubPath) return;
    const parts = this.pickerSubPath.split('/').filter(Boolean);
    parts.pop();
    this.pickerSubPath = parts.join('/');
    this.loadPickerFiles();
  }

  goPickerBreadcrumb(subPath: string) {
    this.pickerSubPath = subPath;
    this.loadPickerFiles();
  }

  createPickerFolder() {
    const name = this.pickerNewFolder.trim();
    if (!name || this.pickerPathId == null) return;
    this.pickerError = '';
    this.nas.mkdir(this.pickerPathId, name, this.pickerSubPath).subscribe({
      next: () => {
        this.pickerNewFolder = '';
        this.pickerShowNewFolder = false;
        this.loadPickerFiles();
      },
      error: (err) => {
        this.pickerError = err.error?.error || this.translate.instant('MUSIC.DOWNLOADS_CREATE_FOLDER_ERROR');
      }
    });
  }

  confirmDestination() {
    if (this.pickerPathId == null) return;
    this.selectedPathId = this.pickerPathId;
    this.subPath = this.normalizeSubPath(this.pickerSubPath);
    this.persistDestination(this.selectedPathId, this.subPath);
    this.pickerOpen = false;
  }

  onManualDestinationChange() {
    if (this.selectedPathId == null) return;
    this.subPath = this.normalizeSubPath(this.subPath);
    this.persistDestination(this.selectedPathId, this.subPath);
  }

  private loadPickerFiles() {
    if (this.pickerPathId == null) {
      this.pickerFiles = [];
      return;
    }
    this.pickerLoading = true;
    this.pickerError = '';
    this.nas.browse(this.pickerPathId, this.pickerSubPath).subscribe({
      next: (files) => {
        this.pickerFiles = files.filter(f => f.directory).sort((a, b) => a.name.localeCompare(b.name));
        this.pickerLoading = false;
      },
      error: (err) => {
        this.pickerError = err.error?.error || this.translate.instant('MUSIC.DOWNLOADS_READ_FOLDER_ERROR');
        this.pickerFiles = [];
        this.pickerLoading = false;
      }
    });
  }

  private restoreDestination() {
    const fallback = this.state.pathId ?? this.writablePaths[0]?.id ?? null;
    this.selectedPathId = fallback;
    try {
      const raw = localStorage.getItem(ModernDownloadsComponent.DESTINATION_KEY);
      if (!raw) return;
      const saved = JSON.parse(raw) as { pathId?: number; subPath?: string };
      if (saved.pathId && this.writablePaths.some(p => p.id === saved.pathId)) {
        this.selectedPathId = saved.pathId;
        this.subPath = this.normalizeSubPath(saved.subPath || '');
      }
    } catch {
      localStorage.removeItem(ModernDownloadsComponent.DESTINATION_KEY);
    }
  }

  private persistDestination(pathId: number, subPath: string) {
    localStorage.setItem(ModernDownloadsComponent.DESTINATION_KEY, JSON.stringify({ pathId, subPath }));
  }

  private normalizeSubPath(value: string | null | undefined): string {
    return (value || '')
      .replace(/\\/g, '/')
      .split('/')
      .map(part => part.trim())
      .filter(part => part && part !== '.' && part !== '..')
      .join('/');
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
