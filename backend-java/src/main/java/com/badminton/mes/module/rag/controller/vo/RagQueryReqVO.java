package com.badminton.mes.module.rag.controller.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;

import com.badminton.mes.module.rag.enums.RagQueryMode;

/**
 * 问答请求。
 *
 * <p>{@code mode} 刻意不加 {@code @Pattern}：取值白名单只由 {@link RagQueryMode}
 * 维护一份。加了注解就必然要手抄一份正则，两份一旦漂移，注解会先拦住本来合法的取值，
 * 而单测只能校验副本，挡不住这种漂移。非法取值由 {@link #effectiveMode()} 抛 A0400。
 *
 * @param question 问题
 * @param mode     检索模式，缺省 naive
 * @author 张竹灏
 * @date 2026/11/08
 */
public record RagQueryReqVO(
        @NotBlank(message = "问题不能为空")
        @Size(max = 2000, message = "问题长度不能超过 2000")
        String question,

        String mode,

        @Positive Long conversationId,

        @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestId) {

    public RagQueryReqVO(String question, String mode) {
        this(question, mode, null, null);
    }

    /**
     * 取生效的检索模式，未指定时用缺省模式。
     *
     * @return 检索模式
     */
    public RagQueryMode effectiveMode() {
        return RagQueryMode.parse(mode);
    }
}
