package com.EverLoad.everload.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_group_reads",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "group_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGroupRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ChatGroup group;

    @Column(nullable = false)
    private LocalDateTime lastReadAt;
}
