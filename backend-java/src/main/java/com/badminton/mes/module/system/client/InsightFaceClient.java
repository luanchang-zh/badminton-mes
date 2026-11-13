package com.badminton.mes.module.system.client;

import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.system.client.InsightFaceModels.DeleteFaceResult;
import com.badminton.mes.module.system.client.InsightFaceModels.EnrollResult;
import com.badminton.mes.module.system.client.InsightFaceModels.FaceSample;
import com.badminton.mes.module.system.client.InsightFaceModels.RejectedImage;
import com.badminton.mes.module.system.client.InsightFaceModels.SearchMatch;
import com.badminton.mes.module.system.config.InsightFaceProperties;
import com.badminton.mes.module.system.constants.SystemErrorCodeConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.MissingNode;

/**
 * InsightFace Server HTTP 客户端，只由人脸相关 Service 调用。
 *
 * <p>异常分三类(规划第 6.1 节)：
 * <ul>
 *   <li>连接失败、连接超时、401/403、5xx、MES 自身请求错误(400/413 等)：{@code ServiceException(C0001)}；</li>
 *   <li>读超时：{@code ServiceException(C0200)}；</li>
 *   <li>业务性 404、409、422：{@link InsightFaceApiException}，由 Service 决定如何处理。</li>
 * </ul>
 *
 * <p>Collection 采用懒初始化：第一次需要写入或搜索前确认 Collection 存在，不存在就创建，
 * 确认结果记在标志位里；之后若某次调用返回 {@code collection_not_found}，清掉标志位，下次重新确认。
 *
 * <p>日志只记录动作、HTTP 状态、错误码和 {@code x-request-id}，不记录 API Key、图片字节和特征向量。
 *
 * @author xxy-lh
 * @date 2026/11/09
 */
@Component
public class InsightFaceClient {

    private static final Logger logger = LoggerFactory.getLogger(InsightFaceClient.class);

    /** 首次注册、追加样本统一使用 standard 审查：单张人脸、尺寸、检测分、质量、姿态 */
    private static final String REVIEW_MODE = "standard";

    /** Collection 检索精度配置，与规划第 3.4 节一致 */
    private static final String SEARCH_PROFILE = "fp32_v1";

    private static final String COLLECTION_NAME = "课设用户";

    /** 查询样本单页上限；每人样本上限远小于此值，一页即可取全 */
    private static final int FACE_PAGE_LIMIT = 100;

    /** 搜索只取前两名：第一名判定阈值，第二名计算分差；官方结果已按 Person 汇总 */
    private static final int SEARCH_LIMIT = 2;

    private static final String REQUEST_ID_HEADER = "x-request-id";

    /** 未知拒绝原因码的统一中文提示 */
    private static final String UNKNOWN_REJECT_REASON = "照片不符合要求";

    /** standard 模式拒绝原因码 → 中文提示，见接口核对记录第 2.3 节 */
    private static final Map<String, String> REJECT_REASONS = Map.of(
            "face_not_found", "未检测到人脸",
            "multiple_faces", "检测到多张人脸，请保证画面中只有本人",
            "face_too_small", "人脸太小，请靠近镜头",
            "low_detection_score", "人脸不清晰，请正对镜头重拍",
            "extreme_pose", "侧脸或低头角度过大，请正对镜头",
            "low_quality", "照片模糊或光线不佳，请重拍",
            "invalid_embedding", UNKNOWN_REJECT_REASON);

    private final RestClient restClient;

    /** 登录搜索连接最多 1 秒、读取最多 30 秒，注册和资料管理继续使用原有超时。 */
    private final RestClient loginRestClient;

    private final ObjectMapper objectMapper;

    private final InsightFaceProperties properties;

    /** Collection 是否已确认存在 */
    private volatile boolean collectionReady;

