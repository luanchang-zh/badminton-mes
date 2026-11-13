package com.badminton.mes.module.rag.dal.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 一个 MES 用户在固定知识库中的会话。 */
@Getter
@Setter
@Entity
@Table(name = "rag_conversation")
public class RagConversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, length = 64)
    private String tenantId;
    @Column(nullable = false, length = 120)
    private String title;
    private Long activeMessageId;
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
