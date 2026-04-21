package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.ChatGroupDto;
import com.EverLoad.everload.dto.ChatMessageDto;
import com.EverLoad.everload.dto.CreateGroupRequest;
import com.EverLoad.everload.dto.SendMessageRequest;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.model.UserStatus;
import com.EverLoad.everload.repository.UserRepository;
import com.EverLoad.everload.service.ChatService;
import com.EverLoad.everload.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;
    private final PresenceService presenceService;

    private User getCurrentUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/groups")
    public ResponseEntity<List<ChatGroupDto>> getGroups(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(chatService.getGroupsForUser(user));
    }

    @PostMapping("/groups")
    public ResponseEntity<ChatGroupDto> createGroup(@RequestBody CreateGroupRequest request,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(chatService.createGroup(request, user));
    }

    @GetMapping("/groups/{id}/messages")
    public ResponseEntity<List<ChatMessageDto>> getMessages(@PathVariable Long id,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(chatService.getMessages(id, user));
    }

    @PostMapping("/groups/{id}/messages")
    public ResponseEntity<ChatMessageDto> sendMessage(@PathVariable Long id,
                                                       @RequestBody SendMessageRequest request,
                                                       @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(chatService.sendMessage(id, request, user));
    }

    @GetMapping("/groups/{id}/members")
    public ResponseEntity<List<Map<String, Object>>> getMembers(@PathVariable Long id,
                                                                  @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(chatService.getGroupMembers(id, user));
    }

    @PostMapping("/groups/{id}/members")
    public ResponseEntity<Void> addMember(@PathVariable Long id,
                                           @RequestBody Map<String, String> body,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        chatService.addMember(id, body.get("username"), user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/private/{username}")
    public ResponseEntity<ChatGroupDto> getOrCreatePrivateChat(@PathVariable String username,
                                                                 @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        User target = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(chatService.getOrCreatePrivateChat(user, target));
    }

    @GetMapping("/groups/{id}/messages/search")
    public ResponseEntity<List<ChatMessageDto>> searchMessages(@PathVariable Long id,
                                                                @RequestParam String q,
                                                                @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(chatService.searchMessages(id, q, user));
    }

    @DeleteMapping("/groups/{id}/messages")
    public ResponseEntity<Void> clearMessages(@PathVariable Long id,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        chatService.clearGroupMessages(id, user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        chatService.deleteGroupByUser(id, user);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/groups/{id}/info")
    public ResponseEntity<ChatGroupDto> updateGroupInfo(@PathVariable Long id,
                                                        @RequestBody Map<String, String> body,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(chatService.updateGroupInfo(id, body.get("name"), body.get("description"), user));
    }

    @PostMapping(value = "/groups/{id}/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateGroupAvatar(@PathVariable Long id,
                                               @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = getCurrentUser(userDetails);
            String filename = chatService.updateGroupImage(id, file, user);
            return ResponseEntity.ok(Map.of("message", "Group avatar updated", "imageFilename", filename));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/groups/{id}/members/{username}/role")
    public ResponseEntity<Void> updateMemberRole(@PathVariable Long id,
                                                 @PathVariable String username,
                                                 @RequestBody Map<String, String> body,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        chatService.updateMemberRole(id, username, body.get("role"), user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/groups/{id}/members/{username}")
    public ResponseEntity<Void> kickMember(@PathVariable Long id,
                                           @PathVariable String username,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        chatService.kickMember(id, username, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/groups/{id}/leave")
    public ResponseEntity<Void> leaveGroup(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        chatService.leaveGroup(id, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/groups/{id}/mark-read")
    public ResponseEntity<Void> markRead(@PathVariable Long id,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        chatService.markRead(id, user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/groups/{id}/read-status")
    public ResponseEntity<Map<String, String>> getReadStatus(@PathVariable Long id,
                                                              @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(chatService.getReadStatus(id, user));
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getActiveUsers(@AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && !u.getId().equals(currentUser.getId()))
                .collect(Collectors.toList());

        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> info = new HashMap<>();
            info.put("username", u.getUsername());
            info.put("avatarUrl", (u.getAvatarFilename() != null && !u.getAvatarFilename().isBlank())
                    ? "/api/user/avatar/img/" + u.getAvatarFilename()
                    : null);
            boolean online = presenceService.isOnline(u.getUsername());
            info.put("online", online);
            // Only expose lastSeen if the user allows it and is not online.
            // Serialize with explicit UTC offset (+00:00) so the browser parses it unambiguously.
            if (!online && !Boolean.FALSE.equals(u.getShowLastSeen()) && u.getLastSeen() != null) {
                info.put("lastSeen", u.getLastSeen().atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            } else {
                info.put("lastSeen", null);
            }
            return info;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}