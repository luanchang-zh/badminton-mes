package com.badminton.mes.module.system.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
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

/** 登录搜索不能继承注册接口的 30 秒等待时间。 */
class InsightFaceLoginTimeoutTest {

    @Test
    @DisplayName("登录搜索面对无响应服务时在两秒附近返回超时")
    void loginSearchHasShortReadTimeout() throws Exception {
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
            properties.setReadTimeout(Duration.ofSeconds(30));
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
