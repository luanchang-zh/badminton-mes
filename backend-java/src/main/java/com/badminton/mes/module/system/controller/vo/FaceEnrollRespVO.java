package com.badminton.mes.module.system.controller.vo;

import java.util.List;

import lombok.Data;

/**
 * 人脸注册 / 追加样本响应。
 *
 * <p>全部照片被拒时同样以成功码返回，{@code acceptedCount=0}，前端按「注册未完成」处理，
 * 并用 {@code rejectedImages} 逐张提示原因。
 *
 * @author xxy-lh
 * @date 2026/11/09
 */
@Data
public class FaceEnrollRespVO {

    /** 本次成功入库的张数，可能为 0 */
    private Integer acceptedCount;

    /** 当前样本总数 */
    private Integer faceCount;

    /** 被拒绝的照片，可能为空 */
    private List<RejectedImage> rejectedImages;

    /** 一张被拒绝的照片。 */
    @Data
    public static class RejectedImage {

        /** 从 1 开始的上传序号 */
        private Integer index;

        /** 中文原因 */
        private String reason;
    }
}
