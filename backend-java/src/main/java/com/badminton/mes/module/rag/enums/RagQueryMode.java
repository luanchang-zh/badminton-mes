package com.badminton.mes.module.rag.enums;

import java.util.Locale;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;

/**
 * 知识库问答的检索模式，取值范围与上游 LightRAG 一致。
 *
 * <p>取值白名单只在这里维护一份：Controller 的请求 VO 不做正则校验，
 * 客户端也不再自带集合，非法取值统一由 {@link #parse(String)} 抛出 A0400。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
public enum RagQueryMode {

    /** 纯向量相似度检索 */
    NAIVE("naive"),

    /** 以实体为中心的局部检索 */
    LOCAL("local"),

    /** 全局主题式检索 */
    GLOBAL("global"),

    /** 局部与全局结合 */
    HYBRID("hybrid"),

    /** 图谱加向量的一体化检索，效果通常最好 */
    MIX("mix");

    /** 缺省模式：前端未指定时使用 */
    public static final RagQueryMode DEFAULT_MODE = NAIVE;

    /** 传给上游的取值 */
    private final String value;

    RagQueryMode(String value) {
        this.value = value;
    }

    /**
     * 传给上游的取值。
     *
     * @return 小写模式名
     */
    public String getValue() {
        return value;
    }

    /**
     * 解析外部传入的模式字符串，做默认值与格式归一化。
     *
     * @param rawMode 原始模式，可为 null 或空白
     * @return 检索模式，未指定时为 {@link #DEFAULT_MODE}
     * @throws ServiceException 取值不在白名单内
     */
    public static RagQueryMode parse(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return DEFAULT_MODE;
        }
        String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
        for (RagQueryMode mode : values()) {
            if (mode.value.equals(normalized)) {
                return mode;
            }
        }
        throw new ServiceException(RagErrorCodeConstants.QUERY_MODE_INVALID);
    }
}
