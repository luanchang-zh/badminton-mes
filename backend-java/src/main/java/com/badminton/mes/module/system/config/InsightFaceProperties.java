package com.badminton.mes.module.system.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * InsightFace 人脸服务配置，对应 {@code mes.insightface.*}。
 *
 * @author xxy-lh
 * @date 2026/11/08
 */
@Data
@Validated
@ConfigurationProperties(prefix = "mes.insightface")
public class InsightFaceProperties {

    /** 人脸服务基础地址，Docker 部署为 http://insightface:8080 */
    @NotBlank
    private String baseUrl = "http://127.0.0.1:18097";

    /** API Key；为空表示人脸服务关闭认证，不发送 Authorization 头 */
    private String apiKey = "";

    /** Collection id */
    @NotBlank
    private String collectionId = "course_users";

    /** MES 判定阈值：第一名 similarity 低于此值不发会话 */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double threshold = 0.4;

    /** 召回阈值：每次搜索随请求传给 InsightFace，必须低于判定阈值 */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double recallThreshold = 0.2;

    /** 第一名与第二名(不同人员)的最小分差 */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double margin = 0.05;

    /** 每人样本上限 */
    @Min(1)
    private int maxFacesPerPerson = 5;

    /** TCP 连接超时 */
    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** 响应读取超时 */
    @NotNull
    private Duration readTimeout = Duration.ofSeconds(30);

    /** 人脸登录限流配置 */
    @Valid
    private FaceLogin faceLogin = new FaceLogin();

    /** 人脸登录按来源 IP 的失败限流。 */
    @Data
    public static class FaceLogin {

        /** 固定窗口内允许的最大失败次数 */
        @Min(1)
        private int ipFailMax = 20;

        /** 固定窗口时长，首次失败时开始计时 */
        @NotNull
        private Duration ipFailWindow = Duration.ofMinutes(15);
    }

    /** 召回阈值高于判定阈值时，分差校验看不到第二名，属于配置错误，启动即失败。 */
    @PostConstruct
    void validateThresholds() {
        if (recallThreshold >= threshold) {
            throw new IllegalStateException(
                    "mes.insightface.recall-threshold 必须小于 mes.insightface.threshold");
        }
    }
}
