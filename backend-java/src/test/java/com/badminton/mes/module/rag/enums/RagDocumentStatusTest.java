package com.badminton.mes.module.rag.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 文档状态归一化测试。
 *
 * <p>重点在「上游多种命名都能识别」与「未知状态不炸」：
 * 前者不测会导致状态判断失效，后者不测会让上游新增状态直接引发 500。
 */
class RagDocumentStatusTest {

    @ParameterizedTest
    @ValueSource(strings = {"processed", "completed", "success", "succeeded", "done", "finished"})
    @DisplayName("各种表示完成的上游状态都归一化为已完成")
    void shouldNormalizeCompletedStates(String rawStatus) {
        assertThat(RagDocumentStatus.fromUpstream(rawStatus))
                .isEqualTo(RagDocumentStatus.COMPLETED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "processing", "queued", "running", "waiting"})
    @DisplayName("各种表示进行中的上游状态都归一化为处理中")
    void shouldNormalizeProcessingStates(String rawStatus) {
        assertThat(RagDocumentStatus.fromUpstream(rawStatus))
                .isEqualTo(RagDocumentStatus.PROCESSING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"failed", "error", "cancelled", "canceled", "aborted"})
    @DisplayName("各种表示失败的上游状态都归一化为失败")
    void shouldNormalizeFailedStates(String rawStatus) {
        assertThat(RagDocumentStatus.fromUpstream(rawStatus))
                .isEqualTo(RagDocumentStatus.FAILED);
    }

    @Test
    @DisplayName("大小写与首尾空白不影响识别")
    void shouldIgnoreCaseAndWhitespace() {
        assertThat(RagDocumentStatus.fromUpstream("  PROCESSED  "))
                .isEqualTo(RagDocumentStatus.COMPLETED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "unknown-state", "totally_new_status"})
    @DisplayName("未知或空状态归入未知，且不抛异常")
    void shouldFallbackToUnknown(String rawStatus) {
        assertThat(RagDocumentStatus.fromUpstream(rawStatus))
                .isEqualTo(RagDocumentStatus.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"parsing", "analyzing", "preprocessed", "PARSING", " Analyzing "})
    @DisplayName("上游解析流水线的中间态归为处理中，而不是未知")
    void shouldTreatParsingStagesAsProcessing(String rawStatus) {
        // 这些状态在大文件解析时会持续数分钟，归成 UNKNOWN 会让前端显示「未知」并误判为异常
        assertThat(RagDocumentStatus.fromUpstream(rawStatus))
                .isEqualTo(RagDocumentStatus.PROCESSING);
    }
}
