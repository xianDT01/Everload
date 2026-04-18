package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.AdminChatGroupDto;
import com.EverLoad.everload.dto.ChatGroupDto;
import com.EverLoad.everload.dto.ChatMessageDto;
import com.EverLoad.everload.dto.CreateGroupRequest;
import com.EverLoad.everload.model.*;
import com.EverLoad.everload.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatGroupRepository chatGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final AvatarService avatarService;
    private final NotificationService notificationService;

    @Transactional
    public List<ChatGroupDto> getGroupsForUser(User user) {
        // Auto-add user to any announcement channels they're not yet a member of.
        // This covers users approved after the initial DataInitializer run.
        List<ChatGroup> announcements = chatGroupRepository.findAllByType(GroupType.ANNOUNCEMENT);
        for (ChatGroup g : announcements) {
            if (!groupMemberRepository.existsByGroupAndUser(g, user)) {
                MemberRole role = user.getRole() == Role.ADMIN ? MemberRole.ADMIN : MemberRole.READONLY;
                groupMemberRepository.save(GroupMember.builder()
                        .group(g)
                        .user(user)
                        .role(role)
                        .build());
            }
        }

        List<ChatGroup> groups = chatGroupRepository.findGroupsByUserId(user.getId());
        return groups.stream()
                .map(g -> toGroupDto(g, user))
                // Sort: groups with recent messages first; groups with no messages at the end
                .sorted(Comparator.comparing(
                        dto -> dto.getLastMessageTime() != null ? dto.getLastMessageTime() : LocalDateTime.MIN,
                        Comparator.reverseOrder()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public ChatGroupDto createGroup(CreateGroupRequest req, User creator) {
        GroupType type = GroupType.valueOf(req.getType());

        ChatGroup group = ChatGroup.builder()
                .name(req.getName())
                .description(req.getDescription())
                .type(type)
                .createdBy(creator)
                .build();
        final ChatGroup savedGroup = chatGroupRepository.save(group);

        // Add creator as ADMIN
        GroupMember creatorMember = GroupMember.builder()
                .group(savedGroup)
                .user(creator)
                .role(MemberRole.ADMIN)
                .build();
        groupMemberRepository.save(creatorMember);

        // Add other members
        if (req.getMemberUsernames() != null) {
            for (String username : req.getMemberUsernames()) {
                userRepository.findByUsername(username).ifPresent(u -> {
                    if (!u.getId().equals(creator.getId())) {
                        GroupMember member = GroupMember.builder()
                                .group(

                                        savedGroup)
                                .user(u)



                                .role(MemberRole.MEMBER)
                                .build();
                        groupMemberRepository.save(member);
                    }
                });
            }
        }

        return toGroupDto(savedGroup, creator);
    }

    @Transactional
    public ChatGroupDto getOrCreatePrivateChat(User user, User target) {
        // Look for existing PRIVATE group with both members
        List<ChatGroup> userGroups = chatGroupRepository.findGroupsByUserId(user.getId());
        for (ChatGroup g : userGroups) {
            if (g.getType() == GroupType.PRIVATE) {
                boolean targetIsMember = groupMemberRepository.existsByGroupAndUser(g, target);
                long memberCount = groupMemberRepository.countByGroup(g);
                if (targetIsMember && memberCount == 2) {
                    return toGroupDto(g, user);
                }
            }
        }

        // Create new private chat
        String chatName = user.getUsername() + " & " + target.getUsername();
        ChatGroup group = ChatGroup.builder()
                .name(chatName)
                .type(GroupType.PRIVATE)
                .createdBy(user)
                .build();
        group = chatGroupRepository.save(group);

        groupMemberRepository.save(GroupMember.builder().group(group).user(user).role(MemberRole.MEMBER).build());
        groupMemberRepository.save(GroupMember.builder().group(group).user(target).role(MemberRole.MEMBER).build());

        return toGroupDto(group, user);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(Long groupId, User user) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (!groupMemberRepository.existsByGroupAndUser(group, user)) {
            throw new RuntimeException("Access denied");
        }

        // Get last 100, return in ascending order
        List<ChatMessage> messages = chatMessageRepository.findTop100ByGroupOrderBySentAtDesc(group);
        Collections.reverse(messages);
        return messages.stream().map(this::toMessageDto).collect(Collectors.toList());
    }

    @Transactional
    public ChatMessageDto sendMessage(Long groupId, com.EverLoad.everload.dto.SendMessageRequest request, User sender) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        GroupMember member = groupMemberRepository.findByGroupAndUser(group, sender)
                .orElseThrow(() -> new RuntimeException("Not a member"));

        if (member.getRole() == MemberRole.READONLY) {
            throw new RuntimeException("Read-only member cannot send messages");
        }

        MessageType msgType = MessageType.TEXT;
        if ("YOUTUBE_SHARE".equals(request.getMessageType())) {
            msgType = MessageType.YOUTUBE_SHARE;
        }

        ChatMessage replyTo = null;
        if (request.getReplyToMessageId() != null) {
            replyTo = chatMessageRepository.findById(request.getReplyToMessageId()).orElse(null);
        }

        ChatMessage message = ChatMessage.builder()
                .group(group)
                .sender(sender)
                .content(request.getContent() != null ? request.getContent() : "")
                .messageType(msgType)
                .videoId(request.getVideoId())
                .videoTitle(request.getVideoTitle())
                .thumbnailUrl(request.getThumbnailUrl())
                .channelTitle(request.getChannelTitle())
                .replyTo(replyTo)
                .edited(false)
                .build();
        message = chatMessageRepository.save(message);

        // Notify mentioned users (@username in plain-text messages)
        if (msgType == MessageType.TEXT && message.getContent() != null && !message.getContent().isEmpty()) {
            notifyMentionedUsers(message, group, sender);
        }

        return toMessageDto(message);
    }

    private void notifyMentionedUsers(ChatMessage message, ChatGroup group, User sender) {
        Matcher matcher = Pattern.compile("@(\\w+)").matcher(message.getContent());
        Set<String> mentioned = new LinkedHashSet<>();
        while (matcher.find()) mentioned.add(matcher.group(1));

        String preview = message.getContent().length() > 60
                ? message.getContent().substring(0, 60) + "…"
                : message.getContent();

        for (String username : mentioned) {
            if (username.equals(sender.getUsername())) continue;
            userRepository.findByUsername(username).ifPresent(mentionedUser -> {
                if (groupMemberRepository.existsByGroupAndUser(group, mentionedUser)) {
                    notificationService.createNotification(
                            mentionedUser,
                            "MENTION",
                            sender.getUsername() + " te mencionó en " + group.getName(),
                            preview,
                            "/chat?group=" + group.getId()
                    );
                }
            });
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getGroupMembers(Long groupId, User user) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (!groupMemberRepository.existsByGroupAndUser(group, user)) {
            throw new RuntimeException("Access denied");
        }

        return groupMemberRepository.findByGroup(group).stream()
                .map(m -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("username", m.getUser().getUsername());
                    info.put("role", m.getRole().name());
                    info.put("avatarUrl", buildAvatarUrl(m.getUser()));
                    info.put("joinedAt", m.getJoinedAt());
                    return info;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void addMember(Long groupId, String username, User requester) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        GroupMember requesterMember = groupMemberRepository.findByGroupAndUser(group, requester)
                .orElseThrow(() -> new RuntimeException("Not a member"));

        if (requesterMember.getRole() != MemberRole.ADMIN) {
            throw new RuntimeException("Only admins can add members");
        }

        User newUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!groupMemberRepository.existsByGroupAndUser(group, newUser)) {
            MemberRole role = group.getType() == GroupType.ANNOUNCEMENT ? MemberRole.READONLY : MemberRole.MEMBER;
            GroupMember member = GroupMember.builder()
                    .group(group)
                    .user(newUser)
                    .role(role)
                    .build();
            groupMemberRepository.save(member);
        }
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> searchMessages(Long groupId, String query, User user) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (!groupMemberRepository.existsByGroupAndUser(group, user)) {
            throw new RuntimeException("Access denied");
        }

        if (query == null || query.isBlank()) {
            return List.of();
        }

        return chatMessageRepository
                .findByGroupAndContentContainingIgnoreCaseOrderBySentAtDesc(group, query.trim())
                .stream()
                .limit(50)
                .map(this::toMessageDto)
                .collect(Collectors.toList());
    }

    // ── Group Administration ──────────────────────────────────────────────────

    @Transactional
    public ChatGroupDto updateGroupInfo(Long groupId, String name, String description, User requester) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (group.getType() == GroupType.ANNOUNCEMENT || group.getType() == GroupType.PRIVATE) {
            throw new RuntimeException("Cannot modify this group type");
        }

        GroupMember requesterMember = groupMemberRepository.findByGroupAndUser(group, requester)
                .orElseThrow(() -> new RuntimeException("Not a member"));

        if (requesterMember.getRole() != MemberRole.ADMIN) {
            throw new RuntimeException("Only admins can edit group info");
        }

        group.setName(name);
        group.setDescription(description);
        chatGroupRepository.save(group);
        return toGroupDto(group, requester);
    }

    @Transactional
    public String updateGroupImage(Long groupId, org.springframework.web.multipart.MultipartFile file, User requester) throws java.io.IOException {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        GroupMember requesterMember = groupMemberRepository.findByGroupAndUser(group, requester)
                .orElseThrow(() -> new RuntimeException("Not a member"));

        if (requesterMember.getRole() != MemberRole.ADMIN) {
            throw new RuntimeException("Only admins can edit group image");
        }

        String filename = avatarService.uploadGroupAvatar(groupId, group.getImageFilename(), file);
        group.setImageFilename(filename);
        chatGroupRepository.save(group);
        return filename;
    }

    @Transactional
    public void updateMemberRole(Long groupId, String username, String newRole, User requester) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (!group.getCreatedBy().getId().equals(requester.getId())) {
            throw new RuntimeException("Only the group creator can manage admin roles");
        }

        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (targetUser.getId().equals(group.getCreatedBy().getId())) {
            throw new RuntimeException("Cannot change the role of the creator");
        }

        GroupMember member = groupMemberRepository.findByGroupAndUser(group, targetUser)
                .orElseThrow(() -> new RuntimeException("Target is not a member"));

        try {
            MemberRole parsedRole = MemberRole.valueOf(newRole.toUpperCase());
            member.setRole(parsedRole);
            groupMemberRepository.save(member);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid role");
        }
    }

    @Transactional
    public void kickMember(Long groupId, String username, User requester) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        GroupMember requesterMember = groupMemberRepository.findByGroupAndUser(group, requester)
                .orElseThrow(() -> new RuntimeException("Not a member"));

        if (requesterMember.getRole() != MemberRole.ADMIN) {
            throw new RuntimeException("Only admins can kick members");
        }

        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (targetUser.getId().equals(group.getCreatedBy().getId())) {
            throw new RuntimeException("Cannot kick the group creator");
        }
        
        if (targetUser.getId().equals(requester.getId())) {
             throw new RuntimeException("Use leave action instead");
        }

        GroupMember member = groupMemberRepository.findByGroupAndUser(group, targetUser)
                .orElseThrow(() -> new RuntimeException("Target is not a member"));

        groupMemberRepository.delete(member);
    }

    @Transactional
    public void leaveGroup(Long groupId, User user) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (group.getType() == GroupType.ANNOUNCEMENT) {
            throw new RuntimeException("Cannot leave announcement channel");
        }

        if (group.getType() == GroupType.PRIVATE) {
             throw new RuntimeException("Cannot leave private chat, delete it instead.");
        }

        if (group.getCreatedBy().getId().equals(user.getId())) {
             // The creator wants to leave. According to plan, this defaults to deleting the group.
             chatMessageRepository.deleteByGroup(group);
             groupMemberRepository.deleteByGroup(group);
             chatGroupRepository.delete(group);
             return;
        }

        GroupMember member = groupMemberRepository.findByGroupAndUser(group, user)
                .orElseThrow(() -> new RuntimeException("Not a member"));

        groupMemberRepository.delete(member);
    }

    // ── User chat management ──────────────────────────────────────────────────

    @Transactional
    public void clearGroupMessages(Long groupId, User user) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (group.getType() == GroupType.ANNOUNCEMENT) {
            throw new RuntimeException("Cannot clear announcement channel");
        }

        GroupMember member = groupMemberRepository.findByGroupAndUser(group, user)
                .orElseThrow(() -> new RuntimeException("Not a member"));

        if (group.getType() != GroupType.PRIVATE && member.getRole() != MemberRole.ADMIN) {
            throw new RuntimeException("Only group admins can clear messages");
        }

        chatMessageRepository.deleteByGroup(group);
    }

    @Transactional
    public void deleteGroupByUser(Long groupId, User user) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (group.getType() == GroupType.ANNOUNCEMENT) {
            throw new RuntimeException("Cannot delete announcement channel");
        }

        GroupMember member = groupMemberRepository.findByGroupAndUser(group, user)
                .orElseThrow(() -> new RuntimeException("Not a member"));

        if (group.getType() != GroupType.PRIVATE && member.getRole() != MemberRole.ADMIN) {
            throw new RuntimeException("Only group admins can delete the group");
        }

        chatMessageRepository.deleteByGroup(group);
        groupMemberRepository.deleteByGroup(group);
        chatGroupRepository.delete(group);
    }

    // ── Admin moderation ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminChatGroupDto> adminGetAllGroups() {
        return chatGroupRepository.findAll().stream()
                .map(this::toAdminGroupDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> adminGetMessages(Long groupId) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        return chatMessageRepository.findByGroupOrderBySentAtAsc(group)
                .stream().map(this::toMessageDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminGetGroupMembers(Long groupId) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        return groupMemberRepository.findByGroup(group).stream()
                .map(m -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("username", m.getUser().getUsername());
                    info.put("role", m.getRole().name());
                    info.put("avatarUrl", buildAvatarUrl(m.getUser()));
                    info.put("joinedAt", m.getJoinedAt());
                    return info;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void adminDeleteGroup(Long groupId) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        chatMessageRepository.deleteByGroup(group);
        groupMemberRepository.deleteByGroup(group);
        chatGroupRepository.delete(group);
    }

    @Transactional
    public void adminDeleteMessage(Long messageId) {
        chatMessageRepository.deleteById(messageId);
    }

    @Transactional
    public void adminRemoveMember(Long groupId, String username) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        userRepository.findByUsername(username).ifPresent(user ->
                groupMemberRepository.findByGroupAndUser(group, user)
                        .ifPresent(groupMemberRepository::delete));
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void ensureAnnouncementChannel() {
        List<ChatGroup> announcements = chatGroupRepository.findAllByType(GroupType.ANNOUNCEMENT);

        ChatGroup announcementGroup;
        if (announcements.isEmpty()) {
            User admin = userRepository.findByUsername("admin").orElse(null);
            announcementGroup = ChatGroup.builder()
                    .name("Avisos EverLoad")
                    .description("Canal oficial de avisos de EverLoad")
                    .type(GroupType.ANNOUNCEMENT)
                    .createdBy(admin)
                    .build();
            announcementGroup = chatGroupRepository.save(announcementGroup);

            if (admin != null) {
                groupMemberRepository.save(GroupMember.builder()
                        .group(announcementGroup)
                        .user(admin)
                        .role(MemberRole.ADMIN)
                        .build());
            }
        } else {
            announcementGroup = announcements.get(0);
        }

        // Add all active users as READONLY if not already members
        final ChatGroup finalGroup = announcementGroup;
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .collect(Collectors.toList());

        for (User u : activeUsers) {
            if (!groupMemberRepository.existsByGroupAndUser(finalGroup, u)) {
                MemberRole role = u.getRole() == Role.ADMIN ? MemberRole.ADMIN : MemberRole.READONLY;
                groupMemberRepository.save(GroupMember.builder()
                        .group(finalGroup)
                        .user(u)
                        .role(role)
                        .build());
            }
        }
    }

    private ChatGroupDto toGroupDto(ChatGroup g, User currentUser) {
        // Load all members once — used for memberCount, onlineCount, and partner detection
        List<GroupMember> members = groupMemberRepository.findByGroup(g);
        long memberCount = members.size();

        // Count online members
        int onlineCount = (int) members.stream()
                .filter(m -> presenceService.isOnline(m.getUser().getUsername()))
                .count();

        // Get last message
        List<ChatMessage> lastMessages = chatMessageRepository.findTop100ByGroupOrderBySentAtDesc(g);
        String lastMessage = null;
        LocalDateTime lastMessageTime = null;
        String lastSenderAvatarUrl = null;
        if (!lastMessages.isEmpty()) {
            ChatMessage lm = lastMessages.get(0);
            String preview = lm.getMessageType() == MessageType.YOUTUBE_SHARE
                    ? "🎬 " + (lm.getVideoTitle() != null ? truncate(lm.getVideoTitle(), 40) : "Vídeo de YouTube")
                    : truncate(lm.getContent(), 50);
            lastMessage = lm.getSender().getUsername() + ": " + preview;
            lastMessageTime = lm.getSentAt();
            lastSenderAvatarUrl = buildAvatarUrl(lm.getSender());
        }

        String privatePartnerUsername = null;
        String privatePartnerAvatarUrl = null;
        Boolean partnerOnline = null;
        LocalDateTime partnerLastSeen = null;

        String currentUserRole = null;
        if (g.getType() == GroupType.PRIVATE) {
            for (GroupMember m : members) {
                if (!m.getUser().getId().equals(currentUser.getId())) {
                    User partner = m.getUser();
                    privatePartnerUsername = partner.getUsername();
                    privatePartnerAvatarUrl = buildAvatarUrl(partner);
                    partnerOnline = presenceService.isOnline(partner.getUsername());
                    // Only expose lastSeen if the partner allows it and is not currently online
                    if (!partnerOnline && !Boolean.FALSE.equals(partner.getShowLastSeen())) {
                        partnerLastSeen = partner.getLastSeen();
                    }
                } else {
                    currentUserRole = m.getRole().name();
                }
            }
        } else {
            for (GroupMember m : members) {
                if (m.getUser().getId().equals(currentUser.getId())) {
                    currentUserRole = m.getRole().name();
                    break;
                }
            }
        }

        return ChatGroupDto.builder()
                .id(g.getId())
                .name(g.getName())
                .description(g.getDescription())
                .type(g.getType().name())
                .createdAt(g.getCreatedAt())
                .memberCount((int) memberCount)
                .lastMessage(lastMessage)
                .lastMessageTime(lastMessageTime)
                .imageFilename(g.getImageFilename())
                .createdByUsername(g.getCreatedBy() != null ? g.getCreatedBy().getUsername() : null)
                .lastSenderAvatarUrl(lastSenderAvatarUrl)
                .privatePartnerUsername(privatePartnerUsername)
                .privatePartnerAvatarUrl(privatePartnerAvatarUrl)
                .partnerOnline(partnerOnline)
                .partnerLastSeen(partnerLastSeen)
                .onlineCount(onlineCount)
                .currentUserRole(currentUserRole)
                .build();
    }

    private ChatMessageDto toMessageDto(ChatMessage m) {
        ChatMessageDto.ChatMessageDtoBuilder builder = ChatMessageDto.builder()
                .id(m.getId())
                .groupId(m.getGroup().getId())
                .senderUsername(m.getSender().getUsername())
                .senderAvatarUrl(buildAvatarUrl(m.getSender()))
                .content(m.getContent())
                .messageType(m.getMessageType() != null ? m.getMessageType().name() : MessageType.TEXT.name())
                .videoId(m.getVideoId())
                .videoTitle(m.getVideoTitle())
                .thumbnailUrl(m.getThumbnailUrl())
                .channelTitle(m.getChannelTitle())
                .sentAt(m.getSentAt())
                .edited(m.isEdited());

        if (m.getReplyTo() != null) {
            ChatMessage rt = m.getReplyTo();
            builder.replyToId(rt.getId())
                   .replyToSender(rt.getSender().getUsername())
                   .replyToContent(truncate(
                       rt.getMessageType() == MessageType.YOUTUBE_SHARE
                           ? "🎬 " + (rt.getVideoTitle() != null ? rt.getVideoTitle() : "Vídeo")
                           : rt.getContent(),
                       120
                   ));
        }

        return builder.build();
    }

    private String buildAvatarUrl(User user) {
        if (user.getAvatarFilename() == null || user.getAvatarFilename().isBlank()) return null;
        return "/api/user/avatar/img/" + user.getAvatarFilename();
    }

    private AdminChatGroupDto toAdminGroupDto(ChatGroup g) {
        long memberCount = groupMemberRepository.countByGroup(g);
        long messageCount = chatMessageRepository.countByGroup(g);

        List<ChatMessage> lastMsgs = chatMessageRepository.findTop100ByGroupOrderBySentAtDesc(g);
        String lastMessage = null;
        java.time.LocalDateTime lastMessageTime = null;
        if (!lastMsgs.isEmpty()) {
            ChatMessage lm = lastMsgs.get(0);
            String preview = lm.getMessageType() == MessageType.YOUTUBE_SHARE
                    ? "🎬 " + (lm.getVideoTitle() != null ? truncate(lm.getVideoTitle(), 40) : "Vídeo de YouTube")
                    : truncate(lm.getContent(), 50);
            lastMessage = lm.getSender().getUsername() + ": " + preview;
            lastMessageTime = lm.getSentAt();
        }

        return AdminChatGroupDto.builder()
                .id(g.getId())
                .name(g.getName())
                .description(g.getDescription())
                .type(g.getType().name())
                .createdAt(g.getCreatedAt())
                .memberCount((int) memberCount)
                .messageCount(messageCount)
                .lastMessage(lastMessage)
                .lastMessageTime(lastMessageTime)
                .createdByUsername(g.getCreatedBy() != null ? g.getCreatedBy().getUsername() : null)
                .build();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}