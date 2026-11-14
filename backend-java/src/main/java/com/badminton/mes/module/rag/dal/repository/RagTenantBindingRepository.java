package com.badminton.mes.module.rag.dal.repository;

import java.util.Optional;

import com.badminton.mes.module.rag.dal.entity.RagTenantBinding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * 租户绑定查询。
 *
 * <p>只暴露「按业务用户查绑定」这一种查询：调用方先拿到绑定实体，
 * 再由 {@code RagTenantResolver} 判断启用状态并给出区分明确的错误码。
 * 若这里直接返回「启用中的绑定」，调用方就无法区分「未绑定」与「已禁用」。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
public interface RagTenantBindingRepository extends JpaRepository<RagTenantBinding, Long> {

    /**
     * 按业务用户主键查询绑定，不区分启用状态。
     *
     * @param userId 业务系统用户主键
     * @return 绑定关系，不存在时为空
     */
    Optional<RagTenantBinding> findByUserId(Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO rag_tenant_binding (user_id, tenant_id, enabled)
            VALUES (:userId, :tenantId, 1)
            ON DUPLICATE KEY UPDATE user_id = user_id
            """, nativeQuery = true)
    int createIfAbsent(Long userId, String tenantId);
}
