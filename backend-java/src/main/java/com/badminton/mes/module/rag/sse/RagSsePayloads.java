package com.badminton.mes.module.rag.sse;

import java.util.List;
import java.util.Map;

/**
 * 流式问答的 SSE 载荷构造。
 *
 * <p>错误载荷刻意只带错误码、描述与用户提示三项，与 {@code CommonResult} 的前三个字段同构，
 * 让前端可以用同一段逻辑处理普通响应与流内错误（流内没有业务数据，故不含 {@code data}）。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
public final class RagSsePayloads {

    private RagSsePayloads() {
    }

    /**
     * 构造结束载荷。
     *
     * @param answer 完整答案
     * @param mode   检索模式
     * @return 结束事件的数据
     */
    public static Map<String, Object> done(String answer, String mode) {
        return Map.of(
                "answer", answer == null ? "" : answer,
                "mode", mode);
    }

    /**
     * 构造错误载荷。
     *
     * @param code    错误码
     * @param message 错误描述
     * @param userTip 用户提示
     * @return 错误事件的数据
     */
    public static Map<String, Object> error(String code, String message, String userTip) {
        return Map.of(
                "code", code,
                "message", message == null ? "" : message,
                "userTip", userTip == null ? "" : userTip);
    }

    /**
     * 构造参考片段载荷。
     *
     * @param references 参考片段
     * @return 参考事件的数据
     */
    public static List<String> references(List<String> references) {
        return references == null ? List.of() : List.copyOf(references);
    }
}
