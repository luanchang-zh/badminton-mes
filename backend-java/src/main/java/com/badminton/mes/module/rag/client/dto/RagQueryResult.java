package com.badminton.mes.module.rag.client.dto;

import java.util.List;

/**
 * 一次问答的结果。
 *
 * <p>{@code contexts} 保留检索命中的上下文片段，与 {@code answer} 分开存放：
 * 口令类信息可能只出现在上下文而没进入最终答案，两者都要能被调用方检查。
 *
 * @param answer   模型回答正文
 * @param contexts 检索命中的上下文片段
 * @param mode     本次使用的检索模式
 * @author 张竹灏
 * @date 2026/11/08
 */
public record RagQueryResult(
        String answer,
        List<String> contexts,
        String mode,
        List<RagReference> references) {

    public RagQueryResult(String answer, List<String> contexts, String mode) {
        this(answer, contexts, mode, List.of());
    }

    /**
     * 紧凑构造器：把 null 列表归一化为空列表，避免调用方到处判空。
     */
    public RagQueryResult {
        contexts = contexts == null ? List.of() : List.copyOf(contexts);
        references = references == null ? List.of() : List.copyOf(references);
    }
}
