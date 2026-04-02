import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { NasService, NasPath, NasFile } from '../../services/nas.service';

@Component({
  selector: 'app-nas-browser',
  templateUrl: './nas-browser.component.html',
  styleUrls: ['./nas-browser.component.css']
})
export class NasBrowserComponent implements OnInit {

  @Input() downloadFileName: string = '';
  @Input() mode: 'browse' | 'select' = 'browse';
  @Output() pathSelected = new EventEmitter<{ pathId: number; subPath: string }>();
  @Output() closed = new EventEmitter<void>();

  paths: NasPath[] = [];
  selectedPath: NasPath | null = null;
  files: NasFile[] = [];
  currentSubPath = '';
  breadcrumbs: string[] = [];
  newFolderName = '';
  showNewFolder = false;
  loading = false;
  error = '';
  mensaje = '';

  ngOnInit(): void {
    this.loadPaths();
  }

  private nasService: NasService;
  constructor(nasService: NasService, private translate: TranslateService) {
    this.nasService = nasService;
  }

  loadPaths(): void {
    this.nasService.getPaths().subscribe({
      next: (paths) => this.paths = paths,
      error: () => this.error = this.translate.instant('NAS.ERROR_LOAD_PATHS')
    });
  }

  selectRoot(path: NasPath): void {
    this.selectedPath = path;
    this.currentSubPath = '';
    this.breadcrumbs = [];
    this.browseDir();
  }

  browseDir(subPath?: string): void {
    if (!this.selectedPath) return;
    if (subPath !== undefined) {
      this.currentSubPath = subPath;
      this.buildBreadcrumbs(subPath);
    }
    this.loading = true;
    this.error = '';
    this.nasService.browse(this.selectedPath.id, this.currentSubPath).subscribe({
      next: (files) => { this.files = files; this.loading = false; },
      error: (err) => {
        this.error = err.error?.error || this.translate.instant('NAS.ERROR_LIST_FILES');
        this.loading = false;
      }
    });
  }

  enterFolder(file: NasFile): void {
    if (!file.directory) return;
    this.currentSubPath = file.path;
    this.buildBreadcrumbs(file.path);
    this.browseDir();
  }

  goUp(): void {
    if (!this.currentSubPath) return;
    const parts = this.currentSubPath.split('/').filter(p => p);
    parts.pop();
    this.currentSubPath = parts.join('/');
    this.buildBreadcrumbs(this.currentSubPath);
    this.browseDir();
  }

  navigateBreadcrumb(index: number): void {
    const parts = this.breadcrumbs.slice(0, index + 1);
    this.currentSubPath = parts.join('/');
    this.buildBreadcrumbs(this.currentSubPath);
    this.browseDir();
  }

  createFolder(): void {
    if (!this.newFolderName.trim() || !this.selectedPath) return;
    this.nasService.mkdir(this.selectedPath.id, this.newFolderName, this.currentSubPath).subscribe({
      next: () => {
        this.newFolderName = '';
        this.showNewFolder = false;
        this.browseDir();
      },
      error: (err) => this.error = err.error?.error || this.translate.instant('NAS.ERROR_CREATE_FOLDER')
    });
  }

  deleteItem(file: NasFile): void {
    if (!this.selectedPath) return;
    if (!confirm(`${this.translate.instant('NAS.CONFIRM_DELETE')} "${file.name}"?`)) return;
    this.nasService.deleteFile(this.selectedPath.id, file.path).subscribe({
      next: () => this.browseDir(),
      error: (err) => this.error = err.error?.error || this.translate.instant('NAS.ERROR_DELETE')
    });
  }

  selectDestination(): void {
    if (!this.selectedPath) return;
    this.pathSelected.emit({ pathId: this.selectedPath.id, subPath: this.currentSubPath });
  }

  close(): void {
    this.closed.emit();
  }

  formatSize(bytes: number): string {
    if (bytes === 0) return '';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  private buildBreadcrumbs(path: string): void {
    this.breadcrumbs = path ? path.split('/').filter(p => p) : [];
  }
}