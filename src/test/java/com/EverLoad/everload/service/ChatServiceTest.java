package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.ChatMessageDto;
import com.EverLoad.everload.dto.SendMessageRequest;
import com.EverLoad.everload.model.ChatGroup;
import com.EverLoad.everload.model.ChatGroupRead;
import com.EverLoad.everload.model.ChatMessage;
import com.EverLoad.everload.model.GroupType;
import com.EverLoad.everload.model.GroupMember;
import com.EverLoad.everload.model.MemberRole;
import com.EverLoad.everload.model.MessageType;
import com.EverLoad.everload.model.Role;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.model.UserStatus;
import com.EverLoad.everload.repository.ChatGroupReadRepository;
import com.EverLoad.everload.repository.ChatGroupRepository;
import com.EverLoad.everload.repository.ChatMessageRepository;
import com.EverLoad.everload.repository.GroupMemberRepository;
import com.EverLoad.everload.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private ChatGroupRepository groupRepository;
    private GroupMemberRepository memberRepository;
    private ChatMessageRepository messageRepository;
    private ChatGroupReadRepository readRepository;
    private UserRepository userRepository;
    private PresenceService presenceService;
    private ChatService service;

    @BeforeEach
    void setUp() {
        groupRepository = mock(ChatGroupRepository.class);
        memberRepository = mock(GroupMemberRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        readRepository = mock(ChatGroupReadRepository.class);
        userRepository = mock(UserRepository.class);
        presenceService = mock(PresenceService.class);
        service = new ChatService(
                groupRepository,
                memberRepository,
                messageRepository,
                userRepository,
                presenceService,
                mock(AvatarService.class),
                mock(NotificationService.class),
                readRepository
        );
    }

    @Test
    void markReadCreatesRecordWithCurrentTimestamp() {
        User user = user(1L, "xian");
        ChatGroup group = group(2L);
        when(groupRepository.findById(2L)).thenReturn(Optional.of(group));
        when(memberRepository.existsByGroupAndUser(group, user)).thenReturn(true);
        when(readRepository.findByUserAndGroup(user, group)).thenReturn(Optional.empty());

        service.markRead(2L, user);

        ArgumentCaptor<ChatGroupRead> captor = ArgumentCaptor.forClass(ChatGroupRead.class);
        verify(readRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals(group, captor.getValue().getGroup());
        assertNotNull(captor.getValue().getLastReadAt());
    }

    @Test
    void getReadStatusFormatsEveryMemberTimestamp() {
        User requester = user(1L, "requester");
        User member = user(3L, "member");
        ChatGroup group = group(2L);
        LocalDateTime readAt = LocalDateTime.of(2026, Month.JULY, 12, 20, 30);
        when(groupRepository.findById(2L)).thenReturn(Optional.of(group));
        when(memberRepository.existsByGroupAndUser(group, requester)).thenReturn(true);
        when(readRepository.findByGroup(group)).thenReturn(List.of(
                ChatGroupRead.builder().user(member).group(group).lastReadAt(readAt).build()
        ));

        Map<String, String> result = service.getReadStatus(2L, requester);

        assertEquals(readAt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                result.get("member"));
    }

    @Test
    void getMessagesBuildsYoutubeBuzzAndReplyPreviews() {
        User user = user(1L, "xian");
        ChatGroup group = group(2L);
        ChatMessage youtube = message(10L, group, user, MessageType.YOUTUBE_SHARE, null, "Demo video", null);
        ChatMessage buzz = message(11L, group, user, MessageType.BUZZ, "", null, youtube);
        ChatMessage text = message(12L, group, user, MessageType.TEXT, "hello", null, buzz);
        when(groupRepository.findById(2L)).thenReturn(Optional.of(group));
        when(memberRepository.existsByGroupAndUser(group, user)).thenReturn(true);
        when(messageRepository.findTop100ByGroupOrderBySentAtDesc(group))
                .thenReturn(new ArrayList<>(List.of(text, buzz, youtube)));

        List<ChatMessageDto> result = service.getMessages(2L, user);

        assertEquals(3, result.size());
        assertEquals("YOUTUBE_SHARE", result.get(0).getMessageType());
        assertEquals("🎬 Demo video", result.get(1).getReplyToContent());
        assertEquals("Zumbido", result.get(2).getReplyToContent());
    }

    @Test
    void getGroupsMapsPrivatePartnerAndLastMessage() {
        User current = user(1L, "current");
        current.setRole(Role.BASIC_USER);
        User partner = user(2L, "partner");
        partner.setAvatarFilename("partner.jpg");
        partner.setShowLastSeen(true);
        partner.setLastSeen(LocalDateTime.of(2026, Month.JULY, 12, 18, 0));
        ChatGroup group = ChatGroup.builder()
                .id(3L).name("private").type(GroupType.PRIVATE).createdBy(current)
                .createdAt(LocalDateTime.of(2026, Month.JULY, 12, 17, 0)).build();
        List<GroupMember> members = List.of(
                GroupMember.builder().group(group).user(current).role(MemberRole.ADMIN).build(),
                GroupMember.builder().group(group).user(partner).role(MemberRole.MEMBER).build());
        ChatMessage latest = message(4L, group, partner, MessageType.YOUTUBE_SHARE, "", null, null);
        when(groupRepository.findAllByType(GroupType.ANNOUNCEMENT)).thenReturn(List.of());
        when(groupRepository.findGroupsByUserId(1L)).thenReturn(List.of(group));
        when(memberRepository.findByGroup(group)).thenReturn(members);
        when(messageRepository.findTop100ByGroupOrderBySentAtDesc(group)).thenReturn(List.of(latest));
        when(presenceService.isOnline("current")).thenReturn(true);
        when(presenceService.isOnline("partner")).thenReturn(false);

        var dto = service.getGroupsForUser(current).get(0);

        assertEquals("partner", dto.getPrivatePartnerUsername());
        assertEquals("/api/user/avatar/img/partner.jpg", dto.getPrivatePartnerAvatarUrl());
        assertEquals(partner.getLastSeen(), dto.getPartnerLastSeen());
        assertEquals("ADMIN", dto.getCurrentUserRole());
        assertTrue(dto.getLastMessage().contains("Vídeo de YouTube"));
    }

    @Test
    void missingGroupsAndMembersUseStableErrors() {
        User user = user(1L, "xian");
        ChatGroup group = group(2L);
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());
        RuntimeException missingGroup = assertThrows(RuntimeException.class,
                () -> service.getMessages(99L, user));
        assertEquals("Group not found", missingGroup.getMessage());

        when(groupRepository.findById(2L)).thenReturn(Optional.of(group));
        when(memberRepository.findByGroupAndUser(group, user)).thenReturn(Optional.empty());
        RuntimeException missingMember = assertThrows(RuntimeException.class,
                () -> service.clearGroupMessages(2L, user));
        assertEquals("Not a member", missingMember.getMessage());
    }

    @Test
    void everyGroupOperationRejectsAnUnknownGroup() {
        User user = user(1L, "xian");
        SendMessageRequest request = new SendMessageRequest();
        MultipartFile file = mock(MultipartFile.class);
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.sendMessage(99L, request, user));
        assertThrows(RuntimeException.class, () -> service.getGroupMembers(99L, user));
        assertThrows(RuntimeException.class, () -> service.addMember(99L, "target", user));
        assertThrows(RuntimeException.class, () -> service.searchMessages(99L, "hello", user));
        assertThrows(RuntimeException.class, () -> service.updateGroupInfo(99L, "name", "description", user));
        assertThrows(RuntimeException.class, () -> service.updateGroupImage(99L, file, user));
        assertThrows(RuntimeException.class, () -> service.updateMemberRole(99L, "target", "ADMIN", user));
        assertThrows(RuntimeException.class, () -> service.kickMember(99L, "target", user));
        assertThrows(RuntimeException.class, () -> service.leaveGroup(99L, user));
        assertThrows(RuntimeException.class, () -> service.clearGroupMessages(99L, user));
        assertThrows(RuntimeException.class, () -> service.deleteGroupByUser(99L, user));
        assertThrows(RuntimeException.class, () -> service.adminGetMessages(99L));
        assertThrows(RuntimeException.class, () -> service.adminGetGroupMembers(99L));
        assertThrows(RuntimeException.class, () -> service.adminDeleteGroup(99L));
        assertThrows(RuntimeException.class, () -> service.adminRemoveMember(99L, "target"));
    }

    @Test
    void memberProtectedOperationsRejectAUserOutsideTheGroup() {
        User requester = user(1L, "requester");
        User creator = user(2L, "creator");
        ChatGroup group = group(3L);
        group.setCreatedBy(creator);
        SendMessageRequest request = new SendMessageRequest();
        MultipartFile file = mock(MultipartFile.class);
        when(groupRepository.findById(3L)).thenReturn(Optional.of(group));
        when(memberRepository.findByGroupAndUser(group, requester)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.sendMessage(3L, request, requester));
        assertThrows(RuntimeException.class, () -> service.addMember(3L, "target", requester));
        assertThrows(RuntimeException.class, () -> service.updateGroupInfo(3L, "name", "description", requester));
        assertThrows(RuntimeException.class, () -> service.updateGroupImage(3L, file, requester));
        assertThrows(RuntimeException.class, () -> service.kickMember(3L, "target", requester));
        assertThrows(RuntimeException.class, () -> service.leaveGroup(3L, requester));
        assertThrows(RuntimeException.class, () -> service.clearGroupMessages(3L, requester));
        assertThrows(RuntimeException.class, () -> service.deleteGroupByUser(3L, requester));
    }

    @Test
    void memberAndAdminViewsMapRepositoryResults() {
        User requester = user(1L, "requester");
        requester.setAvatarFilename("requester.jpg");
        ChatGroup group = group(3L);
        group.setCreatedBy(requester);
        GroupMember member = GroupMember.builder()
                .group(group).user(requester).role(MemberRole.ADMIN)
                .joinedAt(LocalDateTime.of(2026, Month.JULY, 13, 9, 0)).build();
        ChatMessage text = message(4L, group, requester, MessageType.TEXT, "hello chat", null, null);
        ChatMessage youtube = message(5L, group, requester, MessageType.YOUTUBE_SHARE, "", "Video", null);
        when(groupRepository.findById(3L)).thenReturn(Optional.of(group));
        when(groupRepository.findAll()).thenReturn(List.of(group));
        when(memberRepository.existsByGroupAndUser(group, requester)).thenReturn(true);
        when(memberRepository.findByGroup(group)).thenReturn(List.of(member));
        when(messageRepository.findByGroupAndContentContainingIgnoreCaseOrderBySentAtDesc(group, "hello"))
                .thenReturn(List.of(text));
        when(messageRepository.findByGroupOrderBySentAtAsc(group)).thenReturn(List.of(text));
        when(messageRepository.findTop100ByGroupOrderBySentAtDesc(group)).thenReturn(List.of(youtube));

        assertEquals(1, service.getGroupMembers(3L, requester).size());
        assertEquals(1, service.searchMessages(3L, " hello ", requester).size());
        assertEquals(1, service.adminGetAllGroups().size());
        assertEquals(1, service.adminGetMessages(3L).size());
        assertEquals(1, service.adminGetGroupMembers(3L).size());
    }

    @Test
    void announcementChannelAddsOnlyActiveUsersWithAppropriateRoles() {
        User admin = user(1L, "admin");
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        User basic = user(2L, "basic");
        basic.setRole(Role.BASIC_USER);
        basic.setStatus(UserStatus.ACTIVE);
        User pending = user(3L, "pending");
        pending.setRole(Role.BASIC_USER);
        pending.setStatus(UserStatus.PENDING);
        ChatGroup announcements = ChatGroup.builder()
                .id(4L).name("Announcements").type(GroupType.ANNOUNCEMENT).createdBy(admin).build();
        when(groupRepository.findAllByType(GroupType.ANNOUNCEMENT)).thenReturn(List.of(announcements));
        when(userRepository.findAll()).thenReturn(List.of(admin, basic, pending));
        when(memberRepository.existsByGroupAndUser(announcements, admin)).thenReturn(true);
        when(memberRepository.existsByGroupAndUser(announcements, basic)).thenReturn(false);

        service.ensureAnnouncementChannel();

        ArgumentCaptor<GroupMember> member = ArgumentCaptor.forClass(GroupMember.class);
        verify(memberRepository).save(member.capture());
        assertEquals(basic, member.getValue().getUser());
        assertEquals(MemberRole.READONLY, member.getValue().getRole());
    }

    @Test
    void memberManagementRejectsAnUnknownTargetUser() {
        User requester = user(1L, "requester");
        ChatGroup group = group(3L);
        group.setCreatedBy(requester);
        GroupMember admin = GroupMember.builder()
                .group(group).user(requester).role(MemberRole.ADMIN).build();
        when(groupRepository.findById(3L)).thenReturn(Optional.of(group));
        when(memberRepository.findByGroupAndUser(group, requester)).thenReturn(Optional.of(admin));
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.addMember(3L, "missing", requester));
        assertThrows(RuntimeException.class, () -> service.updateMemberRole(3L, "missing", "ADMIN", requester));
        assertThrows(RuntimeException.class, () -> service.kickMember(3L, "missing", requester));
    }

    @Test
    void replyToTextUsesOriginalContentAsPreview() {
        User user = user(1L, "xian");
        ChatGroup group = group(2L);
        ChatMessage original = message(10L, group, user, MessageType.TEXT, "original text", null, null);
        ChatMessage reply = message(11L, group, user, MessageType.TEXT, "reply", null, original);
        when(groupRepository.findById(2L)).thenReturn(Optional.of(group));
        when(memberRepository.existsByGroupAndUser(group, user)).thenReturn(true);
        when(messageRepository.findTop100ByGroupOrderBySentAtDesc(group))
                .thenReturn(new ArrayList<>(List.of(reply)));

        ChatMessageDto result = service.getMessages(2L, user).get(0);

        assertEquals("original text", result.getReplyToContent());
    }

    private User user(Long id, String username) {
        return User.builder().id(id).username(username).build();
    }

    private ChatGroup group(Long id) {
        return ChatGroup.builder().id(id).name("group").type(GroupType.GROUP).build();
    }

    private ChatMessage message(Long id, ChatGroup group, User sender, MessageType type,
                                String content, String videoTitle, ChatMessage replyTo) {
        return ChatMessage.builder()
                .id(id)
                .group(group)
                .sender(sender)
                .messageType(type)
                .content(content)
                .videoTitle(videoTitle)
                .replyTo(replyTo)
                .sentAt(LocalDateTime.of(2026, Month.JULY, 12, 20, 0))
                .build();
    }
}
