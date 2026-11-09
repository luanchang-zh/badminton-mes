package com.badminton.mes.module.system.client;

import java.time.LocalDateTime;
import java.util.List;

/**
 * InsightFace 返回值在 MES 内部的精简表示，只保留业务需要的字段，不含相似度以外的特征信息。
 *
 * @author xxy-lh
 * @date 2026/11/08
 */
public final class InsightFaceModels {

    /**
     * 一张被拒绝的照片。
     *
     * @param index 从 1 开始的上传序号
     * @param reason 中文原因
     */
    public record RejectedImage(int index, String reason) {
    }

    /**
     * 创建 Person 或追加样本的结果。
     *
     * @param faceIds 本次成功入库的 FaceSample id
     * @param rejectedImages 被拒绝的照片
     */
    public record EnrollResult(List<String> faceIds, List<RejectedImage> rejectedImages) {

        public EnrollResult {
            faceIds = List.copyOf(faceIds);
            rejectedImages = List.copyOf(rejectedImages);
        }

        /** 是否没有任何照片入库；创建 Person 时意味着 Person 未被创建。 */
        public boolean allRejected() {
            return faceIds.isEmpty();
        }
    }

    /**
     * 已入库的一张人脸样本。
     *
     * @param faceId FaceSample id
     * @param createdAt 入库时间(本地时区)
     */
    public record FaceSample(String faceId, LocalDateTime createdAt) {
    }

    private InsightFaceModels() {
    }
}
