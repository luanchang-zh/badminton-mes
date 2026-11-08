package com.badminton.mes.module.rag.client;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.rag.client.dto.RagDocumentDTO;
import com.badminton.mes.module.rag.client.dto.RagGraphResult;
import com.badminton.mes.module.rag.client.dto.RagQueryResult;
import com.badminton.mes.module.rag.config.RagProperties;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    /** 租户标识白名单：只允许字母、数字与下划线，与 LightRAG 的 workspace 命名约束一致。 */
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    /** 上游查询接口允许的检索模式。 */
    private static final Set<String> SUPPORTED_QUERY_MODES =
            Set.of("naive", "local", "global", "hybrid", "mix");

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
    public RagClient(RagProperties properties) {
        this(properties, defaultRestClient(properties));
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
     * 按配置构建默认 HTTP 客户端。
     *
     * @param properties 上游配置
     * @return 带超时配置的客户端
     */
    private static RestClient defaultRestClient(RagProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 上传一段文本作为文档。
     *
     * @param tenantId 租户标识，由 {@code RagTenantResolver} 决定
     * @param fileName 文件名
     * @param content  文本内容
     * @return 上游返回的文档标识
     * @throws ServiceException 租户标识非法、内容超限或上游失败
     */
    public String uploadText(String tenantId, String fileName, String content) {
        String safeTenant = requireValidTenant(tenantId);
        String safeContent = content == null ? "" : content;
        if (safeContent.getBytes(StandardCharsets.UTF_8).length > properties.getMaxUploadSize()) {
            throw new ServiceException(RagErrorCodeConstants.UPLOAD_TOO_LARGE);
        }
        Map<String, Object> response = execute(() -> restClient.post()
                .uri(tenantUri(safeTenant, "documents/text"))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.set("X-API-Key", apiKey()))
                .body(Map.of("text", safeContent,
                        "description", fileName == null ? "" : fileName))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
        return RagResponseParser.stringValue(response, "track_id", "doc_id");
    }

    /**
     * 列出该租户的文档。
     *
     * @param tenantId 租户标识
     * @return 文档列表，状态已归一化
     * @throws ServiceException 租户标识非法或上游失败
     */
    public List<RagDocumentDTO> listDocuments(String tenantId) {
        String safeTenant = requireValidTenant(tenantId);
        Map<String, Object> response = execute(() -> restClient.get()
                .uri(tenantUri(safeTenant, "documents/paginated")
                        + "?page=1&page_size=" + DEFAULT_PAGE_SIZE)
                .headers(headers -> headers.set("X-API-Key", apiKey()))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
        return RagResponseParser.parseDocuments(response);
    }

    /**
     * 删除指定文档。
     *
     * @param tenantId 租户标识
     * @param docId    文档标识
     * @throws ServiceException 租户标识非法或上游失败
     */
    public void deleteDocument(String tenantId, String docId) {
        String safeTenant = requireValidTenant(tenantId);
        Map<String, Object> payload = Map.of("doc_ids", List.of(docId));
        execute(() -> restClient.method(HttpMethod.DELETE)
                .uri(tenantUri(safeTenant, "documents/delete_document"))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.set("X-API-Key", apiKey()))
                .body(payload)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * 重新索引指定文档。
     *
     * <p>内容更新必须走上游的重新处理流程：只替换磁盘文件不会刷新向量与图谱。
     *
     * @param tenantId 租户标识
     * @param docIds   需要重新处理的文档标识
     * @throws ServiceException 租户标识非法或上游失败
     */
    public void reindex(String tenantId, List<String> docIds) {
        String safeTenant = requireValidTenant(tenantId);
        execute(() -> restClient.post()
                .uri(tenantUri(safeTenant, "documents/reprocess_failed"))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.set("X-API-Key", apiKey()))
                .body(Map.of("doc_ids", docIds == null ? List.of() : docIds))
                .retrieve()
                .toBodilessEntity());
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
        String safeTenant = requireValidTenant(tenantId);
        String safeMode = mode == null || mode.isBlank() ? "naive" : mode.trim().toLowerCase();
        if (!SUPPORTED_QUERY_MODES.contains(safeMode)) {
            throw new ServiceException(RagErrorCodeConstants.TENANT_ID_INVALID,
                    "不支持的检索模式：" + safeMode);
        }
        Map<String, Object> response = execute(() -> restClient.post()
                .uri(tenantUri(safeTenant, "query"))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.set("X-API-Key", apiKey()))
                .body(Map.of(
                        "query", question == null ? "" : question,
                        "mode", safeMode,
                        "include_references", true))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
        return new RagQueryResult(
                RagResponseParser.stringValue(response, "response", "answer"),
                RagResponseParser.parseContexts(response),
                safeMode);
    }

    /**
     * 列出图谱中的标签，供前端筛选。
     *
     * @param tenantId 租户标识
     * @return 标签列表
     * @throws ServiceException 租户标识非法或上游失败
     */
    public List<String> listGraphLabels(String tenantId) {
        String safeTenant = requireValidTenant(tenantId);
        List<String> labels = execute(() -> restClient.get()
                .uri(tenantUri(safeTenant, "graph/label/list"))
                .headers(headers -> headers.set("X-API-Key", apiKey()))
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
        String safeTenant = requireValidTenant(tenantId);
        Map<String, Object> response = execute(() -> restClient.get()
                .uri(tenantUri(safeTenant, "graphs") + "?label="
                        + RagResponseParser.urlEncode(label)
                        + "&max_depth=" + Math.max(1, maxDepth))
                .headers(headers -> headers.set("X-API-Key", apiKey()))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
        return RagResponseParser.parseGraph(response);
    }

    /**
     * 校验租户标识格式，返回原值便于链式使用。
     *
     * <p>这是第二道防线：即使调用方传错，也不会拼出越界路径。
     *
     * @param tenantId 原始租户标识
     * @return 校验通过的租户标识
     * @throws ServiceException 标识为空或含白名单外字符
     */
    private String requireValidTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()
                || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            logger.warn("Rejected invalid tenant id for upstream call");
            throw new ServiceException(RagErrorCodeConstants.TENANT_ID_INVALID);
        }
        return tenantId;
    }

    /**
     * 拼接租户路由地址。
     *
     * @param tenantId 已校验的租户标识
     * @param path     相对路径
     * @return 完整地址
     */
    private String tenantUri(String tenantId, String path) {
        String base = properties.getBaseUrl() == null
                ? "" : properties.getBaseUrl().replaceAll("/+$", "");
        return base + "/tenant/" + tenantId + "/" + path;
    }

    /**
     * 读取内部密钥；未配置时返回空串，由上游决定是否拒绝。
     *
     * @return 内部密钥
     */
    private String apiKey() {
        return properties.getInternalApiKey() == null ? "" : properties.getInternalApiKey();
    }

    /**
     * 执行一次上游调用，并把所有失败翻译为业务异常。
     *
     * <p>上游地址、响应报文与密钥只写日志，不进入异常信息与 userTip。
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
            logger.error("RAG upstream unreachable: {}", error.getMessage());
            throw new ServiceException(RagErrorCodeConstants.UPSTREAM_UNAVAILABLE);
        } catch (RestClientResponseException error) {
            HttpStatusCode status = error.getStatusCode();
            logger.error("RAG upstream returned status {}: {}",
                    status.value(), error.getResponseBodyAsString());
            throw new ServiceException(RagErrorCodeConstants.UPSTREAM_ERROR,
                    "知识库服务返回状态 " + status.value());
        }
    }
}
