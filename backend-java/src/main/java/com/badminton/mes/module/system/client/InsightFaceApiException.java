package com.badminton.mes.module.system.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

/**
 * InsightFace 返回的业务性 4xx(404、409、422 等)，由 Service 按规划第 4 节决定如何处理。
 *
 * <p>连接失败、认证失败、5xx 和超时不走这个异常，Client 直接抛 C0001 / C0200 的
 * {@code ServiceException}。
 *
 * @author xxy-lh
 * @date 2026/11/08
 */
public class InsightFaceApiException extends RuntimeException {

    /** HTTP 状态码 */
    private final int status;

    /** InsightFace 错误码，例如 person_exists、person_not_found */
    private final String code;

    /** 错误体中的 error.details，只含原因码等元数据，不含图片和特征 */
    private final transient JsonNode details;

    /**
     * 构造业务异常。
     *
     * @param status HTTP 状态码
     * @param code InsightFace 错误码
     * @param details error.details，可为 null
     */
    public InsightFaceApiException(int status, String code, JsonNode details) {
        super("InsightFace HTTP " + status + " " + code);
        this.status = status;
        this.code = code;
        this.details = details == null ? MissingNode.getInstance() : details;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public JsonNode getDetails() {
        return details;
    }

    /**
     * 同时比较状态码和错误码。InsightFace 的 face_not_found 在 404 和 422 下含义不同，不能只看 code。
     *
     * @param expectedStatus 期望的 HTTP 状态码
     * @param expectedCode 期望的错误码
     * @return 是否匹配
     */
    public boolean is(int expectedStatus, String expectedCode) {
        return status == expectedStatus && expectedCode.equals(code);
    }
}
