import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Subscription, timeout } from 'rxjs';
import { ApiBaseService } from './api-base.service';

export interface MaintenanceState {
  active: boolean;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class MaintenanceService implements OnDestroy {
  private pollTimer?: ReturnType<typeof setInterval>;
  private checking = false;
  private statusSub?: Subscription;
  private readonly onResume = () => {
    if (!document.hidden) this.checkStatus();
  };

  private state$ = new BehaviorSubject<MaintenanceState>({ active: false, message: '' });
  readonly maintenance$ = this.state$.asObservable();

  constructor(private http: HttpClient, private apiBase: ApiBaseService) {}

  /** Called on app startup to fetch initial maintenance state. */
  checkInitial(): void {
    this.checkStatus();
    if (!this.pollTimer) {
      this.pollTimer = setInterval(this.onResume, 15000);
      document.addEventListener('visibilitychange', this.onResume);
      window.addEventListener('online', this.onResume);
    }
  }

  ngOnDestroy(): void {
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.pollTimer = undefined;
    this.statusSub?.unsubscribe();
    this.checking = false;
    document.removeEventListener('visibilitychange', this.onResume);
    window.removeEventListener('online', this.onResume);
  }

  private checkStatus(): void {
    if (this.checking) return;
    this.checking = true;
    this.statusSub = this.http.get<MaintenanceState>(this.apiBase.withBackend('/api/maintenance/status'),
      { params: { 'ngsw-bypass': 'true' } })
      .pipe(timeout(8000)).subscribe({
        next: data => {
          this.checking = false;
          if (typeof data?.active === 'boolean') this.state$.next(data);
        },
        error: () => { this.checking = false; }
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
