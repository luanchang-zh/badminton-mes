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

    /** Document must exist within the current tenant. */
    public static final ErrorCode DOCUMENT_NOT_FOUND =
            new ErrorCode("A0404", "文档正文不存在", "资料不存在或尚未完成解析，请刷新后重试");

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

    /** 上传文件类型不在白名单内 */
    public static final ErrorCode UPLOAD_TYPE_UNSUPPORTED =
            new ErrorCode("A0400", "上传文件类型不支持", "只支持纯文本、Markdown 一类可解析为文本的文件");

    /** 检索模式不在支持范围内 */
    public static final ErrorCode QUERY_MODE_INVALID =
            new ErrorCode("A0400", "不支持的检索模式", "检索模式不支持，请重新选择");

    /** 上传文件读取失败，例如编码不正确或流被中断 */
    public static final ErrorCode UPLOAD_READ_FAILED =
            new ErrorCode("A0400", "上传文件读取失败", "文件内容无法读取，请确认是 UTF-8 文本后重试");

    /** 流式问答在建立上游连接前就失败，无法再通过 SSE 事件告知前端 */
    public static final ErrorCode STREAM_START_FAILED =
            new ErrorCode("C0301", "流式问答启动失败", "知识库服务暂时不可用，请稍后重试");

    /** 同名文档已存在，上游以 409 拒绝 */
    public static final ErrorCode DOCUMENT_CONFLICT =
            new ErrorCode("A0409", "同名文档已存在", "已有同名文档，请先删除原文档再上传");

    /** 上游以 4xx 拒绝了请求，通常意味着入参不符合上游约束 */
    public static final ErrorCode REQUEST_REJECTED =
            new ErrorCode("A0400", "知识库服务拒绝了请求", "提交内容不符合知识库要求，请检查后重试");

    /** 上游正在执行其他任务（流水线忙碌或限流） */
    public static final ErrorCode UPSTREAM_BUSY =
            new ErrorCode("A0409", "知识库正在处理其他任务", "知识库繁忙，请稍后再试");

    private RagErrorCodeConstants() {
    }
}
