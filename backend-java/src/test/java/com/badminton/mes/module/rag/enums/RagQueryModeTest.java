package com.badminton.mes.module.rag.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 检索模式枚举测试。
 *
 * <p>白名单只存在于枚举里（VO 不再写正则），因此这里只校验
 * 「取值集合与上游协议一致」：多一个会被上游 422，少一个前端就用不了。
 */
class RagQueryModeTest {

    @ParameterizedTest
    @ValueSource(strings = {"naive", "local", "global", "hybrid", "mix"})
    @DisplayName("合法模式可解析")
    void shouldParseKnownModes(String rawMode) {
        assertThat(RagQueryMode.parse(rawMode).getValue()).isEqualTo(rawMode);
    }

    @Test
    @DisplayName("大小写与首尾空白被归一化")
    void shouldNormalizeCaseAndWhitespace() {
        assertThat(RagQueryMode.parse("  MIX ")).isEqualTo(RagQueryMode.MIX);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("未指定模式时使用缺省模式")
    void shouldFallBackToDefault(String rawMode) {
        assertThat(RagQueryMode.parse(rawMode)).isEqualTo(RagQueryMode.DEFAULT_MODE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bypass", "naivee", "semantic", "0"})
    @DisplayName("未知模式抛出参数错误而不是静默降级")
    void shouldRejectUnknownModes(String rawMode) {
        assertThatThrownBy(() -> RagQueryMode.parse(rawMode))
                .isInstanceOf(ServiceException.class)
                .extracting(error -> ((ServiceException) error).getErrorCode())
                .isEqualTo(RagErrorCodeConstants.QUERY_MODE_INVALID);
    }

    @Test
    @DisplayName("取值集合与上游支持的五种模式完全一致")
    void valueSetShouldMatchUpstreamProtocol() {
        // 上游 /query 的 mode 支持 naive/local/global/hybrid/mix（另有 bypass，本系统不开放）
        assertThat(RagQueryMode.values())
                .extracting(RagQueryMode::getValue)
                .containsExactlyInAnyOrder("naive", "local", "global", "hybrid", "mix");
    }
}
