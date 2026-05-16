import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, shareReplay } from 'rxjs';
import { NasPath, NasService } from '../../services/nas.service';
import { LibraryOverviewDto, MusicService } from '../../services/music.service';

interface OverviewEntry {
  obs: Observable<LibraryOverviewDto>;
  expiresAt: number;
}

const OVERVIEW_TTL_MS = 2 * 60 * 1000; // 2 minutes

@Injectable({ providedIn: 'root' })
export class ModernStateService {
  paths: NasPath[] = [];
  private _pathId = new BehaviorSubject<number | null>(null);
  pathId$ = this._pathId.asObservable();

  private _showQueue = new BehaviorSubject<boolean>(false);
  private _showFullscreen = new BehaviorSubject<boolean>(false);
  private _selectedArtistName = new BehaviorSubject<string>('');
  showQueue$ = this._showQueue.asObservable();
  showFullscreen$ = this._showFullscreen.asObservable();
  selectedArtistName$ = this._selectedArtistName.asObservable();

  get pathId(): number | null { return this._pathId.value; }
  get showQueue(): boolean { return this._showQueue.value; }
  get showFullscreen(): boolean { return this._showFullscreen.value; }
  get selectedArtistName(): string { return this._selectedArtistName.value; }

  private overviewCache = new Map<number, OverviewEntry>();

  constructor(private nas: NasService, private music: MusicService) {
    this.nas.getPaths().subscribe(paths => {
      this.paths = paths.filter(p => p.readable);
      if (this.paths.length) {
        const saved = localStorage.getItem('modern_path_id');
        const found = saved ? this.paths.find(p => p.id === +saved) : null;
        const id = found ? found.id : this.paths[0].id;
        this._pathId.next(id);
        this.prefetchOverview(id);
      }
    });
  }

  selectPath(id: number) {
    this._pathId.next(id);
    localStorage.setItem('modern_path_id', String(id));
    this.overviewCache.delete(id); // force refresh on explicit path change
    this.prefetchOverview(id);
  }

  /** Shared, cached library overview. One HTTP request per pathId per TTL window. */
  getOverview(pathId: number): Observable<LibraryOverviewDto> {
    const now = Date.now();
    const cached = this.overviewCache.get(pathId);
    if (cached && now < cached.expiresAt) return cached.obs;
    const obs = this.music.getLibraryOverview(pathId, 5000).pipe(shareReplay(1));
    this.overviewCache.set(pathId, { obs, expiresAt: now + OVERVIEW_TTL_MS });
    return obs;
  }

  /** Call after a library re-index to force the next getOverview() to fetch fresh data. */
  invalidateOverview(pathId?: number) {
    if (pathId !== undefined) this.overviewCache.delete(pathId);
    else this.overviewCache.clear();
  }

  private prefetchOverview(pathId: number) {
    this.getOverview(pathId).subscribe({ error: () => {} });
  }

  toggleQueue() { this._showQueue.next(!this._showQueue.value); }
  toggleFullscreen() { this._showFullscreen.next(!this._showFullscreen.value); }
  closeFullscreen() { this._showFullscreen.next(false); }
  closeQueue() { this._showQueue.next(false); }
  selectArtist(name: string) { this._selectedArtistName.next((name || '').trim()); }
}
