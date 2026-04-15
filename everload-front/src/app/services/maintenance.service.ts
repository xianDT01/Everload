import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';

export interface MaintenanceState {
  active: boolean;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class MaintenanceService {

  private readonly BASE = (() => {
    const host = typeof window !== 'undefined' ? window.location.hostname : '';
    return (host === 'localhost' || host === '127.0.0.1') ? 'http://localhost:8080' : '';
  })();

  private state$ = new BehaviorSubject<MaintenanceState>({ active: false, message: '' });
  readonly maintenance$ = this.state$.asObservable();

  constructor(private http: HttpClient) {}

  /** Called on app startup to fetch initial maintenance state. */
  checkInitial(): void {
    this.http.get<MaintenanceState>(`${this.BASE}/api/maintenance/status`).subscribe({
      next: data => this.state$.next(data),
      error: () => {} // If the check fails, assume not in maintenance
    });
  }

  /** Called by the MaintenanceInterceptor when a 503 maintenance response is detected. */
  setMaintenance(active: boolean, message?: string): void {
    this.state$.next({
      active,
      message: message || 'La aplicación está en mantenimiento. Inténtalo más tarde.'
    });
  }

  get isActive(): boolean {
    return this.state$.value.active;
  }

  get currentState(): MaintenanceState {
    return this.state$.value;
  }
}