import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { ChatService, ChatGroupDto, ActiveUser } from '../../../services/chat.service';
import { NotificationService } from '../../../services/notification.service';
import { AuthService } from '../../../services/auth.service';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-create-group-modal',
  templateUrl: './create-group-modal.component.html',
  styleUrls: ['./create-group-modal.component.css']
})
export class CreateGroupModalComponent implements OnInit {
  @Output() created = new EventEmitter<ChatGroupDto>();
  @Output() cancelled = new EventEmitter<void>();

  name = '';
  description = '';
  userSearch = '';
  activeUsers: ActiveUser[] = [];
  selectedUsernames = new Set<string>();
  isLoading = false;

  constructor(
    private chatService: ChatService,
    private notificationService: NotificationService,
    public authService: AuthService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.chatService.getActiveUsers().subscribe({
      next: users => this.activeUsers = users,
      error: () => {}
    });
  }

  get filteredUsers(): ActiveUser[] {
    if (!this.userSearch.trim()) return this.activeUsers;
    const q = this.userSearch.toLowerCase();
    return this.activeUsers.filter(u => u.username.toLowerCase().includes(q));
  }

  toggleUser(username: string): void {
    if (this.selectedUsernames.has(username)) {
      this.selectedUsernames.delete(username);
    } else {
      this.selectedUsernames.add(username);
    }
  }

  resolveAvatarUrl(url?: string | null): string | null {
    if (!url) return null;
    return url.startsWith('http') ? url : `${this.authService.BASE}${url}`;
  }

  submit(): void {
    if (!this.name.trim()) {
      this.notificationService.showToast(
        'warning',
        this.translate.instant('CHAT.GROUP_NAME_REQUIRED'),
        this.translate.instant('CHAT.GROUP_NAME_REQUIRED_MSG')
      );
      return;
    }

    this.isLoading = true;
    this.chatService.createGroup({
      name: this.name.trim(),
      description: this.description.trim() || undefined,
      type: 'GROUP',
      memberUsernames: Array.from(this.selectedUsernames)
    }).subscribe({
      next: group => {
        this.isLoading = false;
        this.notificationService.showToast(
          'success',
          this.translate.instant('CHAT.GROUP_CREATED'),
          group.name
        );
        this.created.emit(group);
      },
      error: () => {
        this.isLoading = false;
        this.notificationService.showToast(
          'error',
          this.translate.instant('CHAT.ERROR'),
          this.translate.instant('CHAT.ERROR_CREATE_GROUP')
        );
      }
    });
  }

  cancel(): void {
    this.cancelled.emit();
  }

  getInitials(username: string): string {
    return username.charAt(0).toUpperCase();
  }
}