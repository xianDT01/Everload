package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.ChatGroupDto;
import com.EverLoad.everload.dto.ChatMessageDto;
import com.EverLoad.everload.dto.CreateGroupRequest;
import com.EverLoad.everload.dto.SendMessageRequest;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.model.UserStatus;
import com.EverLoad.everload.repository.UserRepository;
import com.EverLoad.everload.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, String>>> getActiveUsers(@AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && !u.getId().equals(currentUser.getId()))
                .collect(Collectors.toList());

        List<Map<String, String>> result = users.stream().map(u -> {
            Map<String, String> info = new HashMap<>();
            info.put("username", u.getUsername());
            if (u.getAvatarFilename() != null && !u.getAvatarFilename().isBlank()) {
                info.put("avatarUrl", "/api/user/avatar/img/" + u.getAvatarFilename());
            } else {
                info.put("avatarUrl", null);
            }
            return info;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}