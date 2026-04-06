package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.AdminChatGroupDto;
import com.EverLoad.everload.dto.ChatMessageDto;
import com.EverLoad.everload.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Admin Chat", description = "Moderación de chats (solo administradores)")
@RestController
@RequestMapping("/api/admin/chat")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminChatController {

    private final ChatService chatService;

    @Operation(summary = "Listar todos los grupos de chat")
    @GetMapping("/groups")
    public ResponseEntity<List<AdminChatGroupDto>> getAllGroups() {
        return ResponseEntity.ok(chatService.adminGetAllGroups());
    }

    @Operation(summary = "Ver mensajes de un grupo")
    @GetMapping("/groups/{id}/messages")
    public ResponseEntity<List<ChatMessageDto>> getGroupMessages(@PathVariable Long id) {
        return ResponseEntity.ok(chatService.adminGetMessages(id));
    }

    @Operation(summary = "Ver miembros de un grupo")
    @GetMapping("/groups/{id}/members")
    public ResponseEntity<List<Map<String, Object>>> getGroupMembers(@PathVariable Long id) {
        return ResponseEntity.ok(chatService.adminGetGroupMembers(id));
    }

    @Operation(summary = "Eliminar un grupo completo con sus mensajes y miembros")
    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        chatService.adminDeleteGroup(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Eliminar un mensaje concreto")
    @DeleteMapping("/messages/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long id) {
        chatService.adminDeleteMessage(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Expulsar a un usuario de un grupo")
    @DeleteMapping("/groups/{id}/members/{username}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable String username) {
        chatService.adminRemoveMember(id, username);
        return ResponseEntity.ok().build();
    }
}
