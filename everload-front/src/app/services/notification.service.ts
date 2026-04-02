import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';

export interface NotificationDto {
  id: number;
  type: string;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
  actionUrl?: string;
}

export interface ToastNotification {
  id: string;
  type: 'success' | 'error' | 'info' | 'warning';
  title: string;
  message: string;
  duration: number;
  progress: number;
  intervalRef?: any;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {

  readonly BASE: string = (() => {
    const host = typeof window !== 'undefined' ? window.location.hostname : '';
    return (host === 'localhost' || host === '127.0.0.1') ? 'http://localhost:8080' : '';
  })();

  private toastsSubject = new BehaviorSubject<ToastNotification[]>([]);
  toasts$ = this.toastsSubject.asObservable();

  constructor(private http: HttpClient) {}

  getNotifications(): Observable<NotificationDto[]> {
    return this.http.get<NotificationDto[]>(`${this.BASE}/api/notifications`);
  }

  getUnreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.BASE}/api/notifications/unread-count`);
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${this.BASE}/api/notifications/mark-all-read`, {});
  }

  markRead(id: number): Observable<void> {
    return this.http.post<void>(`${this.BASE}/api/notifications/${id}/read`, {});
  }

  showToast(type: 'success' | 'error' | 'info' | 'warning', title: string, message: string, duration: number = 4000): void {
    const id = Math.random().toString(36).substring(2) + Date.now().toString(36);
    const toast: ToastNotification = { id, type, title, message, duration, progress: 100 };

    const current = this.toastsSubject.value;
    const updated = [...current.slice(-3), toast]; // max 4
    this.toastsSubject.next(updated);

    const interval = 50;
    const steps = duration / interval;
    let step = 0;

    toast.intervalRef = setInterval(() => {
      step++;
      toast.progress = Math.max(0, 100 - (step / steps) * 100);

      if (step >= steps) {
        this.dismissToast(id);
      } else {
        this.toastsSubject.next([...this.toastsSubject.value]);
      }
    }, interval);
  }

  dismissToast(id: string): void {
    const current = this.toastsSubject.value;
    const toast = current.find(t => t.id === id);
    if (toast?.intervalRef) {
      clearInterval(toast.intervalRef);
    }
    this.toastsSubject.next(current.filter(t => t.id !== id));
  }
}
