package com.badminton.mes.module.rag.dal.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * 业务用户与 LightRAG 租户的绑定关系。
 *
 * <p>租户标识的权威来源就是这张表：业务侧只认 user_id，租户标识由服务端决定，
 * 前端不参与。{@code tenantId} 与 LightRAG 的 {@code config/tenants.yaml} 中的 user_id 对应。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
@Getter
@Setter
@Entity
@Table(name = "rag_tenant_binding")
public class RagTenantBinding {

    /** 主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务系统用户主键，全局唯一 */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** LightRAG 租户标识，全局唯一 */
    @Column(name = "tenant_id", nullable = false, unique = true, length = 64)
    private String tenantId;

    /** 是否启用，停用后该用户无法访问知识库 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
