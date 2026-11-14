package com.badminton.mes.module.rag.client;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.rag.client.dto.RagDocumentDTO;
import com.badminton.mes.module.rag.client.dto.RagDocumentContent;
import com.badminton.mes.module.rag.client.dto.RagGraphResult;
import com.badminton.mes.module.rag.client.dto.RagQueryResult;
import com.badminton.mes.module.rag.client.dto.RagHistoryMessage;
import com.badminton.mes.module.rag.config.RagProperties;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import com.badminton.mes.module.rag.enums.RagQueryMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * LightRAG 上游服务的同步客户端。
 *
 * <p>本类只做两件事：
 * <ol>
 *   <li>把租户标识拼进 {@code /tenant/{tenantId}} 前缀，并统一注入内部密钥；</li>
 *   <li>把上游错误与网络异常统一翻译成带模块错误码的业务异常。</li>
 * </ol>
 * 响应字段适配交给 {@link RagResponseParser}，状态归一化交给 {@code RagDocumentStatus}。
 *
 * <p><b>两道防线</b>：租户标识由 {@code RagTenantResolver} 从登录上下文决定；
 * 本类在发请求前再按白名单校验一次格式，防止配置或调用错误造成路径注入。
 *
 * <p>上游地址、响应报文与内部密钥<b>不进入任何异常信息</b>，只写日志。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
@Component
public class RagClient {

    /** 记录上游调用的真实原因供排查；异常信息不引用这些细节。 */
    private static final Logger logger = LoggerFactory.getLogger(RagClient.class);

    /** 单次列表请求的默认页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 200;

    /** 已完成配置的 HTTP 客户端。 */
    private final RestClient restClient;

    /** 上游配置。 */
    private final RagProperties properties;

    /**
     * 构造器注入，按配置建立连接与读取超时。
     *
     * @param properties 上游配置
     */
    @Autowired
    public RagClient(RagProperties properties) {
        this(properties, RagRestClientFactory.build(properties, properties.getReadTimeout()));
    }

