package com.badminton.mes.module.system.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.system.config.InsightFaceProperties;
import com.badminton.mes.module.system.constants.SystemErrorCodeConstants;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import tools.jackson.databind.ObjectMapper;

/** 登录搜索允许等待较慢的识别结果，同时尊重配置中的更短读取超时。 */
class InsightFaceLoginTimeoutTest {

    @Test
    @DisplayName("登录搜索允许超过原两秒上限的正常响应")
    void loginSearchAcceptsSlowerResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                Thread.sleep(2200);
                byte[] body = "{\"matches\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            InsightFaceProperties properties = new InsightFaceProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            InsightFaceClient client = new InsightFaceClient(properties, new ObjectMapper());
            FaceImage image = new FaceImage("frame.jpg", new byte[] {1, 2, 3}, MediaType.IMAGE_JPEG);

            assertThat(client.search(image)).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("登录搜索继续遵守配置中更短的读取超时")
    void loginSearchRespectsConfiguredReadTimeout() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/", exchange -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            InsightFaceProperties properties = new InsightFaceProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setReadTimeout(Duration.ofMillis(250));
            InsightFaceClient client = new InsightFaceClient(properties, new ObjectMapper());
            FaceImage image = new FaceImage("frame.jpg", new byte[] {1, 2, 3}, MediaType.IMAGE_JPEG);
            long start = System.nanoTime();
            assertThatThrownBy(() -> client.search(image)).isInstanceOfSatisfying(ServiceException.class,
                    exception -> assertThat(exception.getErrorCode()).isEqualTo(SystemErrorCodeConstants.FACE_SERVICE_TIMEOUT));
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
}
