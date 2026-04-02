package com.EverLoad.everload.service;

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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatGroupRepository chatGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ChatGroupDto> getGroupsForUser(User user) {
        List<ChatGroup> groups = chatGroupRepository.findGroupsByUserId(user.getId());
        return groups.stream()
                .map(g -> toGroupDto(g, user))
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
    public ChatMessageDto sendMessage(Long groupId, String content, User sender) {
        ChatGroup group = chatGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        GroupMember member = groupMemberRepository.findByGroupAndUser(group, sender)
                .orElseThrow(() -> new RuntimeException("Not a member"));

        if (member.getRole() == MemberRole.READONLY) {
            throw new RuntimeException("Read-only member cannot send messages");
        }

        ChatMessage message = ChatMessage.builder()
                .group(group)
                .sender(sender)
                .content(content)
                .edited(false)
                .build();
        message = chatMessageRepository.save(message);

        return toMessageDto(message);
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
        long memberCount = groupMemberRepository.countByGroup(g);

        // Get last message
        List<ChatMessage> lastMessages = chatMessageRepository.findTop100ByGroupOrderBySentAtDesc(g);
        String lastMessage = null;
        LocalDateTime lastMessageTime = null;
        if (!lastMessages.isEmpty()) {
            ChatMessage lm = lastMessages.get(0);
            lastMessage = lm.getSender().getUsername() + ": " + truncate(lm.getContent(), 50);
            lastMessageTime = lm.getSentAt();
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
                .build();
    }

    private ChatMessageDto toMessageDto(ChatMessage m) {
        return ChatMessageDto.builder()
                .id(m.getId())
                .groupId(m.getGroup().getId())
                .senderUsername(m.getSender().getUsername())
                .senderAvatarUrl(buildAvatarUrl(m.getSender()))
                .content(m.getContent())
                .sentAt(m.getSentAt())
                .edited(m.isEdited())
                .build();
    }

    private String buildAvatarUrl(User user) {
        if (user.getAvatarFilename() == null || user.getAvatarFilename().isBlank()) return null;
        return "/api/user/avatar/img/" + user.getAvatarFilename();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}