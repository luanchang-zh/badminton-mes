package com.badminton.mes.module.rag.client;

import java.util.regex.Pattern;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.rag.config.RagProperties;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 上游调用的公共守卫：租户白名单、路由前缀、内部密钥。
 *
 * <p>抽出本类的唯一原因是「安全校验只能有一份实现」：同步客户端与流式客户端
 * 都必须按同一套规则校验租户标识并拼接前缀，两份实现一旦漂移就会出现
 * 一条路径能越权、另一条不能的不一致缺陷。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
final class RagTenantGuard {

    /** 记录拒绝事件供排查；刻意不打印标识本身，避免日志侧泄漏租户信息 */
    private static final Logger logger = LoggerFactory.getLogger(RagTenantGuard.class);

    /** 租户标识白名单：只允许字母、数字与下划线，与 LightRAG 的 workspace 命名约束一致 */
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private RagTenantGuard() {
    }

    /**
     * 校验租户标识格式。
     *
     * <p>这是第二道防线：即使调用方传错，也不会拼出越界的请求路径。
     *
     * @param tenantId 原始租户标识
     * @return 校验通过的租户标识
     * @throws ServiceException 标识为空或含白名单外字符
     */
    static String requireValid(String tenantId) {
        if (tenantId == null || tenantId.isBlank()
                || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            logger.warn("Rejected invalid tenant id for upstream call");
            throw new ServiceException(RagErrorCodeConstants.TENANT_ID_INVALID);
        }
        return tenantId;
    }

    /**
     * 拼接租户路由地址。
     *
     * @param properties 上游配置
     * @param tenantId   已校验的租户标识
     * @param path       相对路径
     * @return 完整地址
     */
    static String tenantUri(RagProperties properties, String tenantId, String path) {
        String base = properties.getBaseUrl() == null
                ? "" : properties.getBaseUrl().replaceAll("/+$", "");
        return base + "/tenant/" + tenantId + "/" + path;
    }

    /**
     * 读取内部密钥；未配置时返回空串，由上游决定是否拒绝。
     *
     * @param properties 上游配置
     * @return 内部密钥
     */
    static String apiKey(RagProperties properties) {
        return properties.getInternalApiKey() == null ? "" : properties.getInternalApiKey();
    }
}
