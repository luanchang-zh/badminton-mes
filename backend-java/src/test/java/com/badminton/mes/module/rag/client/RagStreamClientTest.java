package com.badminton.mes.module.rag.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.ArrayList;
import java.util.List;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.rag.client.dto.RagQueryResult;
import com.badminton.mes.module.rag.config.RagProperties;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import com.badminton.mes.module.rag.enums.RagQueryMode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

/**
 * 流式客户端测试。
 *
 * <p>三件事必须验证：
 * <ol>
 *   <li>NDJSON 的三类行（references / response / error）被正确区分；</li>
 *   <li>路径带租户前缀、请求头带内部密钥；</li>
 *   <li>非法租户在发请求前被拒绝。</li>
 * </ol>
 */
class RagStreamClientTest {

    /** 测试用基础地址 */
    private static final String BASE_URL = "http://rag.test:9621";

    /** 测试用内部密钥 */
    private static final String API_KEY = "test-internal-key";

    /** 测试用租户 */
    private static final String TENANT = "user_1";

    /** 上游 NDJSON 媒体类型 */
    private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");

    private MockRestServiceServer server;

    private RagStreamClient client;

    @Test
    void cachedStreamKeepsHistoryAndStructuredReferenceDetails() {
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/query/stream"))
                .andExpect(content().json("""
                        {"conversation_history":[{"role":"user","content":"前一个问题"}]}
                        """))
                .andRespond(withSuccess("""
                        {"references":[{"reference_id":"2","file_path":"C:\\\\data\\\\sop.md","content":["原文"]}],"response":"完整回答 [2]"}
                        """, NDJSON));
        List<RagStreamLine> lines = new ArrayList<>();
        var result = client.streamQuery(TENANT, "追问", RagQueryMode.MIX,
                List.of(new com.badminton.mes.module.rag.client.dto.RagHistoryMessage("user", "前一个问题")),
                lines::add, new StreamCancellation());
        assertThat(result.references()).singleElement().satisfies(ref -> {
            assertThat(ref.sourceName()).isEqualTo("sop.md");
            assertThat(ref.referenceId()).isEqualTo("2");
        });
        assertThat(lines.getFirst().referenceDetails()).isEqualTo(result.references());
        assertThat(result.answer()).isEqualTo("完整回答 [2]");
        server.verify();
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RagProperties properties = new RagProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setInternalApiKey(API_KEY);
        client = new RagStreamClient(new ObjectMapper(), properties, builder.build());
    }

