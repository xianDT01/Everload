import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { NasPath, NasService } from '../../services/nas.service';

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

  constructor(private nas: NasService) {
    this.nas.getPaths().subscribe(paths => {
      this.paths = paths.filter(p => p.readable);
      if (this.paths.length) {
        const saved = localStorage.getItem('modern_path_id');
        const found = saved ? this.paths.find(p => p.id === +saved) : null;
        this._pathId.next(found ? found.id : this.paths[0].id);
      }
    });
  }

  selectPath(id: number) {
    this._pathId.next(id);
    localStorage.setItem('modern_path_id', String(id));
  }

  toggleQueue() { this._showQueue.next(!this._showQueue.value); }
  toggleFullscreen() { this._showFullscreen.next(!this._showFullscreen.value); }
  closeFullscreen() { this._showFullscreen.next(false); }
  closeQueue() { this._showQueue.next(false); }
  selectArtist(name: string) { this._selectedArtistName.next((name || '').trim()); }
}
