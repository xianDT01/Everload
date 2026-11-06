import { ChatComponent } from './chat.component';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { convertToParamMap } from '@angular/router';
import { ChatGroupDto, ChatService } from '../../services/chat.service';
import { AuthService } from '../../services/auth.service';
import { TranslateService } from '@ngx-translate/core';

describe('ChatComponent conversations', () => {
  let chat: ChatComponent;
  let service: any;
  let groups: BehaviorSubject<ChatGroupDto[]>;
  const online = { id: 1, name: 'Private', type: 'PRIVATE', privatePartnerUsername: 'Adrian', partnerOnline: true } as ChatGroupDto;
  const offline = { id: 2, name: 'Private', type: 'PRIVATE', privatePartnerUsername: 'Lucia', partnerOnline: false } as ChatGroupDto;
  const group = { id: 3, name: 'Music', type: 'GROUP', onlineCount: 2 } as ChatGroupDto;

  beforeEach(() => {
    groups = new BehaviorSubject([online, offline, group]);
    service = jasmine.createSpyObj('ChatService', ['refreshGroups', 'startGroupsPolling', 'stopGroupsPolling', 'stopPolling',
      'markGroupRead', 'markRead', 'getMessages', 'getReadStatus', 'startPolling', 'isGroupUnread', 'getMembers']);
    service.groups$ = groups.asObservable();
    service.markRead.and.returnValue(of({})); service.getMessages.and.returnValue(of([]));
    service.getReadStatus.and.returnValue(of({})); service.getMembers.and.returnValue(of([]));
    service.isGroupUnread.and.callFake((g: ChatGroupDto) => g.id === 2);
    chat = new ChatComponent(service as ChatService, {} as any,
      { getCurrentUser: () => ({ username: 'Demo', role: 'NAS_USER' }) } as AuthService,
      {} as any, { queryParamMap: of(convertToParamMap({})) } as any,
      { instant: (key: string) => key, currentLang: 'gl' } as TranslateService,
      { mainPlayer: { state: { currentTrack: null } } } as any, { style$: new BehaviorSubject(null) } as any);
    chat.ngOnInit();
  });
  afterEach(() => chat.ngOnDestroy());

  it('combines search with online and unread filters without changing server order', () => {
    expect(chat.filteredGroups.map(g => g.id)).toEqual([1, 2, 3]);
    chat.conversationFilter = 'online';
    expect(chat.filteredGroups.map(g => g.id)).toEqual([1, 3]);
    chat.groupSearch = 'Adrian';
    expect(chat.filteredGroups.map(g => g.id)).toEqual([1]);
    chat.groupSearch = ''; chat.conversationFilter = 'unread';
    expect(chat.filteredGroups.map(g => g.id)).toEqual([2]);
  });

  it('updates selected contact presence when the groups poll refreshes', () => {
    chat.selectGroup(online);
    groups.next([{ ...online, partnerOnline: false }, offline]);
    expect(chat.selectedGroup?.partnerOnline).toBeFalse();
    expect(chat.getPresenceSubtitle(chat.selectedGroup!)).toBe('CHAT.OFFLINE');
  });

  it('reopens the same mobile conversation and resumes its polling', () => {
    chat.selectGroup(online);
    chat.backToSidebar();
    expect(chat.sidebarVisible).toBeTrue();
    chat.selectGroup(online);
    expect(chat.sidebarVisible).toBeFalse();
    expect(service.startPolling).toHaveBeenCalledTimes(2);
  });

  it('keeps the XP embedding separate from the standalone redesign', () => {
    expect(chat.embedded).toBeFalse();
    chat.embedded = true;
    expect(chat.filteredGroups.length).toBe(3);
    expect(chat.getDateLabel(new Date().toISOString())).toBe('CHAT.TODAY');
  });

  it('closes standalone dialogs and popup menus with Escape', () => {
    chat.showPrivateChatModal = chat.showClearConfirm = chat.showDeleteConfirm = true;
    chat.showChatOptions = chat.showMutePicker = true;
    chat.closeChatDialogs();
    expect(chat.showPrivateChatModal || chat.showClearConfirm || chat.showDeleteConfirm || chat.showChatOptions || chat.showMutePicker).toBeFalse();
  });

  it('cancels a previous message load when changing conversations', () => {
    const delayed = new Subject<any[]>();
    service.getMessages.and.returnValue(delayed);
    chat.selectGroup(online);
    expect(delayed.observed).toBeTrue();
    service.getMessages.and.returnValue(of([]));
    chat.selectGroup(offline);
    expect(delayed.observed).toBeFalse();
    delayed.next([{ id: 99, content: 'Previous chat' }]);
    expect(chat.messages).toEqual([]);
  });
});
