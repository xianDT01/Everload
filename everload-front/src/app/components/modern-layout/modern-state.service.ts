import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { NasPath, NasService } from '../../services/nas.service';

@Injectable({ providedIn: 'root' })
export class ModernStateService {
  paths: NasPath[] = [];
  private _pathId = new BehaviorSubject<number | null>(null);
  pathId$ = this._pathId.asObservable();

  get pathId(): number | null { return this._pathId.value; }

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
}
