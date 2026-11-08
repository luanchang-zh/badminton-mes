package com.badminton.mes.module.rag.service;

import java.util.List;

import com.badminton.mes.module.rag.client.dto.RagGraphResult;
import com.badminton.mes.module.rag.client.dto.RagQueryResult;
import com.badminton.mes.module.rag.enums.RagQueryMode;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 知识库问答与图谱 Service。
 *
 * <p>与 {@link RagDocumentService} 一样，所有方法都不接收租户参数。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
public interface RagQueryService {

    /**
     * 同步问答。
     *
     * @param question 问题
     * @param mode     检索模式
     * @return 回答与检索上下文
     */
    RagQueryResult query(String question, RagQueryMode mode);

    /**
     * 流式问答，把上游 NDJSON 逐行转成 SSE 事件。
     *
     * <p>方法立即返回，真正的上游调用在独立线程上进行；客户端断开、
     * 超时或出错时都会结束本次流并释放上游连接。
     *
     * @param question 问题
     * @param mode     检索模式
     * @return SSE 发射器，由 Spring 负责把写入的事件推给前端
     */
    SseEmitter streamQuery(String question, RagQueryMode mode);

    /**
     * 列出当前租户的图谱标签。
     *
     * @return 标签列表
     */
    List<String> listGraphLabels();

    /**
     * 按标签查询当前租户的子图。
     *
     * @param label    实体标签
     * @param maxDepth 最大展开深度
     * @return 节点与边
     */
    RagGraphResult getGraph(String label, int maxDepth);
}
