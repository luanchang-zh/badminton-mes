package com.badminton.mes.module.rag.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        return extractReferenceTexts(references);
    }

    /**
     * 从参考条目列表中抽取纯文本。
     *
     * <p>同步响应的 {@code references} 与流式响应的首行 references 结构一致，
     * 因此两条路径共用本方法，避免各写一套字段适配。
     *
     * @param references 参考条目列表
     * @return 非空文本片段，保持原顺序
     */
    static List<String> extractReferenceTexts(List<?> references) {
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
            return new RagGraphResult(nodes, edges, false);
        }
        collectNodes(response.get("nodes"), nodes);
        collectEdges(response.get("edges"), edges);
        // 上游按 max_nodes 截断时会置 is_truncated，必须透出，否则前端以为图谱本来就这么小
        return new RagGraphResult(nodes, edges, Boolean.TRUE.equals(response.get("is_truncated")));
    }

    /**
     * 从参考条目中取出正文。
     *
     * <p>上游的 {@code content} 是<b>字符串数组</b>（同一文件的多个切片），
     * 且只有在请求里带 {@code include_chunk_content=true} 时才存在。
     * 早期实现按字符串读取，导致引用永远为空，这里按数组拼接。
     *
     * @param reference 单条参考，可能是字符串或映射
     * @return 正文，同一文件的多个切片按换行拼接；取不到时为 null
     */
    private static String referenceText(Object reference) {
        if (reference instanceof String text) {
            return text;
        }
        if (reference instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) map;
            Object content = source.get("content");
            if (content instanceof List<?> chunks) {
                String joined = chunks.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .filter(chunk -> !chunk.isBlank())
                        .collect(Collectors.joining("\n"));
                if (!joined.isBlank()) {
                    return joined;
                }
            }
            return stringValue(source, "content", "text", "snippet");
        }
        return null;
    }

    /**
     * 收集图谱节点。
     *
     * <p>上游节点结构是 {@code {id, labels: [...], properties: {entity_type, description}}}
     * （见 {@code lightrag/types.py} 的 {@code KnowledgeGraphNode}）。
     * 实体名取 {@code labels[0]}，实体类型在 {@code properties} 里，都不在顶层。
     *
     * @param rawNodes 上游节点数据
     * @param target   收集目标
     */
    private static void collectNodes(Object rawNodes, List<RagGraphResult.Node> target) {
        if (!(rawNodes instanceof List<?> nodes)) {
            return;
        }
        for (Object node : nodes) {
            if (!(node instanceof Map<?, ?> map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) map;
            Map<String, Object> properties = asMap(source.get("properties"));
            target.add(new RagGraphResult.Node(
                    stringValue(source, "id"),
                    firstLabel(source.get("labels"), stringValue(properties, "entity_id")),
                    stringValue(properties, "entity_type", "type")));
        }
    }

    /**
     * 收集图谱边。
     *
     * <p>边结构是 {@code {id, type, source, target, properties: {weight, description}}}：
     * 权重与关系描述都在 {@code properties} 里。
     *
     * @param rawEdges 上游边数据
     * @param target   收集目标
     */
    private static void collectEdges(Object rawEdges, List<RagGraphResult.Edge> target) {
        if (!(rawEdges instanceof List<?> edges)) {
            return;
        }
        for (Object edge : edges) {
            if (!(edge instanceof Map<?, ?> map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) map;
            Map<String, Object> properties = asMap(source.get("properties"));
            Object weight = properties.get("weight");
            target.add(new RagGraphResult.Edge(
                    stringValue(source, "source", "source_id"),
                    stringValue(source, "target", "target_id"),
                    stringValue(properties, "description", "keywords"),
                    weight instanceof Number number ? number.doubleValue() : null));
        }
    }

    /**
     * 取 {@code labels} 数组的第一个元素作为实体名。
     *
     * @param rawLabels  上游 labels 字段
     * @param fallback   取不到时的兜底值
     * @return 实体名
     */
    private static String firstLabel(Object rawLabels, String fallback) {
        if (rawLabels instanceof List<?> labels) {
            for (Object label : labels) {
                if (label instanceof String text && !text.isBlank()) {
                    return text;
                }
            }
        }
        return fallback;
    }

    /**
     * 把嵌套对象转成映射，非映射结构返回空映射。
     *
     * @param raw 原始值
     * @return 映射，永不为 null
     */
    private static Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) map;
            return source;
        }
        return Map.of();
    }
}
