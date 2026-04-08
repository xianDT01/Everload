import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { NotificationService, ToastNotification } from '../../services/notification.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-notification-toast',
  templateUrl: './notification-toast.component.html',
  styleUrls: ['./notification-toast.component.css']
})
export class NotificationToastComponent implements OnInit, OnDestroy {
  toasts: ToastNotification[] = [];
  private sub!: Subscription;

  constructor(private notificationService: NotificationService, private router: Router) {}

  ngOnInit(): void {
    this.sub = this.notificationService.toasts$.subscribe(toasts => {
      this.toasts = toasts;
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  dismiss(id: string): void {
    this.notificationService.dismissToast(id);
  }

  getIcon(type: string): string {
    switch (type) {
      case 'success': return '✅';
      case 'error': return '❌';
      case 'warning': return '⚠️';
      case 'info': return 'ℹ️';
      default: return '🔔';
    }
  }

  replyToChat(groupId: number, toastId: string): void {
    this.notificationService.dismissToast(toastId);
    this.router.navigate(['/chat'], { queryParams: { group: groupId } });
  }
}