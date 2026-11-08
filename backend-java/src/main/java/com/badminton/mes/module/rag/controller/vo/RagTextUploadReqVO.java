package com.badminton.mes.module.rag.controller.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文本上传请求。
 *
 * <p>只接收内容与来源标识，不接收任何租户标识：租户由服务端根据登录用户解析。
 *
 * <p>{@code fileSource} 是上游的<b>文档身份</b>，参与去重：同名文档会被上游以 409 拒绝，
 * 并不是仅仅展示用的名字。
 *
 * @param fileSource 文档来源标识，同名会被上游判为冲突
 * @param content    文本内容
 * @author 张竹灏
 * @date 2026/11/08
 */
public record RagTextUploadReqVO(
        @NotBlank(message = "文档来源不能为空")
        @Size(max = 255, message = "文档来源长度不能超过 255")
        String fileSource,

        @NotBlank(message = "内容不能为空")
        String content) {
}
