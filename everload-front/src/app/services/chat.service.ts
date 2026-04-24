import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, Subject } from 'rxjs';

export interface ChatGroupDto {
  id: number;
  name: string;
  description?: string;
  type: 'PRIVATE' | 'GROUP' | 'ANNOUNCEMENT';
  createdAt: string;
  memberCount: number;
  lastMessage?: string;
  lastMessageTime?: string;
  imageFilename?: string;
  createdByUsername?: string;
  lastSenderAvatarUrl?: string;
  privatePartnerUsername?: string;
  privatePartnerAvatarUrl?: string;
  /** Presence: for PRIVATE chats */
  partnerOnline?: boolean;
  partnerLastSeen?: string;
  /** Presence: for GROUP/ANNOUNCEMENT */
  onlineCount?: number;
  /** Role of the current user in this group (ADMIN, MEMBER, READONLY) */
  currentUserRole?: string;
}

export interface ChatMessageDto {
  id: number;
  groupId: number;
  senderUsername: string;
  senderAvatarUrl?: string;
  content: string;
  messageType: 'TEXT' | 'YOUTUBE_SHARE' | 'BUZZ';
  videoId?: string;
  videoTitle?: string;
  thumbnailUrl?: string;
  channelTitle?: string;
  sentAt: string;
  edited: boolean;
  replyToId?: number;
  replyToContent?: string;
  replyToSender?: string;
}

export interface YoutubeSharePayload {
  videoId: string;
  videoTitle: string;
  thumbnailUrl: string;
  channelTitle: string;
}

export interface MemberDto {
  username: string;
  role: string;
  avatarUrl?: string;
  joinedAt: string;
}

export interface CreateGroupRequest {
  name: string;
  description?: string;
  type: string;
  memberUsernames: string[];
}

export interface ActiveUser {
  username: string;
  avatarUrl?: string;
  online?: boolean;
  lastSeen?: string;
}

export interface NewMessageAlert {
  groupId: number;
  groupName: string;
  senderName: string;
  content: string;
  isPrivate: boolean;
  senderAvatarUrl?: string;
}

@Injectable({ providedIn: 'root' })
export class ChatService implements OnDestroy {

  readonly BASE: string = (() => {
    const host = typeof window !== 'undefined' ? window.location.hostname : '';
    const port = typeof window !== 'undefined' ? window.location.port : '';
    return (host === 'localhost' || host === '127.0.0.1') && port === '4200'
      ? 'http://localhost:8080'
      : '';
  })();

  private groupsSubject = new BehaviorSubject<ChatGroupDto[]>([]);
  groups$ = this.groupsSubject.asObservable();

  // ── Unread notifications ──────────────────────────────────────────────────
  private unreadCountSubject = new BehaviorSubject<number>(0);
  unreadCount$ = this.unreadCountSubject.asObservable();

  private newMessageAlertSubject = new Subject<NewMessageAlert>();
  newMessageAlert$ = this.newMessageAlertSubject.asObservable();

  private lastKnownGroupTimes = new Map<number, string>(); // groupId → lastMessageTime ISO
  private globalPollInitialized = false;

  // ── Polling refs ──────────────────────────────────────────────────────────
  private pollIntervalRef: any = null;
  currentPollGroupId: number | null = null;
  private groupsPollRef: any = null;
  private globalPollRef: any = null;

  // ── Visibility API — pause polling when tab is hidden ─────────────────────
  // Tracks which pollings were active before hiding so we can resume them.
  private pausedState = { messages: false, groups: false, global: false };
  private pausedMessageCallback: ((msgs: any[]) => void) | null = null;
  private readonly visibilityHandler = () => this.onVisibilityChange();

