package com.badminton.mes.module.rag.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.badminton.mes.common.security.AuthInterceptor;
import com.badminton.mes.module.rag.client.dto.RagGraphResult;
import com.badminton.mes.module.rag.service.RagQueryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 图谱 Controller Web 切片测试。
 *
 * <p>{@code maxDepth} 的上限必须验证：图谱展开是整棵子树，深度放大一级
 * 节点量可能翻数倍，没有上限时一个请求就能把服务打满。
 */
@WebMvcTest(RagGraphController.class)
class RagGraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagQueryService ragQueryService;

    @MockitoBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void permitAllRequests() throws Exception {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("标签列表：返回字符串数组")
    void listLabelsReturnsArray() throws Exception {
        when(ragQueryService.listGraphLabels()).thenReturn(List.of("设备", "工序"));

        mockMvc.perform(get("/api/rag/graph/labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data[0]").value("设备"));
    }

    @Test
    @DisplayName("子图查询：节点与边被转换到响应 VO")
    void getGraphMapsNodesAndEdges() throws Exception {
        when(ragQueryService.getGraph("设备", 2)).thenReturn(new RagGraphResult(
                List.of(new RagGraphResult.Node("n1", "注塑机", "Equipment")),
                List.of(new RagGraphResult.Edge("n1", "n2", "位于", 0.9))));

        mockMvc.perform(get("/api/rag/graph").param("label", "设备"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].id").value("n1"))
                .andExpect(jsonPath("$.data.nodes[0].label").value("注塑机"))
                .andExpect(jsonPath("$.data.edges[0].description").value("位于"))
                .andExpect(jsonPath("$.data.edges[0].weight").value(0.9));
    }

    @Test
    @DisplayName("子图查询：缺少 label 返回 A0400")
    void getGraphRejectsMissingLabel() throws Exception {
        mockMvc.perform(get("/api/rag/graph"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));

        verify(ragQueryService, never()).getGraph(any(), anyInt());
    }

    @Test
    @DisplayName("子图查询：展开深度超过上限返回 A0400")
    void getGraphRejectsExcessiveDepth() throws Exception {
        mockMvc.perform(get("/api/rag/graph").param("label", "设备").param("maxDepth", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));

        verify(ragQueryService, never()).getGraph(eq("设备"), anyInt());
    }
}
