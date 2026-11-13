package com.badminton.mes.module.system.controller;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.badminton.mes.common.exception.GlobalExceptionHandler;
import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.common.security.AuthInterceptor;
import com.badminton.mes.common.security.LoginSessionReader;
import com.badminton.mes.common.security.LoginUser;
import com.badminton.mes.common.security.SecurityWebConfig;
import com.badminton.mes.module.system.constants.SystemErrorCodeConstants;
import com.badminton.mes.module.system.controller.vo.AuthLoginRespVO;
import com.badminton.mes.module.system.controller.vo.FaceEnrollRespVO;
import com.badminton.mes.module.system.controller.vo.FaceProfileRespVO;
import com.badminton.mes.module.system.service.AuthService;
import com.badminton.mes.module.system.service.FaceService;
import com.badminton.mes.module.system.service.RoleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 真实 Tomcat HTTP 测试：使用 application.yml 的上传限制，保留真实鉴权拦截器。 */
@SpringBootTest(classes = FaceApiHttpTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FaceApiHttpTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
    @Import({FaceController.class, AuthController.class, SecurityWebConfig.class,
            AuthInterceptor.class, GlobalExceptionHandler.class})
    static class Application {
    }

    private static final String BASE = "/api/system/face";
    private static final String LOGIN = SecurityWebConfig.FACE_LOGIN_PATH;
    private static final String BOUNDARY = "mes-face-http-test";
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0};
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FaceService faceService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private LoginSessionReader loginSessionReader;

    private LoginUser user;

    @BeforeEach
    void setUp() {
        user = new LoginUser();
        user.setUserId(7L);
        user.setUserNo("face_http_test");
        user.setRoleCodes(List.of("OPERATOR"));
        when(loginSessionReader.resolve("test-session")).thenReturn(Optional.of(user));
    }

    @ParameterizedTest
    @CsvSource({"POST,/enroll", "POST,/reenroll", "GET,/profile", "POST,/samples",
            "DELETE,/samples/face-1", "DELETE,/enrollment"})
    void managementRequiresLogin(String method, String path) throws Exception {
        assertResponse(send(method, BASE + path, false, multipart("images", List.of(JPEG), "true")), 401, "A0230");
        verifyNoInteractions(faceService);
    }

    @Test
    void anonymousLoginReturnsExistingSessionContract() throws Exception {
        AuthLoginRespVO login = new AuthLoginRespVO();
        login.setToken("new-session");
        login.setUserId(7L);
        login.setUserNo("face_http_test");
        login.setUserName("人脸测试");
        login.setRoleCodes(List.of("OPERATOR"));
        login.setWechatBound(true);
        login.setWechatBindingTime(LocalDateTime.of(2026, 11, 11, 10, 0));
        login.setWechatLastLoginTime(LocalDateTime.of(2026, 11, 11, 11, 0));
        when(authService.faceLogin(any(), eq("127.0.0.1"))).thenReturn(login);

        JsonNode data = assertResponse(send("POST", LOGIN, false, multipart("image", List.of(JPEG), null)),
                200, "00000").path("data");

        assertThat(data.path("token").asString()).isEqualTo("new-session");
        assertThat(data.path("userId").asLong()).isEqualTo(7L);
        assertThat(data.path("userNo").asString()).isEqualTo("face_http_test");
        assertThat(data.path("userName").asString()).isEqualTo("人脸测试");
        assertThat(data.path("roleCodes").get(0).asString()).isEqualTo("OPERATOR");
        assertThat(data.path("wechatBound").asBoolean()).isTrue();
        assertThat(data.path("wechatBindingTime").asString()).isEqualTo("2026-11-11 10:00:00");
        assertThat(data.path("wechatLastLoginTime").asString()).isEqualTo("2026-11-11 11:00:00");
        verifyNoInteractions(loginSessionReader);
    }

    @Test
    void enrollmentForwardsConsentAndFilesForCurrentUser() throws Exception {
        FaceEnrollRespVO result = new FaceEnrollRespVO();
        result.setAcceptedCount(1);
        result.setFaceCount(1);
        FaceEnrollRespVO.RejectedImage rejected = new FaceEnrollRespVO.RejectedImage();
        rejected.setIndex(2);
        rejected.setReason("未检测到人脸");
        result.setRejectedImages(List.of(rejected));
        when(faceService.enroll(eq(user), any(), eq(true))).thenReturn(result);

        JsonNode data = assertResponse(send("POST", BASE + "/enroll?userId=999&personId=user-999", true,
                multipart("images", List.of(JPEG, JPEG), "true")), 200, "00000").path("data");

        assertThat(data.path("acceptedCount").asInt()).isEqualTo(1);
        assertThat(data.path("faceCount").asInt()).isEqualTo(1);
        assertThat(data.path("rejectedImages").get(0).path("index").asInt()).isEqualTo(2);
        verify(faceService).enroll(eq(user), argThat(files -> files.size() == 2), eq(true));
    }

    @Test
    void reenrollmentForwardsConsentAndFilesForCurrentUser() throws Exception {
        user.setRoleCodes(List.of("ADMIN", "OPERATOR"));
        FaceEnrollRespVO result = new FaceEnrollRespVO();
        result.setAcceptedCount(2);
        result.setFaceCount(2);
        result.setRejectedImages(List.of());
        when(faceService.reenroll(eq(user), any(), eq(true))).thenReturn(result);

        JsonNode data = assertResponse(send("POST", BASE + "/reenroll?userId=999&personId=user-999", true,
                multipart("images", List.of(JPEG, JPEG), "true")), 200, "00000").path("data");

        assertThat(data.path("acceptedCount").asInt()).isEqualTo(2);
        assertThat(data.path("faceCount").asInt()).isEqualTo(2);
        verify(faceService).reenroll(eq(user), argThat(files -> files.size() == 2), eq(true));
    }

    @Test
    void reenrollmentFailureReturnsRetryableError() throws Exception {
        when(faceService.reenroll(eq(user), any(), eq(true)))
                .thenThrow(new ServiceException(SystemErrorCodeConstants.FACE_OPERATION_IN_PROGRESS));

        assertResponse(send("POST", BASE + "/reenroll", true,
                multipart("images", List.of(JPEG, JPEG), "true")), 400, "A0503");
    }

    @Test
    void profileOnlyUsesCurrentUser() throws Exception {
        FaceProfileRespVO profile = new FaceProfileRespVO();
        profile.setEnrolled(false);
        profile.setFaceLoginAllowed(true);
        profile.setFaceCount(0);
        profile.setMaxFaces(5);
        profile.setSamples(List.of());
        when(faceService.getProfile(user)).thenReturn(profile);

        JsonNode data = assertResponse(send("GET", BASE + "/profile?userId=999", true, null),
                200, "00000").path("data");
        assertThat(data.path("enrolled").asBoolean()).isFalse();
        assertThat(data.path("faceLoginAllowed").asBoolean()).isTrue();
        assertThat(data.path("maxFaces").asInt()).isEqualTo(5);
        assertThat(data.path("samples").isArray()).isTrue();
        verify(faceService).getProfile(user);
    }

    @Test
    void samplesAndDisableOnlyUseCurrentUser() throws Exception {
        FaceEnrollRespVO result = new FaceEnrollRespVO();
        result.setAcceptedCount(1);
        result.setFaceCount(3);
        result.setRejectedImages(List.of());
        when(faceService.addSamples(eq(user), any())).thenReturn(result);

        assertResponse(send("POST", BASE + "/samples", true, multipart("images", List.of(JPEG), null)), 200, "00000");
        assertResponse(send("DELETE", BASE + "/samples/face-1", true, null), 200, "00000");
        assertResponse(send("DELETE", BASE + "/enrollment?userId=999", true, null), 200, "00000");
        verify(faceService).addSamples(eq(user), argThat(files -> files.size() == 1));
        verify(faceService).deleteSample(user, "face-1");
        verify(faceService).removeEnrollment(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {LOGIN, BASE + "/enroll", BASE + "/reenroll", BASE + "/samples"})
    void missingFileIsParameterError(String path) throws Exception {
        assertResponse(send("POST", path, true, multipart("wrong_field", List.of(JPEG), "true")), 400, "A0400");
        verifyNoInteractions(faceService, authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {BASE + "/enroll", BASE + "/reenroll"})
    void invalidConsentIsParameterError(String path) throws Exception {
        assertResponse(send("POST", path, true,
                multipart("images", List.of(JPEG, JPEG), "invalid")), 400, "A0400");
        verifyNoInteractions(faceService);
    }

    @Test
    void invalidSampleIdIsParameterError() throws Exception {
        assertResponse(send("DELETE", BASE + "/samples/invalid.id", true, null), 400, "A0400");
        verifyNoInteractions(faceService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LOGIN, BASE + "/enroll", BASE + "/reenroll", BASE + "/samples"})
    void wrongContentTypeIsParameterError(String path) throws Exception {
        HttpRequest request = request("POST", path, true, "{}".getBytes(StandardCharsets.UTF_8), "application/json");
        assertResponse(HTTP.send(request, HttpResponse.BodyHandlers.ofString()), 400, "A0400");
        verifyNoInteractions(faceService, authService);
    }

    @Test
    void malformedMultipartIsParameterError() throws Exception {
        HttpRequest request = request("POST", LOGIN, false, JPEG, "multipart/form-data");
        assertResponse(HTTP.send(request, HttpResponse.BodyHandlers.ofString()), 400, "A0400");
        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {LOGIN, BASE + "/enroll", BASE + "/reenroll", BASE + "/samples"})
    void elevenMegabyteImageReturnsJsonUploadError(String path) throws Exception {
        byte[] oversized = new byte[11 * 1024 * 1024];
        System.arraycopy(JPEG, 0, oversized, 0, JPEG.length);
        assertResponse(send("POST", path, true, multipart(path.equals(LOGIN) ? "image" : "images",
                List.of(oversized), "true")), 400, "A0702");
        verifyNoInteractions(faceService, authService);
    }

    @Test
    void serviceFailureReturnsBadGateway() throws Exception {
        when(authService.faceLogin(any(), any())).thenThrow(new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_TIMEOUT));
        assertResponse(send("POST", LOGIN, false, multipart("image", List.of(JPEG), null)), 502, "C0200");
    }

    @Test
    void foreignSampleReturnsBusinessError() throws Exception {
        doThrow(new ServiceException(SystemErrorCodeConstants.FACE_SAMPLE_NOT_FOUND))
                .when(faceService).deleteSample(user, "foreign-face");
        assertResponse(send("DELETE", BASE + "/samples/foreign-face", true, null), 400, "A0402");
    }

    private HttpResponse<String> send(String method, String path, boolean loggedIn, byte[] body) throws Exception {
        return HTTP.send(request(method, path, loggedIn, body, "multipart/form-data; boundary=" + BOUNDARY),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String method, String path, boolean loggedIn, byte[] body, String contentType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15));
        if (loggedIn) {
            builder.header("Authorization", "Bearer test-session");
        }
        if (body != null) {
            builder.header("Content-Type", contentType);
        }
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
    }

    private JsonNode assertResponse(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.path("code").asString()).as(response.body()).isEqualTo(code);
        assertThat(response.body()).doesNotContain("similarity", "external_id", "embedding", "request_id", "apiKey");
        return json;
    }

    private static byte[] multipart(String field, List<byte[]> images, String consent) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] image : images) {
            body.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + field
                    + "\"; filename=\"photo.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.writeBytes(image);
            body.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        if (consent != null) {
            body.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"consent\"\r\n\r\n"
                    + consent + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }
}
