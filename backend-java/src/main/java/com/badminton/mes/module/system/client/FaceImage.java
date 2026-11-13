package com.badminton.mes.module.system.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badminton.mes.common.core.GlobalErrorCodeConstants;
import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.common.util.ImageTypeUtils;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

/**
 * 已通过文件头魔数校验的人脸照片。
 *
 * <p>文件名由 MES 按上传顺序统一生成为 {@code photo-{序号}.{扩展名}}，不透传浏览器提供的原始文件名，
 * 避免把用户本地路径等信息写进人脸服务日志。
 *
 * @param filename 规范化文件名
 * @param bytes 图片字节
 * @param mediaType 按魔数识别出的图片类型
 * @author xxy-lh
 * @date 2026/11/08
 */
public record FaceImage(String filename, byte[] bytes, MediaType mediaType) {

    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    /**
     * 人脸接口单独限制每张照片 10MB，避免受 RAG 文档的全局上传上限影响。
     *
     * @param files 上传照片
     * @throws ServiceException 任一照片超过 10MB 时抛 A0702
     */
    public static void validateUploadSizes(List<MultipartFile> files) {
        for (MultipartFile file : files) {
            validateUploadSize(file);
        }
    }

    private static void validateUploadSize(MultipartFile file) {
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ServiceException(GlobalErrorCodeConstants.UPLOAD_FILE_TOO_LARGE,
                    "人脸照片单张不能超过 10MB");
        }
    }

    /**
     * 把上传文件逐个校验并转换为人脸照片，序号从 1 开始。
     *
     * @param files 上传文件，调用方已校验张数
     * @return 人脸照片，顺序与上传顺序一致
     * @throws ServiceException 任一照片超过 10MB 时抛 A0702，格式错误时抛 A0701
     */
    public static List<FaceImage> fromUploads(List<MultipartFile> files) {
        List<FaceImage> images = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            images.add(fromUpload(i + 1, files.get(i)));
        }
        return images;
    }

    /**
     * 校验并转换单个上传文件。
     *
     * @param index 从 1 开始的上传序号
     * @param file 上传文件
     * @return 人脸照片
     * @throws ServiceException 照片超过 10MB 时抛 A0702，格式错误时抛 A0701
     */
    public static FaceImage fromUpload(int index, MultipartFile file) {
        validateUploadSize(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ServiceException(GlobalErrorCodeConstants.PARAM_ERROR, "读取上传文件失败");
        }
        MediaType mediaType = ImageTypeUtils.detect(bytes)
                .orElseThrow(() -> new ServiceException(GlobalErrorCodeConstants.UPLOAD_FILE_TYPE_INVALID,
                        "第 " + index + " 张照片不是 JPEG、PNG 或 WebP"));
        return new FaceImage("photo-" + index + "." + ImageTypeUtils.extension(mediaType), bytes, mediaType);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FaceImage image
                && filename.equals(image.filename)
                && mediaType.equals(image.mediaType)
                && Arrays.equals(bytes, image.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * filename.hashCode() + Arrays.hashCode(bytes);
    }

    /** 不输出图片字节，防止日志打印照片内容。 */
    @Override
    public String toString() {
        return "FaceImage[filename=" + filename + ", mediaType=" + mediaType + ", size=" + bytes.length + "]";
    }
}
