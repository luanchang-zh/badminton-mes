package com.badminton.mes.module.rag.controller.vo;

import java.util.List;

import com.badminton.mes.module.rag.client.dto.RagGraphResult;

/**
 * 图谱子图响应。
 *
 * <p>字段刻意与上游 {@link RagGraphResult} 相同而不复用该类：
 * 上游 DTO 一旦变动（例如新增内部字段）不应直接改变对外契约。
 *
 * @param nodes 节点列表
 * @param edges 边列表
 * @param truncated 上游是否因节点上限截断了子图
 * @author 张竹灏
 * @date 2026/11/08
 */
public record RagGraphRespVO(List<Node> nodes, List<Edge> edges, boolean truncated) {

    /**
     * 从客户端结果转换。
     *
     * @param result 客户端返回的子图
     * @return 响应对象
     */
    public static RagGraphRespVO from(RagGraphResult result) {
        return new RagGraphRespVO(
                result.nodes().stream()
                        .map(node -> new Node(node.id(), node.label(), node.type()))
                        .toList(),
                result.edges().stream()
                        .map(edge -> new Edge(edge.source(), edge.target(),
                                edge.description(), edge.weight()))
                        .toList(),
                result.truncated());
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
     * @param source      起点节点标识
     * @param target      终点节点标识
     * @param description 关系描述
     * @param weight      关系权重
     */
    public record Edge(String source, String target, String description, Double weight) {
    }
}
