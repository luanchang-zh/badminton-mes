package com.badminton.mes.module.rag.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.rag.client.dto.RagQueryResult;
import com.badminton.mes.module.rag.config.RagProperties;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import com.badminton.mes.module.rag.enums.RagQueryMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * LightRAG 流式问答客户端。
 *
 * <p>为什么单独一个类：流式与同步的调用方式完全不同。同步只需把整个响应体反序列化，
 * 流式必须逐行读取 NDJSON、边读边推给 SSE，还要能被前端断开时立即中止。
 * 塞进同一个类会让 {@link RagClient} 同时背负两种生命周期管理。
 *
 * <p><b>连接与取消</b>：读循环跑在独立线程上；前端断开时 {@link StreamCancellation#cancel()}
 * 关闭上游连接，阻塞中的 {@code readLine} 立刻抛错返回，模型侧生成随之终止。
 *
 * <p>上游地址、响应报文与内部密钥只写日志，不进入异常信息。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
@Component
public class RagStreamClient {

    private static final Logger logger = LoggerFactory.getLogger(RagStreamClient.class);

    /** 上游流式接口相对路径 */
    private static final String STREAM_PATH = "query/stream";

    /** 用于把 NDJSON 行解析成映射 */
    private final ObjectMapper objectMapper;

    /** 上游配置 */
    private final RagProperties properties;

    /** 流式调用专用的 HTTP 客户端，读取超时比同步调用长得多 */
    private final RestClient restClient;

    /**
     * 构造器注入，按配置建立长读取超时的客户端。
     *
     * @param objectMapper 项目统一的 JSON 映射器
     * @param properties   上游配置
     */
    @org.springframework.beans.factory.annotation.Autowired
    public RagStreamClient(ObjectMapper objectMapper, RagProperties properties) {
        this(objectMapper, properties,
                RagRestClientFactory.build(properties, properties.getStreamReadTimeout()));
    }

    /**
     * 包级构造器：允许测试注入已配置好的 RestClient。
     *
     * @param objectMapper 项目统一的 JSON 映射器
     * @param properties   上游配置
     * @param restClient   已配置的 HTTP 客户端
     */
    RagStreamClient(ObjectMapper objectMapper, RagProperties properties, RestClient restClient) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.restClient = restClient;
    }

    /**
     * 发起流式问答，逐行回调给调用方。
     *
     * <p>回调在调用线程内同步执行，因此调用方必须把本方法放在独立线程上运行，
     * 否则会阻塞请求线程。
     *
     * @param tenantId     租户标识，由 {@code RagTenantResolver} 决定
     * @param question     问题
     * @param mode         检索模式，null 视为缺省模式
     * @param lineConsumer 行消费者；抛出的异常会中止本次流
     * @param cancellation 取消信号，前端断开时由调用方触发
     * @return 已接收的正文与上下文；被取消时返回已收到的部分
     * @throws ServiceException 租户非法、模式非法或上游失败
     */
    public RagQueryResult streamQuery(String tenantId,
                                      String question,
                                      RagQueryMode mode,
                                      Consumer<RagStreamLine> lineConsumer,
                                      StreamCancellation cancellation) {
        String safeTenant = RagTenantGuard.requireValid(tenantId);
        RagQueryMode safeMode = mode == null ? RagQueryMode.DEFAULT_MODE : mode;
        String uri = RagTenantGuard.tenantUri(properties, safeTenant, STREAM_PATH);
        Map<String, Object> payload = Map.of(
                "query", question == null ? "" : question,
                "mode", safeMode.getValue(),
                "stream", true,
                "include_references", true,
                // 不打开这个开关，上游 references 里只有文件路径，没有片段正文
                "include_chunk_content", true);

        StreamCollector collector = new StreamCollector(safeMode.getValue(), lineConsumer);
        try {
            restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("application/x-ndjson"))
                    .headers(headers -> headers.set("X-API-Key", RagTenantGuard.apiKey(properties)))
                    .body(payload)
                    .exchange((request, response) -> {
                        readResponse(response.getStatusCode().value(), response.getBody(),
                                collector, cancellation);
                        return null;
                    });
        } catch (ResourceAccessException error) {
            // 取消会主动关闭连接，此时读取异常属于预期，不应报错给前端
            if (!cancellation.isCancelled()) {
                logger.error("RAG stream upstream unreachable: {}", error.getMessage());
                throw new ServiceException(RagErrorCodeConstants.UPSTREAM_UNAVAILABLE);
            }
        }
        return collector.toResult();
    }

    /**
     * 校验状态码并读取成功响应体的每一行。
     *
     * @param status       上游状态码
     * @param body         上游响应流
     * @param collector    收集器，同时把行推给调用方
     * @param cancellation 取消信号
     */
    private void readResponse(int status,
                              InputStream body,
                              StreamCollector collector,
                              StreamCancellation cancellation) {
        if (status < 200 || status >= 300) {
            // 非 2xx 时上游返回的是 JSON 错误体，不按 NDJSON 读取，直接交给统一错误码
            logger.error("RAG stream upstream returned status {}", status);
            throw new ServiceException(RagErrorCodeConstants.UPSTREAM_ERROR,
                    "知识库服务返回状态 " + status);
        }
        if (body == null) {
            return;
        }
        // 取消时必须能关掉流句柄，否则 readLine 会一直阻塞到读取超时
        cancellation.bind(body);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String rawLine;
            while (!cancellation.isCancelled() && (rawLine = reader.readLine()) != null) {
                collector.acceptLine(parseLine(rawLine));
            }
        } catch (IOException readError) {
            // 取消触发的关闭也会走到这里，按取消处理以免向用户报错
            if (!cancellation.isCancelled()) {
                logger.error("RAG stream read failed: {}", readError.getMessage());
                throw new ServiceException(RagErrorCodeConstants.UPSTREAM_ERROR,
                        "知识库服务连接中断");
            }
        }
    }

    /**
     * 解析一行 NDJSON。
     *
     * <p>解析失败不抛异常：上游偶发空行或调试输出不应中断整场问答。
     *
     * <p><b>同一行可能既有 references 又有完整 response</b>：上游命中 LLM 缓存时
     * 会把两者放在一行。若按「一行一种类型」处理，必然丢掉其中一项，
     * 因此这里显式识别这种合并形态。
     *
     * @param rawLine 原始行
     * @return 解析结果，无法识别时类型为 {@link RagStreamLine.Kind#IGNORED}
     */
    RagStreamLine parseLine(String rawLine) {
        Map<String, Object> parsed = parseJson(rawLine);
        if (parsed == null) {
            return RagStreamLine.ignored();
        }
        Object error = parsed.get("error");
        if (error instanceof String errorText && !errorText.isBlank()) {
            return RagStreamLine.error(errorText);
        }
        Object references = parsed.get("references");
        Object content = parsed.get("response");
        // 缓存命中的单行完整响应：references 与 response 同时存在，必须都保留
        if (references instanceof List<?> referenceList
                && content instanceof String contentText && !contentText.isEmpty()) {
            return RagStreamLine.referencesAndContent(
                    RagResponseParser.extractReferenceTexts(referenceList), contentText);
        }
        if (references instanceof List<?> referenceList) {
            return RagStreamLine.references(
                    RagResponseParser.extractReferenceTexts(referenceList));
        }
        if (content instanceof String contentText && !contentText.isEmpty()) {
            return RagStreamLine.content(contentText);
        }
        // progress、response_time 等元信息行对前端无意义，统一归入进度类
        return RagStreamLine.progress();
    }

    /**
     * 把一行 JSON 解析成映射。
     *
     * @param rawLine 原始行
     * @return 映射，空行或解析失败时为 null
     */
    private Map<String, Object> parseJson(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawLine, new TypeReference<Map<String, Object>>() {
            });
        } catch (RuntimeException parseError) {
            logger.debug("Ignored malformed stream line: {}", parseError.getMessage());
            return null;
        }
    }

    /**
     * 单次流的收集器：既把行推给调用方，也累积最终结果。
     *
     * <p>累积正文的目的是让调用方在流结束后能拿到完整答案（例如写入会话历史）。
     * 参考片段可能先于正文到达，因此单独缓存。
     */
    private static final class StreamCollector {

        private final String mode;

        private final Consumer<RagStreamLine> lineConsumer;

        private final StringBuilder answer = new StringBuilder();

        private List<String> contexts = List.of();

        private StreamCollector(String mode, Consumer<RagStreamLine> lineConsumer) {
            this.mode = mode;
            this.lineConsumer = lineConsumer;
        }

        /**
         * 累积并下发一行。
         *
         * <p>参考片段与正文按「字段是否存在」累积，而不是按行类型：
         * 缓存命中的合并行两者都有，只按 kind 分支会丢掉其中一项。
         *
         * @param line 已解析的行
         */
        private void acceptLine(RagStreamLine line) {
            if (!line.references().isEmpty()) {
                contexts = line.references();
            }
            if (line.content() != null) {
                answer.append(line.content());
            }
            if (line.kind() != RagStreamLine.Kind.IGNORED) {
                lineConsumer.accept(line);
            }
        }

        /**
         * 汇总为本系统的问答结果。
         *
         * @return 已收到的正文与上下文
         */
        private RagQueryResult toResult() {
            return new RagQueryResult(answer.toString(), contexts, mode);
        }
    }
}
