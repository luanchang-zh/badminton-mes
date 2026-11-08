-- ----------------------------------------------------------------------------
-- RAG 知识库租户绑定
--
-- 业务用户与 LightRAG 租户的一一对应关系。租户标识由服务端维护，
-- 前端永远不参与决定，这是多租户隔离的第一道防线。
-- ----------------------------------------------------------------------------

CREATE TABLE rag_tenant_binding (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '业务系统用户主键',
    tenant_id   VARCHAR(64)  NOT NULL COMMENT 'LightRAG 租户标识，对应 tenants.yaml 的 user_id',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rag_tenant_binding_user (user_id),
    UNIQUE KEY uk_rag_tenant_binding_tenant (tenant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'RAG 知识库租户绑定';
