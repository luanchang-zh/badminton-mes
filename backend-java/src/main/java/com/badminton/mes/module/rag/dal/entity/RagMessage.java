package com.badminton.mes.module.rag.dal.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 用户问题与回答分别保存；requestId 只落在回答记录上以支持幂等。 */
@Getter
@Setter
@Entity
@Table(name = "rag_message", uniqueConstraints =
        @UniqueConstraint(columnNames = {"conversation_id", "request_id"}))
public class RagMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long conversationId;
    @Column(nullable = false, length = 16)
    private String role;
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content = "";
    @Column(nullable = false, length = 16)
    private String mode;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(length = 64)
    private String requestId;
    private Long replyToId;
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String referencesJson = "[]";
    @Column(length = 255)
    private String errorTip;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void initializeTime() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }
}
