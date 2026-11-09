package com.badminton.mes.common.util;

import java.util.Optional;

import org.springframework.http.MediaType;

/**
 * 按文件头魔数识别图片类型。
 *
 * <p>只认 JPEG、PNG、WebP 三种。浏览器提交的 {@code Content-Type} 和扩展名都可以随意伪造，
 * 不作为判断依据。
 *
 * @author xxy-lh
 * @date 2026/11/09
 */
public final class ImageTypeUtils {

    /** WebP 的 MIME 类型，Spring 的 MediaType 没有内置常量 */
    public static final MediaType IMAGE_WEBP = MediaType.parseMediaType("image/webp");

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
    };

    /** WebP 文件头：RIFF + 4 字节长度 + WEBP */
    private static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};

    private static final byte[] WEBP_MAGIC = {'W', 'E', 'B', 'P'};

    private static final int WEBP_MAGIC_OFFSET = 8;

    /**
     * 识别图片类型。
     *
     * @param bytes 文件内容
     * @return 识别出的类型；不是 JPEG、PNG、WebP 时为空
     */
    public static Optional<MediaType> detect(byte[] bytes) {
        if (bytes == null) {
            return Optional.empty();
        }
        if (startsWith(bytes, 0, JPEG_MAGIC)) {
            return Optional.of(MediaType.IMAGE_JPEG);
        }
        if (startsWith(bytes, 0, PNG_MAGIC)) {
            return Optional.of(MediaType.IMAGE_PNG);
        }
        if (startsWith(bytes, 0, RIFF_MAGIC) && startsWith(bytes, WEBP_MAGIC_OFFSET, WEBP_MAGIC)) {
            return Optional.of(IMAGE_WEBP);
        }
        return Optional.empty();
    }

    /**
     * 按类型返回规范扩展名。
     *
     * @param mediaType {@link #detect(byte[])} 识别出的类型
     * @return 不带点的扩展名
     */
    public static String extension(MediaType mediaType) {
        if (MediaType.IMAGE_PNG.equalsTypeAndSubtype(mediaType)) {
            return "png";
        }
        if (IMAGE_WEBP.equalsTypeAndSubtype(mediaType)) {
            return "webp";
        }
        return "jpg";
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] magic) {
        if (bytes.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private ImageTypeUtils() {
    }
}