  constructor(private http: HttpClient) {
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', this.visibilityHandler);
    }
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.stopGroupsPolling();
    this.stopGlobalPolling();
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', this.visibilityHandler);
    }
  }

  private onVisibilityChange(): void {
    if (document.hidden) {
      // Pause: record what was running, then clear intervals
      this.pausedState.messages = !!this.pollIntervalRef;
      this.pausedState.groups   = !!this.groupsPollRef;
      this.pausedState.global   = !!this.globalPollRef;

      if (this.pollIntervalRef)  { clearInterval(this.pollIntervalRef);  this.pollIntervalRef  = null; }
      if (this.groupsPollRef)    { clearInterval(this.groupsPollRef);    this.groupsPollRef    = null; }
      if (this.globalPollRef)    { clearInterval(this.globalPollRef);    this.globalPollRef    = null; }
    } else {
      // Resume: restart whatever was running + do an immediate fetch to catch up
      if (this.pausedState.global) {
        this.refreshGroups(); // immediate catch-up
        this.globalPollRef = setInterval(() => this.refreshGroups(), 5000);
      }
      if (this.pausedState.groups) {
        this.refreshGroups();
        this.groupsPollRef = setInterval(() => this.refreshGroups(), 5000);
      }
      if (this.pausedState.messages && this.currentPollGroupId !== null && this.pausedMessageCallback) {
        const cb = this.pausedMessageCallback;
        const gid = this.currentPollGroupId;
        this.getMessages(gid).subscribe({ next: msgs => cb(msgs), error: () => {} });
        this.pollIntervalRef = setInterval(() => {
          if (this.currentPollGroupId !== null) {
            this.getMessages(this.currentPollGroupId).subscribe({ next: msgs => cb(msgs), error: () => {} });
          }
        }, 2000);
      }
      this.pausedState = { messages: false, groups: false, global: false };
    }
  }

  // ── API calls ─────────────────────────────────────────────────────────────

  getGroups(): Observable<ChatGroupDto[]> {
    return this.http.get<ChatGroupDto[]>(`${this.BASE}/api/chat/groups`);
  }

  createGroup(req: CreateGroupRequest): Observable<ChatGroupDto> {
    return this.http.post<ChatGroupDto>(`${this.BASE}/api/chat/groups`, req);
  }

  getMessages(groupId: number): Observable<ChatMessageDto[]> {
    return this.http.get<ChatMessageDto[]>(`${this.BASE}/api/chat/groups/${groupId}/messages`);
  }

  sendMessage(groupId: number, content: string, replyToMessageId?: number): Observable<ChatMessageDto> {
    const body: any = { content };
    if (replyToMessageId !== undefined) body.replyToMessageId = replyToMessageId;
    return this.http.post<ChatMessageDto>(`${this.BASE}/api/chat/groups/${groupId}/messages`, body);
  }

  sendYoutubeShare(groupId: number, payload: YoutubeSharePayload): Observable<ChatMessageDto> {
    return this.http.post<ChatMessageDto>(`${this.BASE}/api/chat/groups/${groupId}/messages`, {
      content: '',
      messageType: 'YOUTUBE_SHARE',
      videoId: payload.videoId,
      videoTitle: payload.videoTitle,
      thumbnailUrl: payload.thumbnailUrl,
      channelTitle: payload.channelTitle
    });
  }

  sendBuzz(groupId: number): Observable<ChatMessageDto> {
    return this.http.post<ChatMessageDto>(`${this.BASE}/api/chat/groups/${groupId}/messages`, {
      content: 'Zumbido',
      messageType: 'BUZZ'
    });
  }

  getMembers(groupId: number): Observable<MemberDto[]> {
    return this.http.get<MemberDto[]>(`${this.BASE}/api/chat/groups/${groupId}/members`);
  }

  startPrivateChat(username: string): Observable<ChatGroupDto> {
    return this.http.post<ChatGroupDto>(`${this.BASE}/api/chat/private/${username}`, {});
  }

  getActiveUsers(): Observable<ActiveUser[]> {
    return this.http.get<ActiveUser[]>(`${this.BASE}/api/chat/users`);
  }

  searchMessages(groupId: number, query: string): Observable<ChatMessageDto[]> {
    return this.http.get<ChatMessageDto[]>(`${this.BASE}/api/chat/groups/${groupId}/messages/search`, {
      params: { q: query }
    });
  }

  clearMessages(groupId: number): Observable<void> {
    return this.http.delete<void>(`${this.BASE}/api/chat/groups/${groupId}/messages`);
  }

  markRead(groupId: number): Observable<void> {
    return this.http.post<void>(`${this.BASE}/api/chat/groups/${groupId}/mark-read`, {});
  }

  getReadStatus(groupId: number): Observable<Record<string, string>> {
    return this.http.get<Record<string, string>>(`${this.BASE}/api/chat/groups/${groupId}/read-status`);
  }

  deleteGroup(groupId: number): Observable<void> {
    return this.http.delete<void>(`${this.BASE}/api/chat/groups/${groupId}`);
  }

  // ── Group Administration ──────────────────────────────────────────────────

  updateGroupInfo(groupId: number, name: string, description: string): Observable<ChatGroupDto> {
    return this.http.put<ChatGroupDto>(`${this.BASE}/api/chat/groups/${groupId}/info`, { name, description });
  }

  updateGroupAvatar(groupId: number, file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post(`${this.BASE}/api/chat/groups/${groupId}/avatar`, formData);
  }

  updateMemberRole(groupId: number, username: string, role: string): Observable<void> {
    return this.http.put<void>(`${this.BASE}/api/chat/groups/${groupId}/members/${username}/role`, { role });
  }

  kickMember(groupId: number, username: string): Observable<void> {
    return this.http.delete<void>(`${this.BASE}/api/chat/groups/${groupId}/members/${username}`);
  }

  addMember(groupId: number, username: string): Observable<void> {
    return this.http.post<void>(`${this.BASE}/api/chat/groups/${groupId}/members`, { username });
  }

  leaveGroup(groupId: number): Observable<void> {
    return this.http.post<void>(`${this.BASE}/api/chat/groups/${groupId}/leave`, {});
  }

  // ── Groups refresh ────────────────────────────────────────────────────────

  refreshGroups(): void {
    this.getGroups().subscribe({
      next: groups => {
        this.groupsSubject.next(groups);
        this.computeUnreadAndNotify(groups);
      },
      error: () => {}
    });
  }

  // ── Unread count / notifications ──────────────────────────────────────────

  private computeUnreadAndNotify(groups: ChatGroupDto[]): void {
    let unread = 0;

    groups.forEach(g => {
      if (!g.lastMessageTime) return;

      const lastMsgTime = g.lastMessageTime; // ISO-8601 string

      // Auto-mark current active group as read (user is looking at it)
      if (g.id === this.currentPollGroupId) {
        localStorage.setItem(`chat_seen_${g.id}`, lastMsgTime);
      }

      const lastSeen = localStorage.getItem(`chat_seen_${g.id}`);
      const isUnread = !lastSeen || lastMsgTime > lastSeen;
      if (isUnread) unread++;

      // Detect new message since last poll (only for non-active groups)
      if (this.globalPollInitialized && g.id !== this.currentPollGroupId) {
        const prevTime = this.lastKnownGroupTimes.get(g.id);
        if (prevTime && lastMsgTime > prevTime) {
          const lastMsg    = g.lastMessage || '';
          const colonIdx   = lastMsg.indexOf(': ');
          const senderName = colonIdx >= 0 ? lastMsg.slice(0, colonIdx) : '';
          const content    = colonIdx >= 0 ? lastMsg.slice(colonIdx + 2) : lastMsg;
          // Don't show toast notification for muted groups
          if (!this.isGroupMuted(g.id)) {
            this.newMessageAlertSubject.next({
              groupId: g.id,
              groupName: g.name,
              senderName,
              content,
              isPrivate: g.type === 'PRIVATE',
              senderAvatarUrl: g.lastSenderAvatarUrl || undefined
            });
          }
        }
      }

      this.lastKnownGroupTimes.set(g.id, lastMsgTime);
    });

    this.globalPollInitialized = true;
    this.unreadCountSubject.next(unread);
  }

  /** Mark a group as fully read (call when user selects/opens a group) */
  markGroupRead(groupId: number): void {
    const group = this.groupsSubject.value.find(g => g.id === groupId);
    if (group?.lastMessageTime) {
      localStorage.setItem(`chat_seen_${groupId}`, group.lastMessageTime);
      this.computeUnreadAndNotify(this.groupsSubject.value);
    }
  }

  /** Reset notification tracking state (call on logout) */
  resetNotifications(): void {
    this.globalPollInitialized = false;
    this.lastKnownGroupTimes.clear();
    this.unreadCountSubject.next(0);
  }

  // ── Polling: messages in active group ────────────────────────────────────

  startPolling(groupId: number, callback: (messages: ChatMessageDto[]) => void): void {
    this.stopPolling();
    this.currentPollGroupId = groupId;
    this.pausedMessageCallback = callback; // kept for Visibility API resume

    this.pollIntervalRef = setInterval(() => {
      if (this.currentPollGroupId !== null) {
        this.getMessages(this.currentPollGroupId).subscribe({
          next: msgs => callback(msgs),
          error: () => {}
        });
      }
    }, 2000);
  }

  stopPolling(): void {
    if (this.pollIntervalRef) {
      clearInterval(this.pollIntervalRef);
      this.pollIntervalRef = null;
    }
    this.currentPollGroupId = null;
  }

  // ── Polling: group list (used by ChatComponent, 5s) ──────────────────────

  startGroupsPolling(): void {
    this.stopGroupsPolling();
    this.groupsPollRef = setInterval(() => {
      this.refreshGroups();
    }, 5000);
  }

  stopGroupsPolling(): void {
    if (this.groupsPollRef) {
      clearInterval(this.groupsPollRef);
      this.groupsPollRef = null;
    }
  }

  // ── Polling: global background (30s, started from AppComponent) ──────────

  startGlobalPolling(): void {
    if (this.globalPollRef) return; // already running
    this.refreshGroups();           // immediate first fetch
    this.globalPollRef = setInterval(() => {
      this.refreshGroups();
    }, 5000);  // 5s — same cadence as in-chat group poll
  }

  stopGlobalPolling(): void {
    if (this.globalPollRef) {
      clearInterval(this.globalPollRef);
      this.globalPollRef = null;
    }
    this.resetNotifications();
  }

  // ── Mute ─────────────────────────────────────────────────────────────────

  muteGroup(groupId: number, durationMs: number | 'forever'): void {
    const value = durationMs === 'forever'
      ? 'forever'
      : new Date(Date.now() + durationMs).toISOString();
    localStorage.setItem(`chat_muted_${groupId}`, value);
  }

  unmuteGroup(groupId: number): void {
    localStorage.removeItem(`chat_muted_${groupId}`);
  }

  isGroupUnread(group: ChatGroupDto): boolean {
    if (!group.lastMessageTime) return false;
    const lastSeen = localStorage.getItem(`chat_seen_${group.id}`);
    return !lastSeen || group.lastMessageTime > lastSeen;
  }

  isGroupMuted(groupId: number): boolean {
    const val = localStorage.getItem(`chat_muted_${groupId}`);
    if (!val) return false;
    if (val === 'forever') return true;
    const expiry = new Date(val);
    if (expiry <= new Date()) {
      localStorage.removeItem(`chat_muted_${groupId}`);
      return false;
    }
    return true;
  }

  getMuteLabel(groupId: number): string {
    const val = localStorage.getItem(`chat_muted_${groupId}`);
    if (!val) return '';
    if (val === 'forever') return '∞';
    const expiry = new Date(val);
    if (expiry <= new Date()) return '';
    const diff = expiry.getTime() - Date.now();
    const h = Math.ceil(diff / 3600000);
    return h >= 24 ? `${Math.ceil(h/24)}d` : `${h}h`;
  }
}
