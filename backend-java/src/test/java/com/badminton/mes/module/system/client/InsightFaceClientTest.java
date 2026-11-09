package com.badminton.mes.module.system.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Optional;

import com.badminton.mes.common.core.ErrorCode;
import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.system.client.InsightFaceModels.EnrollResult;
import com.badminton.mes.module.system.client.InsightFaceModels.FaceSample;
import com.badminton.mes.module.system.client.InsightFaceModels.DeleteFaceResult;
import com.badminton.mes.module.system.client.InsightFaceModels.RejectedImage;
import com.badminton.mes.module.system.client.InsightFaceModels.SearchMatch;
import com.badminton.mes.module.system.config.InsightFaceProperties;
import com.badminton.mes.module.system.constants.SystemErrorCodeConstants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

/** InsightFace 客户端：状态码分类、Collection 懒初始化、拒绝原因映射。 */
class InsightFaceClientTest {

    private static final String BASE = "http://insightface.test";

    private static final String COLLECTION = BASE + "/v1/collections/course_users";

    private static final String PERSONS = COLLECTION + "/persons";

    private static final String ERROR_TEMPLATE =
            "{\"error\":{\"code\":\"%s\",\"message\":\"m\",\"details\":%s},\"request_id\":\"r\"}";

    private static final List<FaceImage> IMAGES = List.of(
            new FaceImage("photo-1.jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}, MediaType.IMAGE_JPEG),
            new FaceImage("photo-2.jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 2}, MediaType.IMAGE_JPEG));

    private MockRestServiceServer server;

    private InsightFaceClient client;

    @BeforeEach
    void setUp() {
        InsightFaceProperties properties = new InsightFaceProperties();
        properties.setBaseUrl(BASE);
        properties.setApiKey("test-key");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new InsightFaceClient(properties, new ObjectMapper(), builder);
    }

    @Test
    @DisplayName("懒初始化：Collection 不存在时用召回阈值创建，确认后不再请求")
    void ensureCollectionCreatesOnceWhenMissing() {
        server.expect(once(), requestTo(COLLECTION)).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andRespond(error(HttpStatus.NOT_FOUND, "collection_not_found", "{}"));
        server.expect(once(), requestTo(BASE + "/v1/collections")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"id\":\"course_users\",\"threshold\":0.2,"
                        + "\"search\":{\"profile\":\"fp32_v1\",\"max_faces_per_person\":5}}"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{}"));

        client.ensureCollection();
        client.ensureCollection();

        server.verify();
    }

    @Test
    @DisplayName("懒初始化：并发创建返回 409 collection_exists 视为已确认")
    void ensureCollectionToleratesConcurrentCreate() {
        server.expect(requestTo(COLLECTION)).andRespond(error(HttpStatus.NOT_FOUND, "collection_not_found", "{}"));
        server.expect(requestTo(BASE + "/v1/collections"))
                .andRespond(error(HttpStatus.CONFLICT, "collection_exists", "{}"));

        client.ensureCollection();

        server.verify();
    }

    @Test
    @DisplayName("创建 Person：部分成功时返回 faceId，拒绝序号从 1 开始并映射中文，未知原因码统一提示")
    void createPersonPartiallyAccepted() {
        expectCollectionExists();
        server.expect(requestTo(PERSONS)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("name=\"review_mode\"")))
                .andExpect(content().string(containsString("filename=\"photo-1.jpg\"")))
                .andExpect(content().string(containsString("user-7")))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(
                        "{\"person\":{\"id\":\"user-7\"},\"faces\":[{\"id\":\"f1\"}],"
                                + "\"rejected_images\":[{\"index\":1,\"filename\":\"photo-2.jpg\","
                                + "\"reason\":\"extreme_pose\"}]}"));

        EnrollResult result = client.createPerson("user-7", "7", "张三", IMAGES);

        assertThat(result.faceIds()).containsExactly("f1");
        assertThat(result.rejectedImages()).containsExactly(new RejectedImage(2, "侧脸或低头角度过大，请正对镜头"));
        server.verify();
    }

    @Test
    @DisplayName("创建 Person：422 registration_failed 返回全部拒绝结果而不是异常")
    void createPersonAllRejected() {
        expectCollectionExists();
        server.expect(requestTo(PERSONS)).andRespond(error(HttpStatus.UNPROCESSABLE_CONTENT, "registration_failed",
                "{\"rejected_images\":[{\"index\":0,\"reason\":\"face_not_found\"},"
                        + "{\"index\":1,\"reason\":\"liveness_fake\"}]}"));

        EnrollResult result = client.createPerson("user-7", "7", "张三", IMAGES);

        assertThat(result.allRejected()).isTrue();
        assertThat(result.rejectedImages()).containsExactly(
                new RejectedImage(1, "未检测到人脸"), new RejectedImage(2, "照片不符合要求"));
    }

    @Test
    @DisplayName("创建 Person：409 person_exists 原样交给调用方")
    void createPersonConflictPassedThrough() {
        expectCollectionExists();
        server.expect(requestTo(PERSONS)).andRespond(error(HttpStatus.CONFLICT, "person_exists", "{}"));

        assertThatThrownBy(() -> client.createPerson("user-7", "7", "张三", IMAGES))
                .isInstanceOfSatisfying(InsightFaceApiException.class,
                        exception -> assertThat(exception.is(409, "person_exists")).isTrue());
    }

    @Test
    @DisplayName("401、5xx 归为人脸服务不可用 C0001")
    void unauthorizedAndServerErrorAreUnavailable() {
        server.expect(requestTo(COLLECTION)).andRespond(error(HttpStatus.UNAUTHORIZED, "unauthorized", "{}"));
        assertServiceError(client::ensureCollection, SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE);

        server.reset();
        server.expect(requestTo(COLLECTION)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertServiceError(client::ensureCollection, SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("读超时归为 C0200；连接超时、连接拒绝归为 C0001")
    void ioFailuresAreClassified() {
        server.expect(requestTo(COLLECTION)).andRespond(withException(new HttpTimeoutException("timed out")));
        assertServiceError(client::ensureCollection, SystemErrorCodeConstants.FACE_SERVICE_TIMEOUT);

        server.reset();
        server.expect(requestTo(COLLECTION))
                .andRespond(withException(new HttpConnectTimeoutException("connect timed out")));
        assertServiceError(client::ensureCollection, SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE);

        server.reset();
        server.expect(requestTo(COLLECTION)).andRespond(withException(new ConnectException("refused")));
        assertServiceError(client::ensureCollection, SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("删除 Person：204 返回 true，404 person_not_found 视为成功返回 false")
    void deletePersonIsIdempotent() {
        server.expect(requestTo(PERSONS + "/user-7")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(requestTo(PERSONS + "/user-7"))
                .andRespond(error(HttpStatus.NOT_FOUND, "person_not_found", "{}"));

        assertThat(client.deletePerson("user-7")).isTrue();
        assertThat(client.deletePerson("user-7")).isFalse();
    }

    @Test
    @DisplayName("查询样本：解析 id 与 created_at；Person 不存在返回空")
    void listFaces() {
        server.expect(requestTo(PERSONS + "/user-7/faces?limit=100")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"faces\":[{\"id\":\"f1\",\"created_at\":\"2026-11-09T02:00:00+00:00\"}],"
                        + "\"next_cursor\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(PERSONS + "/user-8/faces?limit=100"))
                .andRespond(error(HttpStatus.NOT_FOUND, "person_not_found", "{}"));

        Optional<List<FaceSample>> samples = client.listFaces("user-7");

        assertThat(samples).hasValueSatisfying(list -> {
            assertThat(list).extracting(FaceSample::faceId).containsExactly("f1");
            assertThat(list.getFirst().createdAt()).isNotNull();
        });
        assertThat(client.listFaces("user-8")).isEmpty();
    }

    @Test
    @DisplayName("搜索：limit=2、threshold 为召回阈值，解析 person id、external_id 和相似度")
    void searchParsesMatches() {
        server.expect(requestTo(COLLECTION + "/search")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("name=\"limit\"")))
                .andExpect(content().string(containsString("0.2")))
                .andExpect(content().string(containsString("filename=\"photo-1.jpg\"")))
                .andRespond(withSuccess("{\"matches\":[{\"person\":{\"id\":\"user-7\",\"external_id\":\"7\"},"
                        + "\"similarity\":0.71,\"matched_face_id\":\"f1\"}]}", MediaType.APPLICATION_JSON));

        List<SearchMatch> matches = client.search(IMAGES.getFirst());

        assertThat(matches).containsExactly(new SearchMatch("user-7", "7", 0.71));
    }

    @Test
    @DisplayName("搜索：Collection 不存在视为没有候选；422 无人脸原样交给调用方")
    void searchMissingCollectionAndNoFace() {
        server.expect(requestTo(COLLECTION + "/search"))
                .andRespond(error(HttpStatus.NOT_FOUND, "collection_not_found", "{}"));
        server.expect(requestTo(COLLECTION + "/search"))
                .andRespond(error(HttpStatus.UNPROCESSABLE_CONTENT, "face_not_found", "{}"));

        assertThat(client.search(IMAGES.getFirst())).isEmpty();
        assertThatThrownBy(() -> client.search(IMAGES.getFirst()))
                .isInstanceOfSatisfying(InsightFaceApiException.class,
                        exception -> assertThat(exception.is(422, "face_not_found")).isTrue());
    }

    @Test
    @DisplayName("追加样本：部分成功与全部被拒都返回结果；样本上限 409 原样交给调用方")
    void addFaces() {
        server.expect(requestTo(PERSONS + "/user-7/faces")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"faces\":[{\"id\":\"f3\"}],\"rejected_images\":[]}"));
        server.expect(requestTo(PERSONS + "/user-7/faces"))
                .andRespond(error(HttpStatus.UNPROCESSABLE_CONTENT, "registration_failed",
                        "{\"rejected_images\":[{\"index\":0,\"reason\":\"low_quality\"}]}"));
        server.expect(requestTo(PERSONS + "/user-7/faces"))
                .andRespond(error(HttpStatus.CONFLICT, "person_face_limit_exceeded", "{}"));

        assertThat(client.addFaces("user-7", IMAGES).faceIds()).containsExactly("f3");
        assertThat(client.addFaces("user-7", IMAGES).rejectedImages())
                .containsExactly(new RejectedImage(1, "照片模糊或光线不佳，请重拍"));
        assertThatThrownBy(() -> client.addFaces("user-7", IMAGES))
                .isInstanceOfSatisfying(InsightFaceApiException.class,
                        exception -> assertThat(exception.is(409, "person_face_limit_exceeded")).isTrue());
    }

    @Test
    @DisplayName("删除样本：204、404 face_not_found、404 person_not_found 分别对应三种结果")
    void deleteFace() {
        String url = PERSONS + "/user-7/faces/f1";
        server.expect(requestTo(url)).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(requestTo(url)).andRespond(error(HttpStatus.NOT_FOUND, "face_not_found", "{}"));
        server.expect(requestTo(url)).andRespond(error(HttpStatus.NOT_FOUND, "person_not_found", "{}"));

        assertThat(client.deleteFace("user-7", "f1")).isEqualTo(DeleteFaceResult.DELETED);
        assertThat(client.deleteFace("user-7", "f1")).isEqualTo(DeleteFaceResult.FACE_NOT_FOUND);
        assertThat(client.deleteFace("user-7", "f1")).isEqualTo(DeleteFaceResult.PERSON_NOT_FOUND);
    }

    @Test
    @DisplayName("未配置 API Key 时不发送 Authorization 头")
    void noAuthorizationHeaderWithoutKey() {
        InsightFaceProperties properties = new InsightFaceProperties();
        properties.setBaseUrl(BASE);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer noKeyServer = MockRestServiceServer.bindTo(builder).build();
        InsightFaceClient noKeyClient = new InsightFaceClient(properties, new ObjectMapper(), builder);
        noKeyServer.expect(requestTo(COLLECTION))
                .andExpect(request -> assertThat(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION))
                        .isFalse())
                .andExpect(content().string(not(containsString("Bearer"))))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        noKeyClient.ensureCollection();

        noKeyServer.verify();
    }

    private void expectCollectionExists() {
        server.expect(requestTo(COLLECTION)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    }

    private static org.springframework.test.web.client.ResponseCreator error(
            HttpStatus status, String code, String details) {
        return withStatus(status).contentType(MediaType.APPLICATION_JSON)
                .body(ERROR_TEMPLATE.formatted(code, details));
    }

    private static void assertServiceError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
