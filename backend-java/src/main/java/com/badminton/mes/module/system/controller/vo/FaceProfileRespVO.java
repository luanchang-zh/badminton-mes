package com.badminton.mes.module.system.controller.vo;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 本人人脸登录状态响应。
 *
 * @author xxy-lh
 * @date 2026/11/09
 */
@Data
public class FaceProfileRespVO {

    /** 是否存在有效绑定 */
    private Boolean enrolled;

    /** 当前账号能否使用人脸登录；所有已登录用户均为 true，与角色及登录方式无关 */
    private Boolean faceLoginAllowed;

    /** 样本数 */
    private Integer faceCount;

    /** 每人样本上限 */
    private Integer maxFaces;

    /** 样本列表，未注册时为空数组 */
    private List<Sample> samples;

    /** 一张人脸样本。 */
    @Data
    public static class Sample {

        /** InsightFace FaceSample id */
        private String faceId;

        /** 入库时间 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
    }
}
