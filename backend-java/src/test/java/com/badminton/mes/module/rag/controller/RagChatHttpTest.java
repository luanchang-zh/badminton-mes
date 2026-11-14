package com.badminton.mes.module.rag.controller;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import com.badminton.mes.common.exception.*;
import com.badminton.mes.common.security.*;
import com.badminton.mes.module.rag.client.*;
import com.badminton.mes.module.rag.client.dto.*;
import com.badminton.mes.module.rag.config.RagConfiguration;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import com.badminton.mes.module.rag.dal.entity.RagTenantBinding;
import com.badminton.mes.module.rag.dal.repository.*;
import com.badminton.mes.module.rag.service.*;
import com.badminton.mes.module.rag.service.impl.RagQueryServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 HTTP、鉴权、H2 迁移和 JPA；仅替换尚未部署的 LightRAG 与 Redis 会话读取。 */
@SpringBootTest(classes = RagChatHttpTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:rag_chat_http;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.type.preferred_boolean_jdbc_type=TINYINT",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/migration/V2026110801__create_rag_tenant_binding.sql,classpath:db/migration/V2026111301__create_rag_conversation.sql"
})
class RagChatHttpTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = RagTenantBinding.class)
    @EnableJpaRepositories(basePackageClasses = RagConversationRepository.class)
    @Import({RagConversationController.class, RagProfileController.class, RagQueryController.class,
            RagConversationService.class, RagChatService.class, RagTenantResolver.class, RagBindingService.class,
            RagQueryServiceImpl.class, RagConfiguration.class, SecurityWebConfig.class,
            AuthInterceptor.class, GlobalExceptionHandler.class})
    static class Application { }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired RagTenantBindingRepository bindings;
    @Autowired RagConversationRepository conversations;
    @Autowired RagMessageRepository messages;
    @Autowired RagConversationService conversationService;
    @Autowired com.badminton.mes.module.rag.config.RagProperties properties;
    @MockitoBean LoginSessionReader sessions;
    @MockitoBean RagClient client;
    @MockitoBean RagStreamClient streamClient;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final List<List<RagHistoryMessage>> histories = new CopyOnWriteArrayList<>();
    private final AtomicInteger upstreamCalls = new AtomicInteger();

    @BeforeEach
    void prepare() {
        messages.deleteAllInBatch(); conversations.deleteAllInBatch(); bindings.deleteAllInBatch();
        histories.clear(); upstreamCalls.set(0);
        for (long id : new long[]{1, 2}) {
            RagTenantBinding b = new RagTenantBinding(); b.setUserId(id); b.setTenantId("user_" + id);
            b.setEnabled(true); b.setCreatedAt(LocalDateTime.now()); b.setUpdatedAt(LocalDateTime.now());
            bindings.save(b);
        }
        when(sessions.resolve("a")).thenReturn(Optional.of(user(1, "OPERATOR")));
        when(sessions.resolve("b")).thenReturn(Optional.of(user(2, "CRAFT_ENGINEER")));
        when(sessions.resolve("unbound")).thenReturn(Optional.of(user(3, "ADMIN")));
        when(streamClient.streamQuery(anyString(), anyString(), any(), anyList(), any(), any()))
                .thenAnswer(invocation -> {
                    upstreamCalls.incrementAndGet();
                    histories.add(List.copyOf(invocation.getArgument(3)));
                    Consumer<RagStreamLine> consumer = invocation.getArgument(4);
                    var ref = new RagReference("1", "点检规程.md", List.of("每天检查传动机构"));
                    consumer.accept(new RagStreamLine(RagStreamLine.Kind.REFERENCES, null,
                            List.of("每天检查传动机构"), null, List.of(ref)));
                    consumer.accept(RagStreamLine.content("检查传动机构 [1]"));
                    return new RagQueryResult("检查传动机构 [1]", List.of("每天检查传动机构"), "naive", List.of(ref));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"/profile", "/conversations", "/conversations/1/messages"})
    void endpointsRequireLogin(String path) throws Exception {
        assertReply(send("GET", path, null, null), 401, "A0230");
    }

    @Test
    void profileWorksBeforeRagDeploymentAndReflectsRoles() throws Exception {
        var a = assertReply(send("GET", "/profile", "a", null), 200, "00000");
        assertThat(a.path("data").path("bound").asBoolean()).isTrue();
        assertThat(a.path("data").path("canManageDocuments").asBoolean()).isFalse();
        assertThat(a.path("data").path("serviceConfigured").asBoolean()).isFalse();
        var b = assertReply(send("GET", "/profile", "b", null), 200, "00000");
        assertThat(b.path("data").path("canManageDocuments").asBoolean()).isTrue();
        var empty = assertReply(send("GET", "/profile", "unbound", null), 200, "00000");
        assertThat(empty.path("data").path("bound").asBoolean()).isTrue();
        assertThat(bindings.findByUserId(3L).orElseThrow().getTenantId()).isEqualTo("mes_u_3");
        assertReply(send("POST", "/conversations", "unbound", "{}"), 200, "00000");
        properties.setInternalApiKey("test-profile-key");
        try {
            var configured = assertReply(send("GET", "/profile", "a", null), 200, "00000");
            assertThat(configured.path("data").path("serviceConfigured").asBoolean()).isTrue();
            assertThat(configured.toString()).doesNotContain("test-profile-key", "127.0.0.1");
        } finally {
            properties.setInternalApiKey("");
        }
        verifyNoInteractions(streamClient, client);
    }

    @Test
    void firstVisitsByAnyRoleCreateOneIndependentBinding() throws Exception {
        when(sessions.resolve("new-user")).thenReturn(Optional.of(user(4, "OPERATOR")));
        var calls = new ArrayList<CompletableFuture<Void>>();
        for (int index = 0; index < 8; index++) {
            calls.add(CompletableFuture.runAsync(() -> {
                try {
                    var profile = assertReply(send("GET", "/profile", "new-user", null), 200, "00000");
                    assertThat(profile.path("data").path("enabled").asBoolean()).isTrue();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }));
        }
        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();
        assertThat(bindings.count()).isEqualTo(3);
        assertThat(bindings.findByUserId(4L).orElseThrow().getTenantId()).isEqualTo("mes_u_4");
        assertReply(send("POST", "/conversations", "new-user", "{}"), 200, "00000");
        assertThat(bindings.findByUserId(1L).orElseThrow().getTenantId()).isEqualTo("user_1");
        verifyNoInteractions(streamClient, client);
    }

    @Test
    void createRenamePageAndDeleteWithoutUpstream() throws Exception {
        long id = create();
        assertReply(send("PATCH", "/conversations/" + id, "a", "{\"title\":\"点检咨询\"}"), 200, "00000");
        var page = assertReply(send("GET", "/conversations?pageNo=99&pageSize=20", "a", null), 200, "00000");
        assertThat(page.path("data").path("pageNo").asInt()).isEqualTo(1);
        assertThat(page.path("data").path("list").get(0).path("title").asString()).isEqualTo("点检咨询");
        assertReply(send("DELETE", "/conversations/" + id, "a", null), 200, "00000");
        assertThat(conversations.count()).isZero();
        verifyNoInteractions(streamClient);
    }

    @Test
    void crossUserAndChangedTenantCannotAccessConversation() throws Exception {
        long id = create();
        assertReply(send("GET", "/conversations/" + id + "/messages", "b", null), 400, "A0440");
        assertReply(send("DELETE", "/conversations/" + id, "b", null), 400, "A0440");
        RagTenantBinding b = bindings.findByUserId(1L).orElseThrow(); b.setTenantId("replacement"); bindings.save(b);
        assertReply(send("GET", "/conversations/" + id + "/messages", "a", null), 400, "A0440");
    }

    @Test
    void syncChatPersistsHistoryAndReplaysRequestWithoutRegeneration() throws Exception {
        long id = create();
        var first = assertReply(send("POST", "/query", "a", query(id, "request_001", "如何点检")), 200, "00000");
        assertThat(first.path("data").path("message").path("references").get(0)
                .path("sourceName").asString()).isEqualTo("点检规程.md");
        assertReply(send("POST", "/query", "a", query(id, "request_002", "有哪些注意事项")), 200, "00000");
        assertThat(histories.get(1)).containsExactly(new RagHistoryMessage("user", "如何点检"),
                new RagHistoryMessage("assistant", "检查传动机构 [1]"));
        assertReply(send("POST", "/query", "a", query(id, "request_002", "有哪些注意事项")), 200, "00000");
        assertThat(upstreamCalls.get()).isEqualTo(2);
        assertThat(messages.count()).isEqualTo(4);
        assertReply(send("POST", "/query", "a", query(id, "request_002", "另一个问题")), 400, "A0409");
        var restored = assertReply(send("GET", "/conversations/" + id + "/messages", "a", null), 200, "00000");
        assertThat(restored.path("data").path("total").asInt()).isEqualTo(4);
    }

    @Test
    void invalidRequestsDoNotCreateMessages() throws Exception {
        long id = create();
        assertReply(send("POST", "/query", "a", "{\"conversationId\":" + id + ",\"question\":\"问题\"}"), 400, "A0400");
        assertReply(send("GET", "/conversations?pageSize=101", "a", null), 400, "A0400");
        assertReply(send("PATCH", "/conversations/" + id, "a", "{\"title\":\" \"}"), 400, "A0400");
        assertThat(messages.count()).isZero();
    }

    @Test
    void upstreamFailurePersistsAndRetryCreatesOnlyNewAnswer() throws Exception {
        long id = create();
        doThrow(new ServiceException(RagErrorCodeConstants.UPSTREAM_UNAVAILABLE)).when(streamClient)
                .streamQuery(anyString(), anyString(), any(), anyList(), any(), any());
        assertReply(send("POST", "/query", "a", query(id, "failed_001", "如何点检")), 502, "C0301");
        var old = messages.findByConversationIdAndRequestId(id, "failed_001").orElseThrow();
        assertThat(old.getStatus()).isEqualTo("FAILED");
        assertThat(conversations.findById(id).orElseThrow().getActiveMessageId()).isNull();
        doAnswer(inv -> new RagQueryResult("重试成功", List.of(), "naive"))
                .when(streamClient).streamQuery(anyString(), anyString(), any(), anyList(), any(), any());
        var response = send("POST", "/messages/" + old.getId() + "/retry", "a", "{\"requestId\":\"retry_001\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("event:meta", "event:done", "重试成功");
        assertThat(messages.count()).isEqualTo(3);
    }

    @Test
    void streamIncludesMetadataReferencesAndDone() throws Exception {
        long id = create();
        var response = send("POST", "/query/stream", "a", query(id, "stream_001", "如何点检"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/event-stream");
        assertThat(response.body()).contains("event:meta", "event:reference_details", "event:delta", "event:done", "COMPLETED");
    }

    @Test
    void cancelClosesUpstreamAndBusyConversationRejectsNewQuestion() throws Exception {
        long id = create();
        CountDownLatch started = new CountDownLatch(1), released = new CountDownLatch(1);
        doAnswer(inv -> {
            StreamCancellation cancellation = inv.getArgument(5);
            cancellation.bind(released::countDown);
            Consumer<RagStreamLine> consumer = inv.getArgument(4);
            consumer.accept(RagStreamLine.content("已生成的内容"));
            started.countDown();
            assertThat(released.await(8, TimeUnit.SECONDS)).isTrue();
            return new RagQueryResult("已生成的内容", List.of(), "naive");
        }).when(streamClient).streamQuery(anyString(), anyString(), any(), anyList(), any(), any());
        var running = http.sendAsync(request("POST", "/query/stream", "a", query(id, "stream_busy", "如何点检")),
                HttpResponse.BodyHandlers.ofString());
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        long messageId = conversations.findById(id).orElseThrow().getActiveMessageId();
        assertReply(send("POST", "/query", "a", query(id, "other_001", "新问题")), 400, "A0409");
        assertReply(send("POST", "/messages/" + messageId + "/cancel", "b", null), 400, "A0440");
        var cancelled = assertReply(send("POST", "/messages/" + messageId + "/cancel", "a", null), 200, "00000");
        assertThat(cancelled.path("data").path("status").asString()).isEqualTo("CANCELLED");
        assertThat(running.get(5, TimeUnit.SECONDS).body()).contains("CANCELLED");
        assertThat(messages.findById(messageId).orElseThrow().getContent()).isEqualTo("已生成的内容");
    }

    @Test
    void startupRecoveryReleasesInterruptedConversation() throws Exception {
        long id = create();
        SecurityContextHolder.set("a", user(1, "OPERATOR"));
        try { conversationService.begin(id, "restart_001", "如何点检", com.badminton.mes.module.rag.enums.RagQueryMode.NAIVE); }
        finally { SecurityContextHolder.clear(); }
        conversationService.recoverInterrupted();
        assertThat(conversations.findById(id).orElseThrow().getActiveMessageId()).isNull();
        assertThat(messages.findByConversationIdAndRequestId(id, "restart_001").orElseThrow().getStatus()).isEqualTo("FAILED");
    }

    private long create() throws Exception {
        return assertReply(send("POST", "/conversations", "a", "{}"), 200, "00000").path("data").path("id").asLong();
    }
    private String query(long id, String requestId, String question) {
        return json.writeValueAsString(Map.of("conversationId", id, "requestId", requestId, "question", question));
    }
    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        return http.send(request(method, path, token, body), HttpResponse.BodyHandlers.ofString());
    }
    private HttpRequest request(String method, String path, String token, String body) {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/rag" + path))
                .timeout(Duration.ofSeconds(12));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        return request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build();
    }
    private JsonNode assertReply(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        var result = json.readTree(response.body());
        assertThat(result.path("code").asString()).as(response.body()).isEqualTo(code);
        return result;
    }
    private LoginUser user(long id, String role) {
        var user = new LoginUser(); user.setUserId(id); user.setUserNo("user" + id); user.setRoleCodes(List.of(role)); return user;
    }
}
