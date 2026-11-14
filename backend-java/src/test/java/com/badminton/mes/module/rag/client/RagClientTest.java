package com.badminton.mes.module.rag.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.rag.client.dto.RagDocumentDTO;
import com.badminton.mes.module.rag.config.RagProperties;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import com.badminton.mes.module.rag.enums.RagDocumentStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 上游客户端测试。
 *
 * <p>三类断言最关键：
 * <ol>
 *   <li>路径带租户前缀、请求头带内部密钥——隔离与鉴权的实际生效方式；</li>
 *   <li>非法租户标识在发请求前被拒绝——用「服务端收到 0 次请求」证明；</li>
 *   <li>上游错误被翻译成本模块错误码——调用方不依赖 HTTP 细节。</li>
 * </ol>
 */
class RagClientTest {

    @Test
    void contentReadsStayUnderTheTenantPrefixAndCarryInternalAuthentication() {
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/documents/doc-a/content"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("X-API-Key", API_KEY))
                .andRespond(withSuccess("{\"docId\":\"doc-a\",\"fileName\":\"sop.md\",\"content\":\"source\"}", MediaType.APPLICATION_JSON));
        assertThat(client.readDocument(TENANT, "doc-a").content()).isEqualTo("source");
        server.verify();
    }

    @Test
    void contentReadsRejectPathInjectionBeforeAnUpstreamRequest() {
        assertThatThrownBy(() -> client.readDocument(TENANT, "../other"))
                .isInstanceOf(ServiceException.class);
        server.verify();
    }

    /** 测试用基础地址 */
    private static final String BASE_URL = "http://rag.test:9621";

    /** 测试用内部密钥 */
    private static final String API_KEY = "test-internal-key";

    /** 测试用租户 */
    private static final String TENANT = "user_1";

    private MockRestServiceServer server;

    private RagClient client;

    @Test
    void historyIsForwardedAndStructuredSourcesHideServerDirectories() {
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/query"))
                .andExpect(content().json("""
                        {"conversation_history":[{"role":"user","content":"点检要求"},
                        {"role":"assistant","content":"先停机"}]}
                        """))
                .andRespond(withSuccess("""
                        {"response":"执行检查 [7]","references":[
                        {"reference_id":"7","file_path":"/private/tenant/sop.md","content":["片段一","片段二"]}]}
                        """, MediaType.APPLICATION_JSON));
        var result = client.query(TENANT, "第二项呢", "mix", List.of(
                new com.badminton.mes.module.rag.client.dto.RagHistoryMessage("user", "点检要求"),
                new com.badminton.mes.module.rag.client.dto.RagHistoryMessage("assistant", "先停机")));
        assertThat(result.references()).singleElement().satisfies(ref -> {
            assertThat(ref.referenceId()).isEqualTo("7");
            assertThat(ref.sourceName()).isEqualTo("sop.md");
            assertThat(ref.chunks()).containsExactly("片段一", "片段二");
        });
        server.verify();
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RagProperties properties = new RagProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setInternalApiKey(API_KEY);
        client = new RagClient(properties, builder.build());
    }

