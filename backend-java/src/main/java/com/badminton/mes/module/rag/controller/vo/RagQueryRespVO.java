package com.badminton.mes.module.rag.controller.vo;

import java.util.List;

import com.badminton.mes.module.rag.client.dto.RagQueryResult;

/**
 * 同步问答响应。
 *
 * @param answer   模型回答正文
 * @param contexts 检索命中的上下文片段
 * @param mode     本次使用的检索模式
 * @author 张竹灏
 * @date 2026/11/08
 */
public record RagQueryRespVO(String answer, List<String> contexts, String mode) {

    /**
     * 从客户端结果转换。
     *
     * @param result 客户端返回的问答结果
     * @return 响应对象
     */
    public static RagQueryRespVO from(RagQueryResult result) {
        return new RagQueryRespVO(result.answer(), result.contexts(), result.mode());
    }
}
