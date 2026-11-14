package com.badminton.mes.module.rag.dal.repository;

import java.util.Optional;
import com.badminton.mes.module.rag.dal.entity.RagConversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RagConversationRepository extends JpaRepository<RagConversation, Long> {
    Optional<RagConversation> findByIdAndUserId(Long id, Long userId);
    Page<RagConversation> findByUserIdAndTenantId(Long userId, String tenantId, Pageable pageable);
    long countByUserIdAndTenantId(Long userId, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RagConversation c where c.id = :id and c.userId = :userId")
    Optional<RagConversation> lockOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying
    @Query("update RagConversation c set c.activeMessageId = null where c.activeMessageId is not null")
    int clearInterruptedGenerations();
}
