import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { AuthService } from './services/auth.service';
import { ChatService } from './services/chat.service';
import { NotificationService } from './services/notification.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'everload-front';

  private authSub?: Subscription;
  private alertSub?: Subscription;

  constructor(
    private authService: AuthService,
    private chatService: ChatService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    // Subscribe to auth state: start/stop global chat polling
    this.authSub = this.authService.currentUser$.subscribe(user => {
      if (user) {
        this.chatService.startGlobalPolling();

        // Subscribe to new message alerts for toast notifications (global)
        this.alertSub?.unsubscribe();
        this.alertSub = this.chatService.newMessageAlert$.subscribe(alert => {
          // For private chats show "sender_name" as title; for groups show "group: sender"
          const title = alert.isPrivate
            ? `💬 ${alert.senderName}`
            : `💬 ${alert.groupName}`;
          const message = alert.isPrivate
            ? alert.content
            : (alert.senderName ? `${alert.senderName}: ${alert.content}` : alert.content);

          // Resolve relative avatar URL to absolute
          let avatarUrl: string | undefined;
          if (alert.senderAvatarUrl) {
            avatarUrl = alert.senderAvatarUrl.startsWith('http')
              ? alert.senderAvatarUrl
              : `${this.chatService.BASE}${alert.senderAvatarUrl}`;
          }

          this.notificationService.showToast('info', title, message, 6000, avatarUrl, alert.groupId);
        });
      } else {
        // User logged out: stop global polling and clear notifications
        this.chatService.stopGlobalPolling();
        this.alertSub?.unsubscribe();
      }
    });
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
    this.alertSub?.unsubscribe();
  }
}