package com.badminton.mes.module.rag.constants;

import com.badminton.mes.common.core.ErrorCode;

/**
 * RAG 知识库模块错误码。
 *
 * <p>编号优先复用手册附表 3 的既有错误码，业务细节由 message 承载；
 * {@code message} 面向排查人员，{@code userTip} 面向最终用户，两者不混用。
 *
 * <p>这里刻意不把上游地址、上游原始响应写进任何 message 或 userTip：
 * 上游细节只写日志，避免向调用方泄露内部拓扑。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
public final class RagErrorCodeConstants {

    /** 当前登录用户未绑定任何知识库租户 */
    public static final ErrorCode TENANT_NOT_BOUND =
            new ErrorCode("A0403", "当前用户未绑定知识库租户", "尚未为您开通知识库，请联系管理员");

    /** 绑定存在但已被禁用 */
    public static final ErrorCode TENANT_DISABLED =
            new ErrorCode("A0403", "知识库租户已禁用", "您的知识库权限已停用，请联系管理员");

    /** 租户标识不满足命名白名单，可能是配置错误或路径注入尝试 */
    public static final ErrorCode TENANT_ID_INVALID =
            new ErrorCode("A0400", "租户标识不合法", "知识库配置异常，请联系管理员");

    /** 上游服务不可达或超时 */
    public static final ErrorCode UPSTREAM_UNAVAILABLE =
            new ErrorCode("C0301", "知识库服务不可达", "知识库服务暂时不可用，请稍后重试");

    /** 上游返回非 2xx 状态 */
    public static final ErrorCode UPSTREAM_ERROR =
            new ErrorCode("C0302", "知识库服务返回错误", "知识库处理失败，请稍后重试或联系管理员");

    /** 上传内容超出允许大小 */
    public static final ErrorCode UPLOAD_TOO_LARGE =
            new ErrorCode("A0400", "上传内容超过大小上限", "文件过大，请压缩后重试");

    private RagErrorCodeConstants() {
    }
}