    /**
     * 按配置构造客户端：JDK HttpClient，固定 HTTP/1.1，连接超时与读超时分别配置。
     *
     * @param properties 人脸服务配置
     * @param objectMapper JSON 解析器
     */
    @Autowired
    public InsightFaceClient(InsightFaceProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper,
                RestClient.builder().requestFactory(createRequestFactory(properties)),
                RestClient.builder().requestFactory(createRequestFactory(
                        properties.getConnectTimeout().compareTo(Duration.ofSeconds(1)) < 0
                                ? properties.getConnectTimeout() : Duration.ofSeconds(1),
                        properties.getReadTimeout().compareTo(Duration.ofSeconds(30)) < 0
                                ? properties.getReadTimeout() : Duration.ofSeconds(30))));
    }

    /**
     * 使用外部提供的 Builder 构造客户端，供单元测试绑定 MockRestServiceServer。
     *
     * @param properties 人脸服务配置
     * @param objectMapper JSON 解析器
     * @param builder 已设置请求工厂的 Builder
     */
    InsightFaceClient(InsightFaceProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this(properties, objectMapper, builder, builder);
    }

    private InsightFaceClient(InsightFaceProperties properties, ObjectMapper objectMapper,
                              RestClient.Builder builder, RestClient.Builder loginBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        builder.baseUrl(properties.getBaseUrl());
        if (StringUtils.hasText(properties.getApiKey())) {
            String authorization = "Bearer " + properties.getApiKey();
            builder.defaultHeaders(headers -> headers.set(HttpHeaders.AUTHORIZATION, authorization));
        }
        this.restClient = builder.build();
        loginBuilder.baseUrl(properties.getBaseUrl());
        if (StringUtils.hasText(properties.getApiKey())) {
            loginBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
        }
        this.loginRestClient = loginBuilder.build();
    }

    /**
     * 确认 Collection 存在，不存在则创建。已确认过时直接返回。
     *
     * @throws ServiceException 人脸服务不可用(C0001)或超时(C0200)
     */
    public void ensureCollection() {
        if (collectionReady) {
            return;
        }
        synchronized (this) {
            if (collectionReady) {
                return;
            }
            String collectionId = properties.getCollectionId();
            try {
                execute("查询 Collection", restClient.get()
                        .uri("/v1/collections/{collectionId}", collectionId));
                logger.info("[InsightFace] Collection 已存在, collectionId: {}", collectionId);
            } catch (InsightFaceApiException exception) {
                if (!exception.is(404, "collection_not_found")) {
                    throw unexpected("查询 Collection", exception);
                }
                createCollection(collectionId);
            }
            collectionReady = true;
        }
    }

    /**
     * 创建 Person 并写入首批样本，review_mode 固定为 standard。
     *
     * <p>全部照片被拒(422 registration_failed)时 InsightFace 不创建 Person，这里返回
     * {@code faceIds} 为空的结果，而不是抛异常，方便调用方把逐张原因返回给前端。
     *
     * @param personId Person id，固定为 user-{userId}
     * @param externalId 用户主键的十进制字符串
     * @param name 用户姓名
     * @param images 已校验的照片
     * @return 入库结果
     * @throws InsightFaceApiException 409 person_exists 等业务错误
     */
    public EnrollResult createPerson(String personId, String externalId, String name, List<FaceImage> images) {
        ensureCollection();
        MultipartBodyBuilder body = imagesBody(images);
        body.part("id", personId);
        body.part("external_id", externalId);
        if (StringUtils.hasText(name)) {
            body.part("name", name);
        }
        return enroll("创建 Person", restClient.post()
                .uri("/v1/collections/{collectionId}/persons", properties.getCollectionId())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build()));
    }

    /**
     * 向已有 Person 追加样本，review_mode 固定为 standard。全部照片被拒时返回 {@code faceIds} 为空的结果。
     *
     * @param personId Person id
     * @param images 已校验的照片
     * @return 入库结果
     * @throws InsightFaceApiException 404 person_not_found、409 person_face_limit_exceeded 等业务错误
     */
    public EnrollResult addFaces(String personId, List<FaceImage> images) {
        return enroll("追加样本", restClient.post()
                .uri("/v1/collections/{collectionId}/persons/{personId}/faces",
                        properties.getCollectionId(), personId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(imagesBody(images).build()));
    }

    /**
     * 删除 Person 下的一张样本。路径带 Person id，用别人的 faceId 删除只会得到 404。
     *
     * @param personId Person id
     * @param faceId FaceSample id
     * @return 删除结果
     */
    public DeleteFaceResult deleteFace(String personId, String faceId) {
        try {
            execute("删除样本", restClient.delete()
                    .uri("/v1/collections/{collectionId}/persons/{personId}/faces/{faceId}",
                            properties.getCollectionId(), personId, faceId));
            logger.info("[InsightFace] 已删除样本, personId: {}, faceId: {}", personId, faceId);
            return DeleteFaceResult.DELETED;
        } catch (InsightFaceApiException exception) {
            if (isPersonOrCollectionMissing(exception)) {
                return DeleteFaceResult.PERSON_NOT_FOUND;
            }
            // 404 时 face_not_found 表示样本不存在；422 时同名错误码含义不同，因此必须连同状态码判断
            if (exception.is(404, "face_not_found")) {
                return DeleteFaceResult.FACE_NOT_FOUND;
            }
            throw unexpected("删除样本", exception);
        }
    }

    /**
     * 用一张照片搜索人员库，limit 固定为 2，阈值使用召回阈值。
     *
     * @param image 已校验的照片
     * @return 按相似度降序的候选人员；Collection 不存在时为空
     * @throws InsightFaceApiException 422 face_not_found(无人脸)、422 invalid_image(无法解码)等
     */
    public List<SearchMatch> search(FaceImage image) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("image", image.bytes()).filename(image.filename()).contentType(image.mediaType());
        body.part("limit", String.valueOf(SEARCH_LIMIT));
        body.part("threshold", String.valueOf(properties.getRecallThreshold()));
        JsonNode response;
        try {
            response = execute("搜索", loginRestClient.post()
                    .uri("/v1/collections/{collectionId}/search", properties.getCollectionId())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build()));
        } catch (InsightFaceApiException exception) {
            // 还没有任何人注册时 Collection 可能不存在，等同于没有候选
            if (exception.is(404, "collection_not_found")) {
                return List.of();
            }
            throw exception;
        }
        List<SearchMatch> matches = new ArrayList<>();
        for (JsonNode match : response.path("matches")) {
            JsonNode person = match.path("person");
            matches.add(new SearchMatch(person.path("id").asString(), person.path("external_id").asString(""),
                    match.path("similarity").asDouble()));
        }
        return matches;
    }

    /**
     * 查询 Person 的全部样本。
     *
     * @param personId Person id
     * @return 样本列表；Person 或 Collection 不存在时为空
     */
    public Optional<List<FaceSample>> listFaces(String personId) {
        JsonNode response;
        try {
            response = execute("查询样本", restClient.get()
                    .uri("/v1/collections/{collectionId}/persons/{personId}/faces?limit={limit}",
                            properties.getCollectionId(), personId, FACE_PAGE_LIMIT));
        } catch (InsightFaceApiException exception) {
            if (isPersonOrCollectionMissing(exception)) {
                return Optional.empty();
            }
            throw unexpected("查询样本", exception);
        }
        List<FaceSample> samples = new ArrayList<>();
        for (JsonNode face : response.path("faces")) {
            samples.add(new FaceSample(face.path("id").asString(), parseTime(face.path("created_at"))));
        }
        return Optional.of(samples);
    }

    /**
     * 删除 Person 及其全部样本，幂等。
     *
     * @param personId Person id
     * @return true 表示本次确实删除了；false 表示 Person 或 Collection 本来就不存在
     * @throws ServiceException 人脸服务不可用(C0001)或超时(C0200)，此时调用方不能认为已删除
     */
    public boolean deletePerson(String personId) {
        try {
            execute("删除 Person", restClient.delete()
                    .uri("/v1/collections/{collectionId}/persons/{personId}",
                            properties.getCollectionId(), personId));
            logger.info("[InsightFace] 已删除 Person, personId: {}", personId);
            return true;
        } catch (InsightFaceApiException exception) {
            if (isPersonOrCollectionMissing(exception)) {
                logger.info("[InsightFace] 删除 Person 时不存在，视为成功, personId: {}", personId);
                return false;
            }
            throw unexpected("删除 Person", exception);
        }
    }

    /**
     * 把调用方不打算处理的业务错误转成 C0001，原始状态码和错误码写日志。
     *
     * @param action 动作描述
     * @param exception 业务错误
     * @return C0001 业务异常
     */
    public ServiceException unexpected(String action, InsightFaceApiException exception) {
        logger.error("[InsightFace] {}返回未预期的错误, status: {}, code: {}",
                action, exception.getStatus(), exception.getCode());
        return new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE);
    }

    private void createCollection(String collectionId) {
        Map<String, Object> search = new LinkedHashMap<>();
        search.put("profile", SEARCH_PROFILE);
        search.put("max_faces_per_person", properties.getMaxFacesPerPerson());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", collectionId);
        request.put("name", COLLECTION_NAME);
        // Collection 阈值写召回阈值；MES 每次搜索也会显式传入，判定阈值只在 MES 内比较
        request.put("threshold", properties.getRecallThreshold());
        request.put("search", search);
        try {
            execute("创建 Collection", restClient.post()
                    .uri("/v1/collections")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request));
            logger.info("[InsightFace] 已创建 Collection, collectionId: {}", collectionId);
        } catch (InsightFaceApiException exception) {
            // 并发创建时另一方先建好了，同样视为已确认
            if (!exception.is(409, "collection_exists")) {
                throw unexpected("创建 Collection", exception);
            }
            logger.info("[InsightFace] Collection 已由其他请求创建, collectionId: {}", collectionId);
        }
    }

    private EnrollResult enroll(String action, RestClient.RequestHeadersSpec<?> request) {
        try {
            JsonNode response = execute(action, request);
            List<String> faceIds = new ArrayList<>();
            for (JsonNode face : response.path("faces")) {
                faceIds.add(face.path("id").asString());
            }
            return new EnrollResult(faceIds, parseRejected(response.path("rejected_images")));
        } catch (InsightFaceApiException exception) {
            if (exception.is(422, "registration_failed")) {
                return new EnrollResult(List.of(), parseRejected(exception.getDetails().path("rejected_images")));
            }
            throw exception;
        }
    }

    private MultipartBodyBuilder imagesBody(List<FaceImage> images) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        for (FaceImage image : images) {
            body.part("images", image.bytes())
                    .filename(image.filename())
                    .contentType(image.mediaType());
        }
        body.part("review_mode", REVIEW_MODE);
        return body;
    }

    private List<RejectedImage> parseRejected(JsonNode rejectedImages) {
        List<RejectedImage> result = new ArrayList<>();
        for (JsonNode rejected : rejectedImages) {
            // 官方 index 从 0 开始，MES 对外从 1 开始
            int index = rejected.path("index").asInt() + 1;
            result.add(new RejectedImage(index, translateReason(rejected.path("reason").asString())));
        }
        return result;
    }

    private String translateReason(String code) {
        String reason = REJECT_REASONS.get(code);
        if (reason == null) {
            logger.warn("[InsightFace] 未知的照片拒绝原因码: {}", code);
            return UNKNOWN_REJECT_REASON;
        }
        return reason;
    }

    private LocalDateTime parseTime(JsonNode node) {
        String text = node.asString();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException offsetMissing) {
            try {
                return LocalDateTime.parse(text);
            } catch (DateTimeParseException exception) {
                logger.warn("[InsightFace] 无法解析时间: {}", text);
                return null;
            }
        }
    }

    private boolean isPersonOrCollectionMissing(InsightFaceApiException exception) {
        if (exception.is(404, "collection_not_found")) {
            collectionReady = false;
            return true;
        }
        return exception.is(404, "person_not_found");
    }

    /**
     * 发送请求并按状态码分类：2xx 返回响应体，业务 4xx 抛 {@link InsightFaceApiException}，
     * 其他情况抛 C0001 / C0200。
     */
    private JsonNode execute(String action, RestClient.RequestHeadersSpec<?> request) {
        RawResponse response;
        try {
            response = request.exchange((clientRequest, clientResponse) -> new RawResponse(
                    clientResponse.getStatusCode().value(),
                    clientResponse.getHeaders().getFirst(REQUEST_ID_HEADER),
                    StreamUtils.copyToByteArray(clientResponse.getBody())));
        } catch (ResourceAccessException exception) {
            throw translateIoFailure(action, exception);
        } catch (RestClientException exception) {
            logger.error("[InsightFace] {}请求失败: {}", action, exception.getMessage());
            throw new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE);
        }

        int status = response.status();
        if (status >= 200 && status < 300) {
            return readJson(response.body());
        }
        JsonNode error = readJson(response.body()).path("error");
        String code = error.path("code").asString("");
        if (status == 404 || status == 409 || status == 422) {
            logger.info("[InsightFace] {}返回业务错误, status: {}, code: {}, requestId: {}",
                    action, status, code, response.requestId());
            if ("collection_not_found".equals(code)) {
                collectionReady = false;
            }
            throw new InsightFaceApiException(status, code, error.path("details"));
        }
        if (status == 503 && error.path("details").path("write_committed").asBoolean(false)) {
            logger.error("[InsightFace] {}返回 503 但写入已提交, requestId: {}", action, response.requestId());
        } else {
            logger.error("[InsightFace] {}失败, status: {}, code: {}, requestId: {}",
                    action, status, code, response.requestId());
        }
        throw new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE);
    }

    private ServiceException translateIoFailure(String action, ResourceAccessException exception) {
        Throwable cause = exception.getCause();
        // HttpConnectTimeoutException 是 HttpTimeoutException 的子类，必须先判断：连不上按服务不可用处理
        if (cause instanceof HttpConnectTimeoutException) {
            logger.error("[InsightFace] {}连接超时", action);
            return new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE);
        }
        if (cause instanceof HttpTimeoutException) {
            logger.error("[InsightFace] {}读取超时", action);
            return new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_TIMEOUT);
        }
        logger.error("[InsightFace] {}连接失败: {}", action, exception.getMessage());
        return new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE);
    }

    private JsonNode readJson(byte[] body) {
        if (body == null || body.length == 0) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException exception) {
            logger.warn("[InsightFace] 响应不是合法 JSON, length: {}", body.length);
            return MissingNode.getInstance();
        }
    }

    private static JdkClientHttpRequestFactory createRequestFactory(InsightFaceProperties properties) {
        return createRequestFactory(properties.getConnectTimeout(), properties.getReadTimeout());
    }

    private static JdkClientHttpRequestFactory createRequestFactory(Duration connectTimeout, Duration readTimeout) {
        // 固定 HTTP/1.1：JDK 默认对 http:// 尝试 h2c 升级，没有必要
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    /** 读完并关闭连接后的原始响应。 */
    private record RawResponse(int status, String requestId, byte[] body) {
    }
}
