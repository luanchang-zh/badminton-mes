package com.badminton.mes.module.rag.enums;

import java.util.Locale;
import java.util.Set;

/**
 * 知识库文档处理状态，前端轮询展示用。
 *
 * <p>上游 LightRAG 的状态字符串在不同版本间命名略有差异（processed / completed / success / done
 * 都可能表示完成）。差异属于上游事实，统一在本枚举内吸收，避免每个调用方各写一套判断。
 *
 * <p>未知状态一律归入 {@link #UNKNOWN}，不抛异常：上游新增状态不应导致本系统 500。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
public enum RagDocumentStatus {

    /** 排队或处理中 */
    PROCESSING("处理中"),

    /** 已完成，可检索 */
    COMPLETED("已完成"),

    /** 处理失败 */
    FAILED("失败"),

    /** 未识别的上游状态 */
    UNKNOWN("未知");

    /** 上游表示「处理中」的取值 */
    private static final Set<String> PROCESSING_VALUES =
            Set.of("pending", "processing", "queued", "running", "waiting",
                    // 上游解析流水线的中间态，缺了会被归到 UNKNOWN 显示成「未知」
                    "parsing", "analyzing", "preprocessed");

    /** 上游表示「已完成」的取值 */
    private static final Set<String> COMPLETED_VALUES =
            Set.of("processed", "completed", "success", "succeeded", "done", "finished");

    /** 上游表示「失败」的取值 */
    private static final Set<String> FAILED_VALUES =
            Set.of("failed", "error", "cancelled", "canceled", "aborted");

    /** 面向用户的中文描述 */
    private final String description;

    RagDocumentStatus(String description) {
        this.description = description;
    }

    /**
     * 获取中文描述，供前端直接展示。
     *
     * @return 中文状态描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 把上游状态字符串归一化为本枚举。
     *
     * <p>大小写不敏感、忽略首尾空白；null 或空串归入 {@link #UNKNOWN}，不抛异常。
     *
     * @param rawStatus 上游返回的原始状态，可能为 null
     * @return 归一化后的状态，永不为 null
     */
    public static RagDocumentStatus fromUpstream(String rawStatus) {
        if (rawStatus == null) {
            return UNKNOWN;
        }
        String normalized = rawStatus.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return UNKNOWN;
        }
        if (COMPLETED_VALUES.contains(normalized)) {
            return COMPLETED;
        }
        if (FAILED_VALUES.contains(normalized)) {
            return FAILED;
        }
        if (PROCESSING_VALUES.contains(normalized)) {
            return PROCESSING;
        }
        return UNKNOWN;
    }
}
