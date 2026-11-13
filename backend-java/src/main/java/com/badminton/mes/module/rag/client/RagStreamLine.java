package com.badminton.mes.module.rag.client;

import java.util.List;
import com.badminton.mes.module.rag.client.dto.RagReference;

/**
 * 上游流式问答输出的一行 NDJSON 解析结果。
 *
 * <p>上游在同一路流里交替推送参考片段、正文分片、进度与错误，字段形态各不相同。
 * 解析成统一的 {@link Kind} 后，上层只需按种类分支，不必自己判断「这一行到底算什么」。
 *
 * <p><b>为什么允许同时有 references 与 content</b>：上游在命中 LLM 缓存时会把
 * {@code {references, response}} 放在同一行。若坚持「一行一种类型」，
 * 必然丢掉其中一项——而高频问题几乎必然命中缓存，等于引用功能最需要时失效。
 *
 * @param kind       行类型
 * @param content    正文分片，仅 {@link Kind#CONTENT} 有值
 * @param references 参考片段，仅 {@link Kind#REFERENCES} 有值
 * @param error      上游错误描述，仅 {@link Kind#ERROR} 有值
 * @author 张竹灏
 * @date 2026/11/08
 */
public record RagStreamLine(Kind kind, String content, List<String> references, String error,
                            List<RagReference> referenceDetails) {

    public RagStreamLine(Kind kind, String content, List<String> references, String error) {
        this(kind, content, references, error, List.of());
    }

    /** 紧凑构造器：列表字段归一化，避免调用方判空 */
    public RagStreamLine {
        references = references == null ? List.of() : List.copyOf(references);
        referenceDetails = referenceDetails == null ? List.of() : List.copyOf(referenceDetails);
    }

    /**
     * 构造正文分片。
     *
     * @param content 分片内容
     * @return 流式行
     */
    public static RagStreamLine content(String content) {
        return new RagStreamLine(Kind.CONTENT, content, null, null);
    }

    /**
     * 构造参考片段行。
     *
     * @param references 参考片段
     * @return 流式行
     */
    public static RagStreamLine references(List<String> references) {
        return new RagStreamLine(Kind.REFERENCES, null, references, null);
    }

    /**
     * 构造「参考片段 + 正文」合并行，用于上游命中缓存时的单行完整响应。
     *
     * @param references 参考片段
     * @param content    完整正文
     * @return 流式行
     */
    public static RagStreamLine referencesAndContent(List<String> references, String content) {
        return new RagStreamLine(Kind.CONTENT, content, references, null);
    }

    /**
     * 构造错误行。
     *
     * @param error 上游错误描述
     * @return 流式行
     */
    public static RagStreamLine error(String error) {
        return new RagStreamLine(Kind.ERROR, null, null, error);
    }

    /**
     * 构造空行或无法识别的行。
     *
     * @return 流式行
     */
    public static RagStreamLine ignored() {
        return new RagStreamLine(Kind.IGNORED, null, null, null);
    }

    /**
     * 构造进度行。
     *
     * @return 流式行
     */
    public static RagStreamLine progress() {
        return new RagStreamLine(Kind.PROGRESS, null, null, null);
    }

    /** 上游流式输出的行类型 */
    public enum Kind {

        /** 参考片段，通常出现在第一行 */
        REFERENCES,

        /** 正文分片 */
        CONTENT,

        /** 上游处理失败 */
        ERROR,

        /** 检索进度提示，本系统不透传给前端 */
        PROGRESS,

        /** 未识别或空行，调用方应忽略 */
        IGNORED
    }
}
