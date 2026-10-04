import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, shareReplay } from 'rxjs';
import { NasPath, NasService } from '../../services/nas.service';
import { LibraryOverviewDto, MusicService } from '../../services/music.service';

interface OverviewEntry {
  obs: Observable<LibraryOverviewDto>;
  expiresAt: number;
}

const OVERVIEW_TTL_MS = 2 * 60 * 1000; // 2 minutes
const HOME_OVERVIEW_LIMIT = 400;

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

  private overviewCache = new Map<string, OverviewEntry>();

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
    this.invalidateOverview(id); // force refresh on explicit path change
    this.prefetchOverview(id);
  }

  /** Shared overview cache. The home page uses a small slice; library views request the full slice. */
  getOverview(pathId: number, limit = 5000): Observable<LibraryOverviewDto> {
    const now = Date.now();
    const cacheKey = this.cacheKey(pathId, limit);
    const cached = this.overviewCache.get(cacheKey);
    if (cached && now < cached.expiresAt) return cached.obs;
    const obs = this.music.getLibraryOverview(pathId, limit).pipe(shareReplay(1));
    this.overviewCache.set(cacheKey, { obs, expiresAt: now + OVERVIEW_TTL_MS });
    return obs;
  }

  /** Call after a library re-index to force the next getOverview() to fetch fresh data. */
  invalidateOverview(pathId?: number) {
    if (pathId !== undefined) {
      for (const key of this.overviewCache.keys()) {
        if (key.startsWith(`${pathId}:`)) this.overviewCache.delete(key);
      }
    }
    else this.overviewCache.clear();
  }

  private prefetchOverview(pathId: number) {
    this.getOverview(pathId, HOME_OVERVIEW_LIMIT).subscribe({ error: () => {} });
  }

  private cacheKey(pathId: number, limit: number): string {
    return `${pathId}:${Math.max(1, Math.min(limit, 10000))}`;
  }

  toggleQueue() { this._showQueue.next(!this._showQueue.value); }
  toggleFullscreen() { this._showFullscreen.next(!this._showFullscreen.value); }
  closeFullscreen() { this._showFullscreen.next(false); }
  closeQueue() { this._showQueue.next(false); }
  selectArtist(name: string) { this._selectedArtistName.next((name || '').trim()); }
}
