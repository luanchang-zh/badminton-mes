package com.badminton.mes.module.rag.service;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.common.security.SecurityContextHolder;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import com.badminton.mes.module.rag.dal.entity.RagTenantBinding;
import com.badminton.mes.module.rag.dal.repository.RagTenantBindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 已登录用户首次访问聊天页时，按用户主键建立独立的本地会话空间。 */
@Service
public class RagBindingService {
    private final RagTenantBindingRepository bindings;

    public RagBindingService(RagTenantBindingRepository bindings) {
        this.bindings = bindings;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RagTenantBinding ensureCurrentBinding() {
        Long userId = SecurityContextHolder.getRequiredLoginUserId();
        var existing = bindings.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        bindings.createIfAbsent(userId, "mes_u_" + userId);
        return bindings.findByUserId(userId)
                .orElseThrow(() -> new ServiceException(RagErrorCodeConstants.TENANT_NOT_BOUND));
    }
}
