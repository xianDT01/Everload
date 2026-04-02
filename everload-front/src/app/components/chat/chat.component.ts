import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewChecked, HostListener } from '@angular/core';
import { Router } from '@angular/router';
import { ChatService, ChatGroupDto, ChatMessageDto, ActiveUser } from '../../services/chat.service';
import { NotificationService } from '../../services/notification.service';
import { AuthService } from '../../services/auth.service';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  styleUrls: ['./chat.component.css']
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewChecked {
  @ViewChild('messagesContainer') messagesContainer!: ElementRef;

  groups: ChatGroupDto[] = [];
  selectedGroup: ChatGroupDto | null = null;
  messages: ChatMessageDto[] = [];
  messageInput = '';
  sidebarVisible = true;
  groupSearch = '';

  showCreateGroupModal = false;
  showPrivateChatModal = false;
  activeUsers: ActiveUser[] = [];
  userSearch = '';

  currentUsername = '';
  isAdmin = false;

  emojiPickerOpen = false;
  readonly emojis = [
    '😀','😂','😍','😎','🤔','😭','😅','😊','🎉','❤️',
    '👍','🔥','✅','💪','🙏','😤','🤣','😬','🥰','💀',
    '👀','🌟','⚡','💯','🚀','🎵','🎮','💻','📱','🌈',
    '🤩','😴','🥳','😡','🤯','😈','👋','🫡','🤝','🎁',
    '🍕','☕','🍺','🎂','🌮','🍔','🍦','🐶','🐱','🦊'
  ];

  @ViewChild('messageTextarea') messageTextarea!: ElementRef<HTMLTextAreaElement>;
  @ViewChild('emojiPickerRef') emojiPickerRef!: ElementRef;
  @ViewChild('emojiBtnRef') emojiBtnRef!: ElementRef;

  private groupsSub!: Subscription;
  private shouldScrollToBottom = false;

  constructor(
    private chatService: ChatService,
    private notificationService: NotificationService,
    private authService: AuthService,
    private router: Router,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    const user = this.authService.getCurrentUser();
    this.currentUsername = user?.username || '';
    this.isAdmin = user?.role === 'ADMIN';

    this.groupsSub = this.chatService.groups$.subscribe(groups => {
      this.groups = groups;
    });

    this.chatService.refreshGroups();
  }

  ngOnDestroy(): void {
    this.groupsSub?.unsubscribe();
    this.chatService.stopPolling();
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  private scrollToBottom(): void {
    try {
      if (this.messagesContainer) {
        const el = this.messagesContainer.nativeElement;
        el.scrollTop = el.scrollHeight;
      }
    } catch {}
  }

  get filteredGroups(): ChatGroupDto[] {
    if (!this.groupSearch.trim()) return this.groups;
    const q = this.groupSearch.toLowerCase();
    return this.groups.filter(g => g.name.toLowerCase().includes(q));
  }

  selectGroup(group: ChatGroupDto): void {
    if (this.selectedGroup?.id === group.id) return;
    this.selectedGroup = group;
    this.messages = [];
    this.sidebarVisible = false;
    this.chatService.stopPolling();

    this.chatService.getMessages(group.id).subscribe({
      next: msgs => {
        this.messages = msgs;
        this.shouldScrollToBottom = true;
      },
      error: () => this.notificationService.showToast(
        'error',
        this.translate.instant('CHAT.ERROR'),
        this.translate.instant('CHAT.ERROR_LOAD_MESSAGES')
      )
    });

    this.chatService.startPolling(group.id, (msgs) => {
      if (msgs.length !== this.messages.length) {
        this.messages = msgs;
        this.shouldScrollToBottom = true;
      }
    });
  }

  sendMessage(): void {
    if (!this.messageInput.trim() || !this.selectedGroup) return;

    const content = this.messageInput.trim();
    this.messageInput = '';

    this.chatService.sendMessage(this.selectedGroup.id, content).subscribe({
      next: msg => {
        this.messages = [...this.messages, msg];
        this.shouldScrollToBottom = true;

        // Update last message in group list
        const g = this.groups.find(gr => gr.id === this.selectedGroup!.id);
        if (g) {
          g.lastMessage = this.currentUsername + ': ' + content.substring(0, 50);
          g.lastMessageTime = msg.sentAt;
        }
      },
      error: () => {
        this.notificationService.showToast(
          'error',
          this.translate.instant('CHAT.ERROR'),
          this.translate.instant('CHAT.ERROR_SEND_MESSAGE')
        );
        this.messageInput = content; // restore
      }
    });
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  isOwnMessage(msg: ChatMessageDto): boolean {
    return msg.senderUsername === this.currentUsername;
  }

  canSendMessage(): boolean {
    if (!this.selectedGroup) return false;
    if (this.selectedGroup.type === 'ANNOUNCEMENT') {
      return this.isAdmin;
    }
    return true;
  }

  isAnnouncementGroup(group: ChatGroupDto): boolean {
    return group.type === 'ANNOUNCEMENT';
  }

  getGroupIcon(group: ChatGroupDto): string {
    if (group.type === 'ANNOUNCEMENT') return '📢';
    if (group.type === 'PRIVATE') return '👤';
    return '👥';
  }

  getInitials(name: string): string {
    return name.charAt(0).toUpperCase();
  }

  private getDateLocale(): string {
    const lang = this.translate.currentLang || 'es';
    return lang === 'en' ? 'en-GB' : 'es-ES';
  }

  formatTime(dateStr?: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const locale = this.getDateLocale();
    if (diff < 86400000) {
      return date.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' });
    }
    return date.toLocaleDateString(locale, { day: '2-digit', month: 'short' });
  }

  formatMessageTime(dateStr?: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleTimeString(this.getDateLocale(), { hour: '2-digit', minute: '2-digit' });
  }

  resolveAvatarUrl(url?: string | null): string | null {
    if (!url) return null;
    return url.startsWith('http') ? url : `${this.authService.BASE}${url}`;
  }

  toggleEmojiPicker(): void {
    this.emojiPickerOpen = !this.emojiPickerOpen;
  }

  insertEmoji(emoji: string): void {
    const textarea = this.messageTextarea?.nativeElement;
    if (textarea) {
      const start = textarea.selectionStart ?? this.messageInput.length;
      const end = textarea.selectionEnd ?? start;
      this.messageInput = this.messageInput.slice(0, start) + emoji + this.messageInput.slice(end);
      setTimeout(() => {
        textarea.focus();
        textarea.setSelectionRange(start + emoji.length, start + emoji.length);
      });
    } else {
      this.messageInput += emoji;
    }
    this.emojiPickerOpen = false;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.emojiPickerOpen) return;
    const picker = this.emojiPickerRef?.nativeElement;
    const btn = this.emojiBtnRef?.nativeElement;
    if (picker && btn &&
        !picker.contains(event.target as Node) &&
        !btn.contains(event.target as Node)) {
      this.emojiPickerOpen = false;
    }
  }

  backToSidebar(): void {
    this.sidebarVisible = true;
    this.chatService.stopPolling();
  }

  goHome(): void {
    this.router.navigate(['/']);
  }

  openCreateGroup(): void {
    this.showCreateGroupModal = true;
  }

  onGroupCreated(group: ChatGroupDto): void {
    this.showCreateGroupModal = false;
    this.chatService.refreshGroups();
    setTimeout(() => this.selectGroup(group), 300);
  }

  openPrivateChatModal(): void {
    this.chatService.getActiveUsers().subscribe({
      next: users => {
        this.activeUsers = users;
        this.showPrivateChatModal = true;
      },
      error: () => this.notificationService.showToast(
        'error',
        this.translate.instant('CHAT.ERROR'),
        this.translate.instant('CHAT.ERROR_LOAD_USERS')
      )
    });
  }

  get filteredActiveUsers(): ActiveUser[] {
    if (!this.userSearch.trim()) return this.activeUsers;
    const q = this.userSearch.toLowerCase();
    return this.activeUsers.filter(u => u.username.toLowerCase().includes(q));
  }

  startPrivateChat(username: string): void {
    this.showPrivateChatModal = false;
    this.chatService.startPrivateChat(username).subscribe({
      next: group => {
        this.chatService.refreshGroups();
        setTimeout(() => this.selectGroup(group), 300);
      },
      error: () => this.notificationService.showToast(
        'error',
        this.translate.instant('CHAT.ERROR'),
        this.translate.instant('CHAT.ERROR_START_CHAT')
      )
    });
  }

  trackByMessageId(index: number, msg: ChatMessageDto): number {
    return msg.id;
  }

  trackByGroupId(index: number, group: ChatGroupDto): number {
    return group.id;
  }
}