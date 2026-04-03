import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';

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
}

export interface ChatMessageDto {
  id: number;
  groupId: number;
  senderUsername: string;
  senderAvatarUrl?: string;
  content: string;
  messageType: 'TEXT' | 'YOUTUBE_SHARE';
  videoId?: string;
  videoTitle?: string;
  thumbnailUrl?: string;
  channelTitle?: string;
  sentAt: string;
  edited: boolean;
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
}

@Injectable({ providedIn: 'root' })
export class ChatService implements OnDestroy {

  readonly BASE: string = (() => {
    const host = typeof window !== 'undefined' ? window.location.hostname : '';
    return (host === 'localhost' || host === '127.0.0.1') ? 'http://localhost:8080' : '';
  })();

  private groupsSubject = new BehaviorSubject<ChatGroupDto[]>([]);
  groups$ = this.groupsSubject.asObservable();

  private pollIntervalRef: any = null;
  private currentPollGroupId: number | null = null;
  private groupsPollRef: any = null;

  constructor(private http: HttpClient) {}

  ngOnDestroy(): void {
    this.stopPolling();
    this.stopGroupsPolling();
  }

  getGroups(): Observable<ChatGroupDto[]> {
    return this.http.get<ChatGroupDto[]>(`${this.BASE}/api/chat/groups`);
  }

  refreshGroups(): void {
    this.getGroups().subscribe({
      next: groups => this.groupsSubject.next(groups),
      error: () => {}
    });
  }

  createGroup(req: CreateGroupRequest): Observable<ChatGroupDto> {
    return this.http.post<ChatGroupDto>(`${this.BASE}/api/chat/groups`, req);
  }

  getMessages(groupId: number): Observable<ChatMessageDto[]> {
    return this.http.get<ChatMessageDto[]>(`${this.BASE}/api/chat/groups/${groupId}/messages`);
  }

  sendMessage(groupId: number, content: string): Observable<ChatMessageDto> {
    return this.http.post<ChatMessageDto>(`${this.BASE}/api/chat/groups/${groupId}/messages`, { content });
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

  getMembers(groupId: number): Observable<MemberDto[]> {
    return this.http.get<MemberDto[]>(`${this.BASE}/api/chat/groups/${groupId}/members`);
  }

  startPrivateChat(username: string): Observable<ChatGroupDto> {
    return this.http.post<ChatGroupDto>(`${this.BASE}/api/chat/private/${username}`, {});
  }

  getActiveUsers(): Observable<ActiveUser[]> {
    return this.http.get<ActiveUser[]>(`${this.BASE}/api/chat/users`);
  }

  startPolling(groupId: number, callback: (messages: ChatMessageDto[]) => void): void {
    this.stopPolling();
    this.currentPollGroupId = groupId;

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
}