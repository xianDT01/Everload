import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';

export interface AuthResponse {
  token: string;
  username: string;
  email: string;
  role: 'ADMIN' | 'NAS_USER' | 'BASIC_USER';
  status: string;
  avatarUrl?: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {

  readonly BASE: string = (() => {
    const host = typeof window !== 'undefined' ? window.location.hostname : '';
    return (host === 'localhost' || host === '127.0.0.1') ? 'http://localhost:8080' : '';
  })();

  private currentUserSubject = new BehaviorSubject<AuthResponse | null>(this.loadFromStorage());
  currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {}

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.BASE}/api/auth/register`, request);
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.BASE}/api/auth/login`, request).pipe(
      tap(response => {
        localStorage.setItem('auth_user', JSON.stringify(response));
        localStorage.setItem('auth_token', response.token);
        this.currentUserSubject.next(response);
      })
    );
  }

  logout(): void {
    const token = this.getToken();
    if (token) {
      // Revoke JWT + mark offline — fire-and-forget
      this.http.post(`${this.BASE}/api/auth/logout`, {}).subscribe({ error: () => {} });
    }
    localStorage.removeItem('auth_user');
    localStorage.removeItem('auth_token');
    this.currentUserSubject.next(null);
  }

  uploadAvatar(file: File): Observable<{ message: string; avatarUrl: string }> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<{ message: string; avatarUrl: string }>(
      `${this.BASE}/api/user/avatar`, formData
    ).pipe(
      tap(response => {
        const current = this.getCurrentUser();
        if (current) {
          const updated = { ...current, avatarUrl: response.avatarUrl };
          localStorage.setItem('auth_user', JSON.stringify(updated));
          this.currentUserSubject.next(updated);
        }
      })
    );
  }

  removeAvatar(): Observable<any> {
    return this.http.delete(`${this.BASE}/api/user/avatar`).pipe(
      tap(() => {
        const current = this.getCurrentUser();
        if (current) {
          const updated = { ...current, avatarUrl: undefined };
          localStorage.setItem('auth_user', JSON.stringify(updated));
          this.currentUserSubject.next(updated);
        }
      })
    );
  }

  getAvatarUrl(): string | null {
    const url = this.getCurrentUser()?.avatarUrl;
    if (!url) return null;
    return url.startsWith('http') ? url : `${this.BASE}${url}`;
  }

  getToken(): string | null {
    return localStorage.getItem('auth_token');
  }

  getCurrentUser(): AuthResponse | null {
    return this.currentUserSubject.value;
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  isAdmin(): boolean {
    return this.getCurrentUser()?.role === 'ADMIN';
  }

  hasNasAccess(): boolean {
    const role = this.getCurrentUser()?.role;
    return role === 'ADMIN' || role === 'NAS_USER';
  }

  isPending(): boolean {
    return this.getCurrentUser()?.status === 'PENDING';
  }

  updateToken(token: string): void {
    localStorage.setItem('auth_token', token);
  }

  updateStoredUser(partial: Partial<AuthResponse>): void {
    const current = this.getCurrentUser();
    if (!current) return;
    const updated = { ...current, ...partial };
    localStorage.setItem('auth_user', JSON.stringify(updated));
    this.currentUserSubject.next(updated);
  }

  private loadFromStorage(): AuthResponse | null {
    try {
      const stored = localStorage.getItem('auth_user');
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  }
}