import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { SetupService } from '../services/setup.service';

@Injectable({ providedIn: 'root' })
export class FirstRunGuard {
  constructor(private setup: SetupService, private router: Router) {}
  canActivate() {
    return this.setup.status().pipe(
      map(status => status.required ? this.router.parseUrl('/setup') : true),
      catchError(() => of(true))
    );
  }
}
