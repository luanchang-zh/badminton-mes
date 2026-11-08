package com.badminton.mes.module.rag.client.dto;

import java.util.List;

/**
 * 知识图谱的一个子图，供前端可视化。
 *
 * <p>节点与边都用 record 承载最小字段；上游字段更多，但 P4 只暴露可视化需要的部分，
 * 避免把上游 DTO 直接泄漏到上层。
 *
 * @param nodes 节点列表
 * @param edges 边列表
 * @author 张竹灏
 * @date 2026/11/08
 */
public record RagGraphResult(List<Node> nodes, List<Edge> edges) {

    /**
     * 紧凑构造器：null 归一化为空列表。
     */
    public RagGraphResult {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    /**
     * 图谱节点。
     *
     * @param id    节点标识
     * @param label 节点名称
     * @param type  实体类型
     */
    public record Node(String id, String label, String type) {
    }

    /**
     * 图谱边。
     *
     * @param source       起点节点标识
     * @param target       终点节点标识
     * @param description  关系描述
     * @param weight       关系权重
     */
    public record Edge(String source, String target, String description, Double weight) {
    }
}
