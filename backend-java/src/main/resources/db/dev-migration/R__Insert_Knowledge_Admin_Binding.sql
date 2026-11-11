-- An independent development workspace for admin; never rebind an existing account.
INSERT INTO rag_tenant_binding (user_id, tenant_id, enabled)
SELECT u.id, 'user_admin', 1 FROM sys_user u
WHERE u.user_no = 'admin'
AND NOT EXISTS (SELECT 1 FROM rag_tenant_binding b WHERE b.user_id = u.id)
AND NOT EXISTS (SELECT 1 FROM rag_tenant_binding b WHERE b.tenant_id = 'user_admin');
