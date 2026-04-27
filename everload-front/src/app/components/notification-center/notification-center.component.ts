import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { NotificationService, NotificationDto } from '../../services/notification.service';

@Component({
  selector: 'app-notification-center',
  templateUrl: './notification-center.component.html',
  styleUrls: ['./notification-center.component.css']
})
export class NotificationCenterComponent implements OnInit, OnDestroy {
  notifications: NotificationDto[] = [];
  unreadCount = 0;
  isOpen = false;
  private pollInterval: any;
  private lastUnreadCount = 0;
  private toastedIds = new Set<number>();

  constructor(private notificationService: NotificationService) {}

  ngOnInit(): void {
    this.loadUnreadCount();
    this.pollInterval = setInterval(() => this.loadUnreadCount(), 30000);
  }

  ngOnDestroy(): void {
    if (this.pollInterval) clearInterval(this.pollInterval);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.notification-center')) {
      this.isOpen = false;
    }
  }

  toggle(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      this.loadNotifications();
    }
  }

  private loadUnreadCount(): void {
    this.notificationService.getUnreadCount().subscribe({
      next: res => {
        const newCount = res.count;
        if (newCount > this.lastUnreadCount) {
          this.checkForAdminNotices();
        }
        this.lastUnreadCount = newCount;
        this.unreadCount = newCount;
      },
      error: () => {}
    });
  }

  private checkForAdminNotices(): void {
    this.notificationService.getNotifications().subscribe({
      next: list => {
        list
          .filter(n => !n.read && n.type === 'admin_notice' && !this.toastedIds.has(n.id))
          .forEach(n => {
            this.toastedIds.add(n.id);
            this.notificationService.showToast('warning', n.title, n.message, 10000);
          });
      },
      error: () => {}
    });
  }

  private loadNotifications(): void {
    this.notificationService.getNotifications().subscribe({
      next: list => {
        this.notifications = list.slice(0, 20);
      },
      error: () => {}
    });
  }

  markAllRead(): void {
    this.notificationService.markAllRead().subscribe({
      next: () => {
        this.notifications.forEach(n => n.read = true);
        this.unreadCount = 0;
      },
      error: () => {}
    });
  }

  markRead(notification: NotificationDto): void {
    if (notification.read) return;
    this.notificationService.markRead(notification.id).subscribe({
      next: () => {
        notification.read = true;
        if (this.unreadCount > 0) this.unreadCount--;
      },
      error: () => {}
    });
  }

  getTypeIcon(type: string): string {
    switch (type) {
      case 'download_complete': return '✅';
      case 'download_failed': return '❌';
      case 'chat_message': return '💬';
      case 'group_invite': return '👥';
      case 'admin_notice': return '📢';
      default: return '🔔';
    }
  }

  timeAgo(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const now = new Date();
    const diff = Math.floor((now.getTime() - date.getTime()) / 1000);
    if (diff < 60) return 'hace ' + diff + 's';
    if (diff < 3600) return 'hace ' + Math.floor(diff / 60) + 'min';
    if (diff < 86400) return 'hace ' + Math.floor(diff / 3600) + 'h';
    return 'hace ' + Math.floor(diff / 86400) + 'd';
  }
}