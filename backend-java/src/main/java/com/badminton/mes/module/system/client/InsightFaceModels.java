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

    /**
     * 搜索命中的一个人员。InsightFace 已按 Person 汇总，取该人员最高的样本相似度。
     *
     * @param personId Person id
     * @param externalId 注册时写入的 external_id，即 MES 用户主键
     * @param similarity cosine 相似度，只用于判定和写日志，不返回给前端
     */
    public record SearchMatch(String personId, String externalId, double similarity) {
    }

    /** 删除单张样本的结果。 */
    public enum DeleteFaceResult {

        /** 已删除 */
        DELETED,

        /** 样本不存在，或不属于该 Person */
        FACE_NOT_FOUND,

        /** Person 或 Collection 不存在 */
        PERSON_NOT_FOUND
    }

    private InsightFaceModels() {
    }
}
