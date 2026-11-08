-- ----------------------------------------------------------------------------
-- RAG 知识库租户绑定演示数据
--
-- 给两个演示账号各绑定一个 LightRAG 租户，租户标识必须与
-- lightrag-service/config/tenants.yaml 中的 user_id 一致：
--   user_1 → ws_001
--   user_2 → ws_002
--
-- 用 REPEATABLE 迁移（R__）保证重复执行幂等；user_id 通过 user_no 反查，
-- 不写死自增主键，避免与既有演示数据的插入顺序耦合。
-- ----------------------------------------------------------------------------

INSERT INTO rag_tenant_binding (user_id, tenant_id, enabled)
SELECT u.id, 'user_1', 1
FROM sys_user u
WHERE u.user_no = 'pmc_demo'
ON DUPLICATE KEY UPDATE tenant_id = VALUES(tenant_id), enabled = VALUES(enabled);

INSERT INTO rag_tenant_binding (user_id, tenant_id, enabled)
SELECT u.id, 'user_2', 1
FROM sys_user u
WHERE u.user_no = 'manager_demo'
ON DUPLICATE KEY UPDATE tenant_id = VALUES(tenant_id), enabled = VALUES(enabled);
