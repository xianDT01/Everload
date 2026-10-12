package com.everload.everload.controller;

import com.everload.everload.dto.AdminChatGroupDto;
import com.everload.everload.service.AuditLogService;
import com.everload.everload.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin Chat", description = "Moderación de chats (solo administradores)")
@RestController
@RequestMapping("/api/admin/chat")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminChatController {

    private final ChatService chatService;
    private final AuditLogService auditLogService;

    @Operation(summary = "Listar todos los grupos de chat")
    @GetMapping("/groups")
    public ResponseEntity<List<AdminChatGroupDto>> getAllGroups() {
        return ResponseEntity.ok(chatService.adminGetAllGroups());
    }

    @Operation(summary = "Eliminar todos los mensajes de un grupo")
    @DeleteMapping("/groups/{id}/messages")
    public ResponseEntity<Void> clearGroupMessages(@PathVariable Long id) {
        chatService.adminClearGroupMessages(id);
        auditLogService.log("GROUP_MESSAGES_CLEARED", "ChatGroup", id.toString(), null);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Eliminar un grupo completo con sus mensajes y miembros")
    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        chatService.adminDeleteGroup(id);
        auditLogService.log("GROUP_DELETED", "ChatGroup", id.toString(), null);
        return ResponseEntity.ok().build();
    }

}
