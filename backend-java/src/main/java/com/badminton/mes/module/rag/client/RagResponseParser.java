package com.badminton.mes.module.rag.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.badminton.mes.module.rag.client.dto.RagDocumentDTO;
import com.badminton.mes.module.rag.client.dto.RagGraphResult;
import com.badminton.mes.module.rag.enums.RagDocumentStatus;

/**
 * 上游响应的解析工具。
 *
 * <p>上游不同接口对同一语义使用不同字段名（{@code track_id} / {@code doc_id}、
 * {@code items} / {@code documents} 等），这类适配集中在这里，
 * 让 {@link RagClient} 只保留「发请求 + 转错误」两件事。
 *
 * <p>解析过程对缺字段保持宽容：取不到就返回 null 或空集合，不抛异常——
 * 上游字段变化不应让本系统 500。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
final class RagResponseParser {

    private RagResponseParser() {
    }

    /**
     * 从多种可能的字段名中取出第一个非空字符串值。
     *
     * @param source 响应映射，可能为 null
     * @param keys   候选字段名，按优先级排列
     * @return 第一个非空字符串，全部缺失时为 null
     */
    static String stringValue(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
            if (value instanceof Number number) {
                return String.valueOf(number);
            }
        }
        return null;
    }

    /**
     * 解析文档列表响应。
     *
     * @param response 上游响应，可能为 null
     * @return 文档列表，状态已归一化
     */
    static List<RagDocumentDTO> parseDocuments(Map<String, Object> response) {
        Object rawItems = response == null ? null : response.get("items");
        if (!(rawItems instanceof List<?>)) {
            rawItems = response == null ? null : response.get("documents");
        }
        if (!(rawItems instanceof List<?> items)) {
            return List.of();
        }

        List<RagDocumentDTO> documents = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) map;
            String rawStatus = stringValue(source, "status");
            documents.add(new RagDocumentDTO(
                    stringValue(source, "id", "doc_id"),
                    stringValue(source, "file_path", "file_name"),
                    rawStatus,
                    RagDocumentStatus.fromUpstream(rawStatus)));
        }
        return documents;
    }

    /**
     * 解析问答响应中的参考上下文。
     *
     * @param response 上游响应，可能为 null
     * @return 上下文片段列表，保持上游顺序
     */
    static List<String> parseContexts(Map<String, Object> response) {
        Object rawReferences = response == null ? null : response.get("references");
        if (!(rawReferences instanceof List<?> references)) {
            return List.of();
        }
        List<String> contexts = new ArrayList<>(references.size());
        for (Object reference : references) {
            String text = referenceText(reference);
            if (text != null && !text.isBlank()) {
                contexts.add(text);
            }
        }
        return contexts;
    }

    /**
     * 解析图谱子图响应。
     *
     * @param response 上游响应，可能为 null
     * @return 节点与边
     */
    static RagGraphResult parseGraph(Map<String, Object> response) {
        List<RagGraphResult.Node> nodes = new ArrayList<>();
        List<RagGraphResult.Edge> edges = new ArrayList<>();
        if (response == null) {
            return new RagGraphResult(nodes, edges);
        }
        collectNodes(response.get("nodes"), nodes);
        collectEdges(response.get("edges"), edges);
        return new RagGraphResult(nodes, edges);
    }

    /**
     * 对查询参数做 URL 编码。
     *
     * @param value 原始值
     * @return 编码后的值，null 转为空串
     */
    static String urlEncode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 从参考条目中取出正文。
     *
     * @param reference 单条参考，可能是字符串或映射
     * @return 正文，取不到时为 null
     */
    private static String referenceText(Object reference) {
        if (reference instanceof String text) {
            return text;
        }
        if (reference instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) map;
            return stringValue(source, "content", "text", "snippet");
        }
        return null;
    }

    /**
     * 收集图谱节点。
     *
     * @param rawNodes 上游节点数据
     * @param target   收集目标
     */
    private static void collectNodes(Object rawNodes, List<RagGraphResult.Node> target) {
        if (!(rawNodes instanceof List<?> nodes)) {
            return;
        }
        for (Object node : nodes) {
            if (node instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) map;
                target.add(new RagGraphResult.Node(
                        stringValue(source, "id", "entity_id"),
                        stringValue(source, "label", "entity_name", "name"),
                        stringValue(source, "type", "entity_type")));
            }
        }
    }

    /**
     * 收集图谱边。
     *
     * @param rawEdges 上游边数据
     * @param target   收集目标
     */
    private static void collectEdges(Object rawEdges, List<RagGraphResult.Edge> target) {
        if (!(rawEdges instanceof List<?> edges)) {
            return;
        }
        for (Object edge : edges) {
            if (edge instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) map;
                Object weight = source.get("weight");
                target.add(new RagGraphResult.Edge(
                        stringValue(source, "source", "source_id"),
                        stringValue(source, "target", "target_id"),
                        stringValue(source, "description", "label"),
                        weight instanceof Number number ? number.doubleValue() : null));
            }
        }
    }
}
