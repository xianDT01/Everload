import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService } from './services/auth.service';
import { ChatService } from './services/chat.service';
import { MusicService } from './services/music.service';
import { NotificationService } from './services/notification.service';
import { MaintenanceService, MaintenanceState } from './services/maintenance.service';
import { PwaUpdateService } from './services/pwa-update.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'everload-front';
  playerMode: 'full' | 'mini' | 'hidden' = 'mini';
  maintenanceState: MaintenanceState = { active: false, message: '' };
  currentUrl = '/';

  private authSub?: Subscription;
  private alertSub?: Subscription;
  private maintenanceSub?: Subscription;
  private heartbeatRef: any = null;

  constructor(
    public authService: AuthService,
    private chatService: ChatService,
    public musicService: MusicService,
    private notificationService: NotificationService,
    public maintenanceService: MaintenanceService,
    private pwaUpdate: PwaUpdateService,
    private http: HttpClient,
    private router: Router
  ) {
    this.router.events.subscribe(event => {
      if (event instanceof NavigationEnd) {
        const url = event.urlAfterRedirects || event.url;
        this.currentUrl = url;
        if (url.includes('/nas-music')) {
          if (url.includes('mode=deck')) {
            this.playerMode = 'hidden';
          } else {
            this.playerMode = 'full';
          }
        } else {
          this.playerMode = 'mini';
        }
      }
    });
  }

  ngOnInit(): void {
    // Initialize PWA update checks
    this.pwaUpdate.init();

    // Check maintenance state on startup
    this.maintenanceService.checkInitial();
    this.maintenanceSub = this.maintenanceService.maintenance$.subscribe(state => {
      this.maintenanceState = state;
    });

    // Subscribe to auth state: start/stop global chat polling
    this.authSub = this.authService.currentUser$.subscribe(user => {
      if (user) {
        this.chatService.startGlobalPolling();
        this.startHeartbeat();

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
        this.stopHeartbeat();
        this.alertSub?.unsubscribe();
      }
    });
  }

  ngOnDestroy(): void {
    this.authSub?.unsubscribe();
    this.alertSub?.unsubscribe();
    this.maintenanceSub?.unsubscribe();
    this.stopHeartbeat();
  }

  @HostListener('window:beforeunload')
  onBeforeUnload(): void {
    const token = this.authService.getToken();
    const base = this.chatService.BASE;
    if (token) {
      // keepalive ensures the request completes even after the page starts unloading
      fetch(`${base}/api/presence/offline`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
        keepalive: true
      }).catch(() => {});
    }
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.sendHeartbeat(); // immediate
    this.heartbeatRef = setInterval(() => this.sendHeartbeat(), 30_000);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatRef) {
      clearInterval(this.heartbeatRef);
      this.heartbeatRef = null;
    }
  }

  private sendHeartbeat(): void {
    this.http.post(`${this.chatService.BASE}/api/presence/heartbeat`, {})
      .subscribe({ error: () => {} });
  }
}