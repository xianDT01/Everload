import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, catchError, distinctUntilChanged, map, of, Subscription, switchMap, tap } from 'rxjs';
import { AuthService } from './auth.service';
import { ApiBaseService } from './api-base.service';

export type FloatingPlayerStyle = 'island' | 'bar';
export type FloatingPlayerMode = 'hidden' | 'mini' | 'full' | 'island';

export function floatingPlayerMode(url: string, style: FloatingPlayerStyle | null): FloatingPlayerMode {
  const path = url.split(/[?#]/)[0].replace(/\/+$/, '');
  if (path === '/modern' || path.startsWith('/modern/')) return 'hidden';
  if (path === '/nas-music' || path.startsWith('/nas-music/')) {
    return new URLSearchParams(url.split('?')[1]?.split('#')[0] || '').get('mode') === 'deck' ? 'hidden' : 'full';
  }
  return style === 'island' || (style === null && path === '/chat') ? 'island' : 'mini';
}

@Injectable({ providedIn: 'root' })
export class PlayerPreferencesService implements OnDestroy {
  readonly style$ = new BehaviorSubject<FloatingPlayerStyle | null>(null);
  private readonly authSub: Subscription;
  private revision = 0;

  constructor(private http: HttpClient, private apiBase: ApiBaseService, private auth: AuthService) {
    this.authSub = this.auth.currentUser$.pipe(
      map(user => user?.username || null),
      distinctUntilChanged(),
      switchMap(username => {
        const revision = ++this.revision;
        this.style$.next(null);
        const load = username ? this.http.get<{ floatingPlayerStyle?: string }>(this.apiBase.withBackend('/api/user/profile')).pipe(
          map(profile => profile.floatingPlayerStyle), catchError(() => of(null))
        ) : of(null);
        return load.pipe(map(style => ({ style, revision })));
      })
    ).subscribe(({ style, revision }) => { if (revision === this.revision) this.accept(style); });
  }

  accept(style: unknown): void {
    this.revision++;
    this.style$.next(style === 'island' || style === 'bar' ? style : null);
  }

  save(style: FloatingPlayerStyle) {
    const username = this.auth.getCurrentUser()?.username;
    return this.http.put<{ user: { floatingPlayerStyle?: string }; newToken: string }>(
      this.apiBase.withBackend('/api/user/profile'), { floatingPlayerStyle: style }
    ).pipe(tap(response => {
      if (this.auth.getCurrentUser()?.username !== username) return;
      this.accept(response.user.floatingPlayerStyle);
      if (response.newToken) this.auth.updateToken(response.newToken);
    }));
  }

  ngOnDestroy(): void { this.authSub.unsubscribe(); }
}