    @Test
    @DisplayName("流式请求带租户前缀、内部密钥与 NDJSON 接受头")
    void shouldSendStreamWithTenantPrefixAndApiKey() {
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/query/stream"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", API_KEY))
                .andExpect(header("Accept", "application/x-ndjson"))
                .andRespond(withSuccess("{\"response\":\"你好\"}\n", NDJSON));

        client.streamQuery(TENANT, "问题", RagQueryMode.MIX, line -> {
        }, new StreamCancellation());

        server.verify();
    }

    @Test
    @DisplayName("参考片段与正文分片被分别下发，正文按到达顺序拼接")
    void shouldDispatchReferencesAndContentSeparately() {
        String body = """
                {"references":[{"content":["片段一","片段二"]}]}
                {"response":"答案"}
                {"response":"是 42"}
                """;
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/query/stream"))
                .andRespond(withSuccess(body, NDJSON));

        List<RagStreamLine> received = new ArrayList<>();
        RagQueryResult result = client.streamQuery(TENANT, "问题", RagQueryMode.MIX,
                received::add, new StreamCancellation());

        assertThat(received).extracting(RagStreamLine::kind).containsExactly(
                RagStreamLine.Kind.REFERENCES,
                RagStreamLine.Kind.CONTENT,
                RagStreamLine.Kind.CONTENT);
        assertThat(received.get(0).references()).containsExactly("片段一\n片段二");
        assertThat(result.answer()).isEqualTo("答案是 42");
        assertThat(result.contexts()).containsExactly("片段一\n片段二");
        assertThat(result.mode()).isEqualTo("mix");
    }

    @Test
    @DisplayName("命中缓存时单行同时含 references 与 response，两者都不丢")
    void shouldKeepBothFieldsOnCacheHit() {
        // 上游在高频问题命中 LLM 缓存时会把引用与完整答案放在同一行；
        // 若按「一行一种类型」处理，引用会整行丢失，而这正是最需要出处的场景
        String body = """
                {"references":[{"content":["切片正文"]}],"response":"完整答案"}
                """;
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/query/stream"))
                .andRespond(withSuccess(body, NDJSON));

        List<RagStreamLine> received = new ArrayList<>();
        RagQueryResult result = client.streamQuery(TENANT, "问题", RagQueryMode.MIX,
                received::add, new StreamCancellation());

        assertThat(result.answer()).isEqualTo("完整答案");
        assertThat(result.contexts()).containsExactly("切片正文");
        assertThat(received).singleElement()
                .satisfies(line -> {
                    assertThat(line.content()).isEqualTo("完整答案");
                    assertThat(line.references()).containsExactly("切片正文");
                });
    }

    @Test
    @DisplayName("上游 error 行被识别为错误行")
    void shouldRecognizeErrorLine() {
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/query/stream"))
                .andRespond(withSuccess("{\"error\":\"模型不可用\"}\n", NDJSON));

        List<RagStreamLine> received = new ArrayList<>();
        client.streamQuery(TENANT, "问题", RagQueryMode.NAIVE, received::add,
                new StreamCancellation());

        assertThat(received).singleElement()
                .satisfies(line -> {
                    assertThat(line.kind()).isEqualTo(RagStreamLine.Kind.ERROR);
                    assertThat(line.error()).isEqualTo("模型不可用");
                });
    }

    @Test
    @DisplayName("进度与无法解析的行被归入忽略类，不中断整场问答")
    void shouldIgnoreProgressAndMalformedLines() {
        String body = """
                {"progress":"extracting_keywords"}
                not-json-at-all

                {"response":"正文"}
                """;
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/query/stream"))
                .andRespond(withSuccess(body, NDJSON));

        List<RagStreamLine> received = new ArrayList<>();
        RagQueryResult result = client.streamQuery(TENANT, "问题", RagQueryMode.LOCAL,
                received::add, new StreamCancellation());

        // 进度行会下发（类型为 PROGRESS），无法解析的行与空行被完全丢弃
        assertThat(received).extracting(RagStreamLine::kind)
                .containsExactly(RagStreamLine.Kind.PROGRESS, RagStreamLine.Kind.CONTENT);
        assertThat(result.answer()).isEqualTo("正文");
    }

    @Test
    @DisplayName("上游 5xx 转换为上游返回错误")
    void shouldTranslateServerError() {
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/query/stream"))
                .andRespond(withServerError()
                        .body("{\"detail\":\"internal db-secret\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.streamQuery(TENANT, "问题", RagQueryMode.MIX,
                line -> {
                }, new StreamCancellation()))
                .isInstanceOf(ServiceException.class)
                .satisfies(error -> {
                    ServiceException serviceError = (ServiceException) error;
                    assertThat(serviceError.getErrorCode())
                            .isEqualTo(RagErrorCodeConstants.UPSTREAM_ERROR);
                    assertThat(serviceError.getMessage()).doesNotContain("db-secret");
                });
    }

    @Test
    @DisplayName("非法租户标识在发请求前被拒绝，服务端收到 0 次请求")
    void shouldRejectInvalidTenantBeforeSendingRequest() {
        // 不设置任何 server.expect：一旦发出请求，MockRestServiceServer 会立即失败
        assertThatThrownBy(() -> client.streamQuery("../../etc", "问题", RagQueryMode.MIX,
                line -> {
                }, new StreamCancellation()))
                .isInstanceOf(ServiceException.class)
                .extracting(error -> ((ServiceException) error).getErrorCode())
                .isEqualTo(RagErrorCodeConstants.TENANT_ID_INVALID);
    }

    @Test
    @DisplayName("未指定模式时按缺省模式发起请求")
    void shouldUseDefaultModeWhenModeMissing() {
        server.expect(requestTo(BASE_URL + "/tenant/" + TENANT + "/query/stream"))
                .andExpect(content().json("{\"query\":\"问题\",\"mode\":\"naive\","
                        + "\"stream\":true,\"include_references\":true,"
                        + "\"include_chunk_content\":true}"))
                .andRespond(withSuccess("{\"response\":\"ok\"}\n", NDJSON));

        client.streamQuery(TENANT, "问题", null, line -> {
        }, new StreamCancellation());

        server.verify();
    }
}
