package com.badminton.mes.module.rag.dal.repository;

import java.util.List;
import java.util.Optional;
import com.badminton.mes.module.rag.dal.entity.RagMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface RagMessageRepository extends JpaRepository<RagMessage, Long> {
    @Query("select m.conversationId from RagMessage m where m.id = :id")
    Optional<Long> findConversationIdById(@org.springframework.data.repository.query.Param("id") Long id);
    Page<RagMessage> findByConversationId(Long conversationId, Pageable pageable);
    Optional<RagMessage> findByConversationIdAndRequestId(Long conversationId, String requestId);
    List<RagMessage> findTop100ByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId);
    void deleteByConversationId(Long conversationId);

    @Modifying
    @Query("update RagMessage m set m.status = 'FAILED', m.errorTip = '服务重启，生成已中断' "
            + "where m.status = 'GENERATING'")
    int failInterruptedGenerations();
}