    @Test
    @DisplayName("上传请求带租户前缀、内部密钥，且字段名与上游一致")
    void shouldSendUploadWithTenantPrefixAndApiKey() {
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/documents/text"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", API_KEY))
                // 上游 InsertTextRequest 只认 text / file_source；
                // 字段名写错会被 Pydantic 静默忽略，然后以 400 拒绝，这里必须锁死
                .andExpect(content().json("""
                        {"text": "内容", "file_source": "report.md"}
                        """))
                .andRespond(withSuccess("{\"track_id\":\"doc-1\"}", MediaType.APPLICATION_JSON));

        String trackId = client.uploadText(TENANT, "report.md", "内容");

        assertThat(trackId).isEqualTo("doc-1");
        server.verify();
    }

    @Test
    @DisplayName("列表请求用 POST 且分页参数在请求体里")
    void shouldListDocumentsWithPostBody() {
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/documents/paginated"))
                // 上游该接口是 POST，用 GET 会直接 405
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"page": 1, "page_size": 200, "sort_field": "updated_at",
                         "sort_direction": "desc"}
                        """))
                .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.listDocuments(TENANT)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("非法租户标识在发请求前就被拒绝，服务端收到 0 次请求")
    void shouldRejectInvalidTenantBeforeSendingRequest() {
        // 不设置任何 server.expect：一旦发出请求，MockRestServiceServer 会立即失败
        assertThatThrownBy(() -> client.listDocuments("../../etc"))
                .isInstanceOf(ServiceException.class)
                .extracting(error -> ((ServiceException) error).getErrorCode())
                .isEqualTo(RagErrorCodeConstants.TENANT_ID_INVALID);

        assertThatThrownBy(() -> client.listDocuments(null))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> client.listDocuments("user-1"))
                .isInstanceOf(ServiceException.class);

        server.verify();
    }

    @Test
    @DisplayName("列表响应中的状态被归一化")
    void shouldNormalizeStatusInListResponse() {
        String body = "{\"items\":["
                + "{\"id\":\"d1\",\"file_path\":\"a.md\",\"status\":\"PROCESSED\"},"
                + "{\"id\":\"d2\",\"file_path\":\"b.md\",\"status\":\"pending\"}]}";
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/documents/paginated"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", API_KEY))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<RagDocumentDTO> documents = client.listDocuments(TENANT);

        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).status()).isEqualTo(RagDocumentStatus.COMPLETED);
        assertThat(documents.get(1).status()).isEqualTo(RagDocumentStatus.PROCESSING);
        server.verify();
    }

    @Test
    @DisplayName("上游 500 转换为上游返回错误，且异常信息不含上游报文")
    void shouldTranslateServerError() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/documents/paginated")))
                .andRespond(withServerError()
                        .body("internal host db-secret:5432 exploded")
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.listDocuments(TENANT))
                .isInstanceOf(ServiceException.class)
                .satisfies(error -> {
                    ServiceException serviceError = (ServiceException) error;
                    assertThat(serviceError.getErrorCode())
                            .isEqualTo(RagErrorCodeConstants.UPSTREAM_ERROR);
                    // 上游报文里的敏感片段不得出现在异常信息中
                    assertThat(serviceError.getMessage()).doesNotContain("db-secret");
                });
    }

    @Test
    @DisplayName("连接超时转换为上游不可达")
    void shouldTranslateTimeout() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/documents/paginated")))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.listDocuments(TENANT))
                .isInstanceOf(ServiceException.class)
                .extracting(error -> ((ServiceException) error).getErrorCode())
                .isEqualTo(RagErrorCodeConstants.UPSTREAM_UNAVAILABLE);
    }

    @Test
    @DisplayName("不支持的检索模式在发请求前被拒绝")
    void shouldRejectUnsupportedQueryMode() {
        assertThatThrownBy(() -> client.query(TENANT, "问题", "not-a-mode"))
                .isInstanceOf(ServiceException.class);

        server.verify();
    }

    @Test
    @DisplayName("问答响应拆出回答与上下文，content 为数组时按切片拼接")
    void shouldParseAnswerAndContexts() {
        // 上游 ReferenceItem.content 是 List[str]（同一文件的多个切片），不是字符串
        String body = "{\"response\":\"答案是 42\","
                + "\"references\":[{\"content\":[\"片段一\",\"片段二\"]},"
                + "{\"file_path\":\"x.md\",\"content\":[\"片段三\"]}]}";
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/query"))
                .andExpect(method(HttpMethod.POST))
                // 不打开 include_chunk_content，上游就不返回片段正文
                .andExpect(content().json("""
                        {"query": "问题", "mode": "mix",
                         "include_references": true, "include_chunk_content": true}
                        """))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var result = client.query(TENANT, "问题", "mix");

        assertThat(result.answer()).isEqualTo("答案是 42");
        assertThat(result.contexts()).containsExactly("片段一\n片段二", "片段三");
        assertThat(result.mode()).isEqualTo("mix");
        server.verify();
    }

    @Test
    @DisplayName("图谱响应按上游嵌套结构解析：节点名在 labels、类型与权重在 properties")
    void shouldParseNestedGraphStructure() {
        // 上游 KnowledgeGraphNode = {id, labels: [...], properties: {entity_type, ...}}
        // 上游 KnowledgeGraphEdge = {id, type, source, target, properties: {weight, description}}
        // 早期实现按扁平字段读，结果节点名、类型、权重全是 null
        String body = """
                {"nodes":[
                  {"id":"n1","labels":["注塑机"],"properties":{"entity_type":"设备","description":"一号机"}},
                  {"id":"n2","labels":["点检工序"],"properties":{"entity_type":"工序"}}
                ],
                "edges":[
                  {"id":"e1","type":"RELATED","source":"n1","target":"n2",
                   "properties":{"weight":0.87,"description":"执行"}}
                ],
                "is_truncated":true}
                """;
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        BASE_URL + "/tenant/" + TENANT + "/graphs")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var graph = client.getGraph(TENANT, "设备", 2);

        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.nodes().get(0).label()).isEqualTo("注塑机");
        assertThat(graph.nodes().get(0).type()).isEqualTo("设备");
        assertThat(graph.nodes().get(1).type()).isEqualTo("工序");
        assertThat(graph.edges()).singleElement().satisfies(edge -> {
            assertThat(edge.source()).isEqualTo("n1");
            assertThat(edge.target()).isEqualTo("n2");
            assertThat(edge.description()).isEqualTo("执行");
            assertThat(edge.weight()).isEqualTo(0.87);
        });
        server.verify();
    }

    @Test
    @DisplayName("同名文档冲突按 409 翻译为用户侧错误而不是第三方故障")
    void shouldTranslateConflictToUserError() {
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/documents/text"))
                .andRespond(org.springframework.test.web.client.response
                        .MockRestResponseCreators.withStatus(
                                org.springframework.http.HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Document storage already contains report.md\"}"));

        assertThatThrownBy(() -> client.uploadText(TENANT, "report.md", "内容"))
                .isInstanceOf(ServiceException.class)
                .extracting(error -> ((ServiceException) error).getErrorCode())
                .isEqualTo(RagErrorCodeConstants.DOCUMENT_CONFLICT);
    }

    @Test
    @DisplayName("上传内容超过上限时拒绝且不发请求")
    void shouldRejectOversizedUpload() {
        RagProperties properties = new RagProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setMaxUploadSize(4L);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer smallServer = MockRestServiceServer.bindTo(builder).build();
        RagClient smallClient = new RagClient(properties, builder.build());

        assertThatThrownBy(() -> smallClient.uploadText(TENANT, "big.md", "超过四个字节了"))
                .isInstanceOf(ServiceException.class)
                .extracting(error -> ((ServiceException) error).getErrorCode())
                .isEqualTo(RagErrorCodeConstants.UPLOAD_TOO_LARGE);

        smallServer.verify();
    }
}