    /**
     * 包级构造器：允许测试注入已配置好的 RestClient。
     *
     * <p>{@code MockRestServiceServer} 必须绑定到**实际使用**的那个 RestClient，
     * 因此测试通过本构造器传入自己构建的实例；生产代码走上面的公开构造器。
     *
     * @param properties 上游配置
     * @param restClient 已配置的 HTTP 客户端
     */
    RagClient(RagProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /**
     * 上传一段文本作为文档。
     *
     * <p>字段名必须与上游 {@code InsertTextRequest} 一致：上传名走 {@code file_source}，
     * 它是上游的<b>文档身份</b>（参与去重与 doc_id 生成），不是展示用描述。
     *
     * @param tenantId   租户标识，由 {@code RagTenantResolver} 决定
     * @param fileSource 文档来源标识，同名文档会被上游判为冲突
     * @param content    文本内容
     * @return 上游返回的任务追踪号，用于查询处理进度，<b>不是文档主键</b>
     * @throws ServiceException 租户标识非法、内容超限或上游失败
     */
    public String uploadText(String tenantId, String fileSource, String content) {
        String safeTenant = RagTenantGuard.requireValid(tenantId);
        String safeContent = content == null ? "" : content;
        if (safeContent.getBytes(StandardCharsets.UTF_8).length > properties.getMaxUploadSize()) {
            throw new ServiceException(RagErrorCodeConstants.UPLOAD_TOO_LARGE);
        }
        Map<String, Object> response = execute(() -> restClient.post()
                .uri(RagTenantGuard.tenantUri(properties, safeTenant, "documents/text"))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.set("X-API-Key", RagTenantGuard.apiKey(properties)))
                .body(Map.of("text", safeContent,
                        "file_source", fileSource == null ? "" : fileSource))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
        return RagResponseParser.stringValue(response, "track_id");
    }

    /**
     * 列出该租户的文档。
     *
     * <p>上游该接口是 POST + JSON 请求体，分页参数不在查询串里。
     *
     * @param tenantId 租户标识
     * @return 文档列表，状态已归一化
     * @throws ServiceException 租户标识非法或上游失败
     */
    public List<RagDocumentDTO> listDocuments(String tenantId) {
        String safeTenant = RagTenantGuard.requireValid(tenantId);
        Map<String, Object> response = execute(() -> restClient.post()
                .uri(RagTenantGuard.tenantUri(properties, safeTenant, "documents/paginated"))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.set("X-API-Key", RagTenantGuard.apiKey(properties)))
                .body(Map.of(
                        "page", 1,
                        "page_size", DEFAULT_PAGE_SIZE,
                        "sort_field", "updated_at",
                        "sort_direction", "desc"))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
        return RagResponseParser.parseDocuments(response);
    }

    /** Read stored text from a tenant-bound route without exposing internal credentials. */
    public RagDocumentContent readDocument(String tenantId, String docId) {
        String safeTenant = RagTenantGuard.requireValid(tenantId);
        if (docId == null || !docId.matches("[A-Za-z0-9_-]{1,200}")) {
            throw new ServiceException(RagErrorCodeConstants.REQUEST_REJECTED);
        }
        return execute(() -> restClient.get()
                .uri(RagTenantGuard.tenantUri(properties, safeTenant, "documents/" + docId + "/content"))
                .headers(headers -> headers.set("X-API-Key", RagTenantGuard.apiKey(properties)))
                .retrieve().body(RagDocumentContent.class));
    }

    /**
     * 删除指定文档。
     *
     * <p>上游即使返回 200 也不代表删除已执行：流水线忙碌时 body 里的
     * {@code status} 是 {@code busy}，必须按响应体判定，不能只看状态码。
     *
     * @param tenantId 租户标识
     * @param docId    文档标识
     * @throws ServiceException 租户标识非法、上游拒绝执行或上游失败
     */
    public void deleteDocument(String tenantId, String docId) {
        String safeTenant = RagTenantGuard.requireValid(tenantId);
        Map<String, Object> payload = Map.of("doc_ids", List.of(docId));
        Map<String, Object> response = execute(() -> restClient.method(HttpMethod.DELETE)
                .uri(RagTenantGuard.tenantUri(properties, safeTenant, "documents/delete_document"))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.set("X-API-Key", RagTenantGuard.apiKey(properties)))
                .body(payload)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
        String status = RagResponseParser.stringValue(response, "status");
        if (!"deletion_started".equals(status)) {
            logger.warn("RAG upstream refused deletion, status: {}", status);
            throw new ServiceException(RagErrorCodeConstants.UPSTREAM_BUSY);
        }
    }

    /**
     * 重新处理当前租户下所有失败、待处理或中断的文档。
     *
     * <p>上游该接口<b>不接收文档级过滤</b>：它一次性把整个 workspace 里
     * FAILED / PENDING / 中断的记录重新入队。因此本方法无法「只重建某一个文档」——
     * 若需要文档级重建，只能先删除再按原文件名重新上传。
     *
     * @param tenantId 租户标识
     * @throws ServiceException 租户标识非法、上游拒绝执行或上游失败
     */
    public void reprocessFailedDocuments(String tenantId) {
        String safeTenant = RagTenantGuard.requireValid(tenantId);
        Map<String, Object> response = execute(() -> restClient.post()
                .uri(RagTenantGuard.tenantUri(properties, safeTenant, "documents/reprocess_failed"))
                .headers(headers -> headers.set("X-API-Key", RagTenantGuard.apiKey(properties)))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
        String status = RagResponseParser.stringValue(response, "status");
        if (status != null && status.contains("rejected")) {
            logger.warn("RAG upstream refused reprocessing, status: {}", status);
            throw new ServiceException(RagErrorCodeConstants.UPSTREAM_BUSY);
        }
    }

    /**
     * 发起一次问答。
     *
     * @param tenantId 租户标识
     * @param question 问题
     * @param mode     检索模式：naive / local / global / hybrid / mix
     * @return 回答与检索上下文
     * @throws ServiceException 租户标识非法、模式不支持或上游失败
     */
    public RagQueryResult query(String tenantId, String question, String mode) {
        return query(tenantId, question, mode, List.of());
    }

    /** 发起携带服务端历史的问答。 */
    public RagQueryResult query(String tenantId, String question, String mode, List<RagHistoryMessage> history) {
        String safeTenant = RagTenantGuard.requireValid(tenantId);
        // 模式白名单只在 RagQueryMode 维护一份，这里直接解析，非法取值在发请求前抛错
        String safeMode = RagQueryMode.parse(mode).getValue();
        Map<String, Object> response = execute(() -> restClient.post()
                .uri(RagTenantGuard.tenantUri(properties, safeTenant, "query"))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.set("X-API-Key", RagTenantGuard.apiKey(properties)))
                .body(queryPayload(question, safeMode, history))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
        return new RagQueryResult(
                RagResponseParser.stringValue(response, "response", "answer"),
                RagResponseParser.parseContexts(response),
                safeMode, RagResponseParser.parseReferences(response));
    }

    static Map<String, Object> queryPayload(String question, String mode, List<RagHistoryMessage> history) {
        Map<String, Object> payload = new LinkedHashMap<>(Map.of(
                        "query", question == null ? "" : question,
                        "mode", mode,
                        "include_references", true,
                        // 不打开这个开关，上游 references 里只有文件路径，没有片段正文
                        "include_chunk_content", true));
        if (history != null && !history.isEmpty()) {
            payload.put("conversation_history", history);
        }
        return payload;
    }

    /**
     * 列出图谱中的标签，供前端筛选。
     *
     * @param tenantId 租户标识
     * @return 标签列表
     * @throws ServiceException 租户标识非法或上游失败
     */
    public List<String> listGraphLabels(String tenantId) {
        String safeTenant = RagTenantGuard.requireValid(tenantId);
        List<String> labels = execute(() -> restClient.get()
                .uri(RagTenantGuard.tenantUri(properties, safeTenant, "graph/label/list"))
                .headers(headers -> headers.set("X-API-Key", RagTenantGuard.apiKey(properties)))
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {
                }));
        return labels == null ? List.of() : List.copyOf(labels);
    }

    /**
     * 按标签查询子图。
     *
     * @param tenantId 租户标识
     * @param label    实体标签
     * @param maxDepth 最大展开深度
     * @return 节点与边
     * @throws ServiceException 租户标识非法或上游失败
     */
    public RagGraphResult getGraph(String tenantId, String label, int maxDepth) {
        String safeTenant = RagTenantGuard.requireValid(tenantId);
        Map<String, Object> response = execute(() -> restClient.get()
                // 用 uriBuilder 让框架统一编码：手工拼串再预编码会遇到 % 与 + 的二次编码问题
                .uri(RagTenantGuard.tenantUri(properties, safeTenant, "graphs"), uriBuilder -> uriBuilder
                        .queryParam("label", label == null ? "" : label)
                        .queryParam("max_depth", maxDepth)
                        .build())
                .headers(headers -> headers.set("X-API-Key", RagTenantGuard.apiKey(properties)))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
        return RagResponseParser.parseGraph(response);
    }

    /**
     * 执行一次上游调用，并把所有失败翻译为业务异常。
     *
     * <p>上游地址、响应报文与密钥只写日志，不进入异常信息与 userTip。
     *
     * <p>4xx 按语义分流：其中的 400/409/422 绝大多数是用户输入问题（同名冲突、
     * 问题过短、来源非法），归为 A 类让前端能给出可操作提示；只有 5xx 与
     * 无法归类的状态才算第三方故障。全部归 C 类会让用户输入错误显示成
     * 「知识库处理失败」，运维也会被误导去查上游。
     *
     * @param call 实际的调用
     * @param <T>  返回类型
     * @return 调用结果
     * @throws ServiceException 上游不可达或返回错误
     */
    private <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException error) {
            // 连接失败与读写超时都归入不可达：对调用方处理方式一致
            logger.error("RAG upstream unreachable", error);
            throw new ServiceException(RagErrorCodeConstants.UPSTREAM_UNAVAILABLE);
        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();
            logger.error("RAG upstream returned status {}: {}",
                    status, error.getResponseBodyAsString());
            throw translateStatus(status);
        } catch (RestClientException error) {
            // 响应体反序列化失败、未知 Content-Type 等：属于上游协议问题，不应冒泡成系统 500
            logger.error("RAG upstream call failed", error);
            throw new ServiceException(RagErrorCodeConstants.UPSTREAM_ERROR);
        }
    }

    /**
     * 把上游 HTTP 状态码翻译为模块错误码。
     *
     * @param status 上游状态码
     * @return 对应的业务异常，由调用方直接抛出
     */
    private ServiceException translateStatus(int status) {
        return switch (status) {
            case 404 -> new ServiceException(RagErrorCodeConstants.DOCUMENT_NOT_FOUND);
            // 同名文档冲突：上游用 409 明确表达了「已存在」，不是故障
            case 409 -> new ServiceException(RagErrorCodeConstants.DOCUMENT_CONFLICT);
            // 入参不符合上游约束：问题过短、来源非法、分页参数越界等
            case 400, 422 -> new ServiceException(RagErrorCodeConstants.REQUEST_REJECTED);
            // 上游限流
            case 429 -> new ServiceException(RagErrorCodeConstants.UPSTREAM_BUSY);
            // 其余（含 5xx 与未预期状态）按第三方故障处理
            default -> new ServiceException(RagErrorCodeConstants.UPSTREAM_ERROR,
                    "知识库服务返回状态 " + status);
        };
    }
}
