-- 人脸登录与 MES 用户绑定。InsightFace 保存人员和特征，MES 只记录哪个用户对应哪个 Person。
-- 有效绑定用生成列做唯一约束(与 V2026071601 修正微信绑定的做法一致)：
-- 关闭人脸后再次注册会新增一行，历史行保留，同一用户同时只能有一条未删除绑定。
CREATE TABLE sys_face_binding (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'MES 用户主键',
    collection_id VARCHAR(64) NOT NULL COMMENT 'InsightFace Collection id',
    person_id VARCHAR(64) NOT NULL COMMENT 'InsightFace Person id',
    consent_time DATETIME NOT NULL COMMENT '用户勾选人脸信息处理同意的时间',
    last_login_time DATETIME NULL COMMENT '最近一次人脸登录成功时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    active_user_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN is_deleted = 0 THEN user_id ELSE NULL END
    ) STORED COMMENT '有效绑定用户，供唯一索引使用',
    active_person_id VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN is_deleted = 0 THEN person_id ELSE NULL END
    ) STORED COMMENT '有效 Person id，供唯一索引使用',
    PRIMARY KEY pk_id (id),
    UNIQUE KEY uk_active_user_id (active_user_id),
    UNIQUE KEY uk_collection_active_person (collection_id, active_person_id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '人脸登录与 MES 用户绑定';
