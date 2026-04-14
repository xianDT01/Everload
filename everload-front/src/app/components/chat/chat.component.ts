import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewChecked, HostListener } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { ChatService, ChatGroupDto, ChatMessageDto, ActiveUser, YoutubeSharePayload } from '../../services/chat.service';
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
  showGroupInfoModal = false;
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

  // ── Themes ─────────────────────────────────────────────────────────────────
  currentTheme = 'everload';
  showThemePicker = false;
  readonly themes = [
    { id: 'everload', label: 'EverLoad', color: '#e94560' },
    { id: 'whatsapp', label: 'WhatsApp', color: '#25d366' },
    { id: 'telegram', label: 'Telegram', color: '#2aabee' },
    { id: 'discord', label: 'Discord', color: '#5865f2' }
  ];

  // ── Mute UI ─────────────────────────────────────────────────────────────────
  showMutePicker = false;

  // ── Chat options (clear / delete) ─────────────────────────────────────────
  showChatOptions = false;
  showClearConfirm = false;
  showDeleteConfirm = false;

  // ── Reply ──────────────────────────────────────────────────────────────────
  replyTo: ChatMessageDto | null = null;

  // ── Search ─────────────────────────────────────────────────────────────────
  searchMode = false;
  searchQuery = '';
  searchResults: ChatMessageDto[] | null = null;

  // ── Message actions (hover) ────────────────────────────────────────────────
  hoveredMessageId: number | null = null;

  @ViewChild('messageTextarea') messageTextarea!: ElementRef<HTMLTextAreaElement>;
  @ViewChild('emojiPickerRef') emojiPickerRef!: ElementRef;
  @ViewChild('emojiBtnRef') emojiBtnRef!: ElementRef;
  @ViewChild('chatOptionsPanelRef') chatOptionsPanelRef!: ElementRef;
  @ViewChild('chatOptionsBtnRef') chatOptionsBtnRef!: ElementRef;

  private groupsSub!: Subscription;
  private shouldScrollToBottom = false;
  private userScrolledUp = false;  // true when user has scrolled above the bottom

  constructor(
    private chatService: ChatService,
    private notificationService: NotificationService,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    const savedTheme = localStorage.getItem('chat_theme');
    if (savedTheme) this.currentTheme = savedTheme;

    const user = this.authService.getCurrentUser();
    this.currentUsername = user?.username || '';
    this.isAdmin = user?.role === 'ADMIN';

    this.groupsSub = this.chatService.groups$.subscribe(groups => {
      this.groups = groups;
    });

    this.chatService.refreshGroups();
    this.chatService.startGroupsPolling();

    // Auto-open group from notification action
    this.route.queryParamMap.subscribe(params => {
      const groupId = params.get('group');
      if (groupId) {
        const id = parseInt(groupId, 10);
        const found = this.groups.find(g => g.id === id);
        if (found) {
          this.selectGroup(found);
        } else {
          // groups may not be loaded yet; store pending and open after refresh
          this.chatService.refreshGroups();
          const sub = this.chatService.groups$.subscribe(groups => {
            const g = groups.find(gr => gr.id === id);
            if (g) { this.selectGroup(g); sub.unsubscribe(); }
          });
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.groupsSub?.unsubscribe();
    this.chatService.stopPolling();
    this.chatService.stopGroupsPolling();
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
        this.userScrolledUp = false;
      }
    } catch {}
  }

  /** Called from (scroll) event on the messages container */
  onMessagesScroll(event: Event): void {
    const el = event.target as HTMLElement;
    // If more than 100px from bottom, consider the user scrolled up
    this.userScrolledUp = (el.scrollHeight - el.scrollTop - el.clientHeight) > 100;
  }

  get filteredGroups(): ChatGroupDto[] {
    const base = this.groupSearch.trim()
      ? this.groups.filter(g =>
          g.name.toLowerCase().includes(this.groupSearch.toLowerCase()) ||
          (g.privatePartnerUsername || '').toLowerCase().includes(this.groupSearch.toLowerCase())
        )
      : this.groups;
    // Keep server-side order (already sorted by lastMessageTime desc)
    return base;
  }

  selectGroup(group: ChatGroupDto): void {
    if (this.selectedGroup?.id === group.id) return;
    this.selectedGroup = group;
    this.messages = [];
    this.sidebarVisible = false;
    this.userScrolledUp = false;
    this.replyTo = null;
    this.searchMode = false;
    this.searchQuery = '';
    this.searchResults = null;
    this.chatService.stopPolling();

    // Mark group as read immediately when selected
    this.chatService.markGroupRead(group.id);

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
      const lastKnown = this.messages.length > 0 ? this.messages[this.messages.length - 1].id : -1;
      const lastNew   = msgs.length > 0 ? msgs[msgs.length - 1].id : -1;
      if (lastNew !== lastKnown || msgs.length !== this.messages.length) {
        this.messages = msgs;
        // Only auto-scroll to bottom if user hasn't scrolled up to read older messages
        if (!this.userScrolledUp) {
          this.shouldScrollToBottom = true;
        }
      }
    });
  }

  sendMessage(): void {
    if (!this.messageInput.trim() || !this.selectedGroup) return;

    const content = this.messageInput.trim();
    this.messageInput = '';
    const replyId = this.replyTo?.id;
    this.replyTo = null;

    this.chatService.sendMessage(this.selectedGroup.id, content, replyId).subscribe({
      next: msg => {
        this.messages = [...this.messages, msg];
        this.shouldScrollToBottom = true;

        // Update last message in group list and re-sort
        const g = this.groups.find(gr => gr.id === this.selectedGroup!.id);
        if (g) {
          g.lastMessage = this.currentUsername + ': ' + content.substring(0, 50);
          g.lastMessageTime = msg.sentAt;
          // Move this group to the top
          this.groups = [g, ...this.groups.filter(gr => gr.id !== g.id)];
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
    // Close emoji picker
    if (this.emojiPickerOpen) {
      const picker = this.emojiPickerRef?.nativeElement;
      const btn = this.emojiBtnRef?.nativeElement;
      if (picker && btn &&
          !picker.contains(event.target as Node) &&
          !btn.contains(event.target as Node)) {
        this.emojiPickerOpen = false;
      }
    }
    // Close chat options panel
    if (this.showChatOptions) {
      const panel = this.chatOptionsPanelRef?.nativeElement;
      const btn = this.chatOptionsBtnRef?.nativeElement;
      if (panel && btn &&
          !panel.contains(event.target as Node) &&
          !btn.contains(event.target as Node)) {
        this.showChatOptions = false;
      }
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

  // ── Group Info Modal ───────────────────────────────────────────────────────

  openGroupInfo(): void {
    if (this.selectedGroup && this.selectedGroup.type !== 'PRIVATE') {
      this.showGroupInfoModal = true;
    }
  }

  onGroupInfoUpdated(): void {
    // Refresh group string info like name or avatar
    this.chatService.refreshGroups();
    
    // If the selected group was left by the current user, it won't be returned by refreshGroups.
    // However, refreshGroups takes time to propagate, so we must proactively check
    // if the user left or were deleted. Let's just fetch groups manually and verify.
    this.chatService.getGroups().subscribe({
      next: (groups) => {
        if (!groups.find(g => g.id === this.selectedGroup?.id)) {
           this.selectedGroup = null;
           this.messages = [];
           this.sidebarVisible = true;
           this.showGroupInfoModal = false;
        } else {
           // Update loaded selectedGroup reference so the UI reflects new name/avatar/description immediately
           this.selectedGroup = groups.find(g => g.id === this.selectedGroup?.id) || null;
        }
      }
    });
  }

  // ── Reply ──────────────────────────────────────────────────────────────────

  setReply(msg: ChatMessageDto): void {
    this.replyTo = msg;
    // When the reply preview appears the input area grows, shrinking the messages area.
    // Scroll to bottom so the latest message stays visible instead of scrolling off.
    this.shouldScrollToBottom = true;
    this.messageTextarea?.nativeElement.focus();
  }

  cancelReply(): void {
    this.replyTo = null;
  }

  replyPreview(msg: ChatMessageDto): string {
    if (msg.messageType === 'YOUTUBE_SHARE') return '🎬 ' + (msg.videoTitle || 'YouTube');
    return msg.content.length > 80 ? msg.content.slice(0, 80) + '…' : msg.content;
  }

  // ── Search ─────────────────────────────────────────────────────────────────

  toggleSearch(): void {
    this.searchMode = !this.searchMode;
    if (!this.searchMode) {
      this.searchQuery = '';
      this.searchResults = null;
    }
  }

  runSearch(): void {
    if (!this.searchQuery.trim() || !this.selectedGroup) return;
    const q = this.searchQuery.toLowerCase();
    this.searchResults = this.messages.filter(m =>
      m.content.toLowerCase().includes(q) ||
      m.senderUsername.toLowerCase().includes(q) ||
      (m.videoTitle || '').toLowerCase().includes(q)
    );
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.searchResults = null;
  }

  get displayMessages(): ChatMessageDto[] {
    return this.searchResults !== null ? this.searchResults : this.messages;
  }

  // ── Copy message ───────────────────────────────────────────────────────────

  copyMessage(msg: ChatMessageDto): void {
    const text = msg.messageType === 'YOUTUBE_SHARE'
      ? `${msg.videoTitle || ''} — https://www.youtube.com/watch?v=${msg.videoId}`
      : msg.content;

    navigator.clipboard.writeText(text).then(() => {
      this.notificationService.showToast(
        'success',
        this.translate.instant('CHAT.COPIED'),
        '',
        2000
      );
    }).catch(() => {});
  }

  // ── Theme methods ─────────────────────────────────────────────────────────

  applyTheme(themeId: string): void {
    this.currentTheme = themeId;
    localStorage.setItem('chat_theme', themeId);
    this.showThemePicker = false;
  }

  toggleThemePicker(): void {
    this.showThemePicker = !this.showThemePicker;
    if (this.showThemePicker) this.showMutePicker = false;
  }

  // ── Presence helpers ─────────────────────────────────────────────────────

  /** Returns the online status line shown below the group name in the header. */
  getPresenceSubtitle(group: ChatGroupDto): string {
    if (group.type === 'PRIVATE') {
      if (group.partnerOnline) return 'En línea';
      if (group.partnerLastSeen) return 'Visto ' + this.formatLastSeen(group.partnerLastSeen);
      return 'Desconectado';
    }
    // GROUP or ANNOUNCEMENT
    const total = group.memberCount;
    const online = group.onlineCount ?? 0;
    if (online > 0) {
      return `${total} miembros · ${online} en línea`;
    }
    return `${total} miembros`;
  }

  /** Human-readable "hace X min" from an ISO date string. */
  formatLastSeen(dateStr?: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const diff = Math.floor((Date.now() - date.getTime()) / 1000);
    if (diff < 60) return 'hace un momento';
    if (diff < 3600) return `hace ${Math.floor(diff / 60)} min`;
    if (diff < 86400) return `hace ${Math.floor(diff / 3600)} h`;
    return `el ${date.toLocaleDateString('es-ES', { day: '2-digit', month: 'short' })}`;
  }

  /** Whether to show the green online dot on a group item in the sidebar. */
  isPartnerOnline(group: ChatGroupDto): boolean {
    return group.type === 'PRIVATE' && group.partnerOnline === true;
  }

  // ── Display name and avatar helpers ──────────────────────────────────────

  getGroupDisplayName(group: ChatGroupDto): string {
    if (group.type === 'PRIVATE' && group.privatePartnerUsername) {
      return group.privatePartnerUsername;
    }
    return group.name;
  }

  getGroupAvatarUrl(group: ChatGroupDto): string | null {
    if (group.type === 'PRIVATE' && group.privatePartnerAvatarUrl) {
      return this.resolveAvatarUrl(group.privatePartnerAvatarUrl);
    }
    // Show the group's own image if it has one (e.g. for GROUP type chats)
    if (group.imageFilename) {
      return this.resolveAvatarUrl(`/api/user/avatar/img/${group.imageFilename}`);
    }
    return null;
  }

  // ── Mute helpers ─────────────────────────────────────────────────────────

  isGroupMuted(group: ChatGroupDto): boolean {
    return this.chatService.isGroupMuted(group.id);
  }

  getMuteLabel(group: ChatGroupDto): string {
    return this.chatService.getMuteLabel(group.id);
  }

  muteGroup(durationMs: number | 'forever'): void {
    if (!this.selectedGroup) return;
    this.chatService.muteGroup(this.selectedGroup.id, durationMs);
    this.showMutePicker = false;
  }

  unmuteGroup(): void {
    if (!this.selectedGroup) return;
    this.chatService.unmuteGroup(this.selectedGroup.id);
    this.showMutePicker = false;
  }

  toggleMutePicker(): void {
    this.showMutePicker = !this.showMutePicker;
    if (this.showMutePicker) this.showThemePicker = false;
  }

  trackByMessageId(index: number, msg: ChatMessageDto): number {
    return msg.id;
  }

  trackByGroupId(index: number, group: ChatGroupDto): number {
    return group.id;
  }

  isYoutubeShare(msg: ChatMessageDto): boolean {
    return msg.messageType === 'YOUTUBE_SHARE';
  }

  downloadFromChat(videoId: string, type: 'video' | 'music'): void {
    this.router.navigate(['/youtube-downloads'], { queryParams: { v: videoId, type } });
  }

  openInYoutube(videoId: string): void {
    window.open(`https://www.youtube.com/watch?v=${videoId}`, '_blank');
  }

  // ── Chat options (clear / delete) ─────────────────────────────────────────

  toggleChatOptions(): void {
    this.showChatOptions = !this.showChatOptions;
    if (this.showChatOptions) {
      this.showMutePicker = false;
      this.showThemePicker = false;
    }
  }

  canManageChat(): boolean {
    if (!this.selectedGroup) return false;
    if (this.selectedGroup.type === 'ANNOUNCEMENT') return false;
    if (this.selectedGroup.type === 'PRIVATE') return true;
    return this.selectedGroup.currentUserRole === 'ADMIN' || this.selectedGroup.createdByUsername === this.currentUsername;
  }

  canDeleteChat(): boolean {
    if (!this.selectedGroup) return false;
    if (this.selectedGroup.type === 'PRIVATE') return true;
    return this.selectedGroup.createdByUsername === this.currentUsername;
  }

  confirmClear(): void {
    this.showChatOptions = false;
    this.showClearConfirm = true;
  }

  confirmDelete(): void {
    this.showChatOptions = false;
    this.showDeleteConfirm = true;
  }

  executeClear(): void {
    if (!this.selectedGroup) return;
    this.showClearConfirm = false;
    this.chatService.clearMessages(this.selectedGroup.id).subscribe({
      next: () => {
        this.messages = [];
        this.chatService.refreshGroups();
        this.notificationService.showToast('success', this.translate.instant('CHAT.CLEAR_SUCCESS'), '');
      },
      error: () => this.notificationService.showToast('error', this.translate.instant('CHAT.ERROR'), this.translate.instant('CHAT.CLEAR_ERROR'))
    });
  }

  executeDelete(): void {
    if (!this.selectedGroup) return;
    const groupName = this.getGroupDisplayName(this.selectedGroup);
    this.showDeleteConfirm = false;
    this.chatService.deleteGroup(this.selectedGroup.id).subscribe({
      next: () => {
        this.selectedGroup = null;
        this.messages = [];
        this.sidebarVisible = true;
        this.chatService.stopPolling();
        this.chatService.refreshGroups();
        this.notificationService.showToast('success', this.translate.instant('CHAT.DELETE_SUCCESS'), groupName);
      },
      error: () => this.notificationService.showToast('error', this.translate.instant('CHAT.ERROR'), this.translate.instant('CHAT.DELETE_ERROR'))
    });
  }

  shareYoutubeCardToCurrentGroup(msg: ChatMessageDto): void {
    if (!this.selectedGroup || !msg.videoId) return;
    const payload: YoutubeSharePayload = {
      videoId: msg.videoId,
      videoTitle: msg.videoTitle || msg.videoId,
      thumbnailUrl: msg.thumbnailUrl || `https://img.youtube.com/vi/${msg.videoId}/hqdefault.jpg`,
      channelTitle: msg.channelTitle || ''
    };
    this.chatService.sendYoutubeShare(this.selectedGroup.id, payload).subscribe({
      next: newMsg => {
        this.messages = [...this.messages, newMsg];
        this.shouldScrollToBottom = true;
      },
      error: () => {}
    });
  }
}