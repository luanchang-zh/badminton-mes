package com.badminton.mes.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 图片魔数识别。 */
class ImageTypeUtilsTest {

    @Test
    @DisplayName("识别 JPEG、PNG、WebP 文件头")
    void detectsSupportedTypes() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0};
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(ImageTypeUtils.detect(jpeg)).contains(MediaType.IMAGE_JPEG);
        assertThat(ImageTypeUtils.detect(png)).contains(MediaType.IMAGE_PNG);
        assertThat(ImageTypeUtils.detect(webp)).contains(ImageTypeUtils.IMAGE_WEBP);
        assertThat(ImageTypeUtils.extension(ImageTypeUtils.IMAGE_WEBP)).isEqualTo("webp");
    }

    @Test
    @DisplayName("文本、空文件、只有 RIFF 头的 WAV 都不算图片")
    void rejectsOtherContent() {
        assertThat(ImageTypeUtils.detect("hello.jpg".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageTypeUtils.detect(new byte[0])).isEmpty();
        assertThat(ImageTypeUtils.detect(null)).isEmpty();
        assertThat(ImageTypeUtils.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
    }
}
