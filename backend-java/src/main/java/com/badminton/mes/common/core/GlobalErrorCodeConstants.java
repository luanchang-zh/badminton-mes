package com.badminton.mes.common.core;

/**
 * 全局通用错误码，取值复用《Java开发手册(黄山版)》附表 3 的宏观错误码。
 *
 * <p>模块专属错误码放在各模块 {@code enums} 包的 {@code XxxErrorCodeConstants} 中，
 * 与业务代码就近维护；本类只放跨模块通用的错误码。
 *
 * @author 张竹灏
 * @date 2026/07/07
 */
public final class GlobalErrorCodeConstants {

    /** 成功。全部正常但必须填充错误码时返回五个零(ERRCODE-003) */
    public static final ErrorCode SUCCESS = new ErrorCode("00000", "成功", null);

    /** 用户请求参数错误(附表 3 二级宏观错误码 A0400)，参数校验失败统一使用 */
    public static final ErrorCode PARAM_ERROR =
            new ErrorCode("A0400", "用户请求参数错误", "请求参数有误，请检查后重试");

    /** 请求 JSON 解析失败(附表 3 A0427) */
    public static final ErrorCode JSON_PARSE_ERROR =
            new ErrorCode("A0427", "请求 JSON 解析失败", "请求格式错误，请检查提交内容");

    /** 用户上传文件超出大小限制(附表 3 A0702)：单个文件或单次请求总量超过 multipart 配置 */
    public static final ErrorCode UPLOAD_FILE_TOO_LARGE =
            new ErrorCode("A0702", "用户上传文件太大", "文件过大，单张不超过 5MB，单次合计不超过 16MB");

    /** 用户登录已过期(附表 3 A0230)：未携带 token 或会话失效，HTTP 映射 401 */
    public static final ErrorCode UNAUTHORIZED =
            new ErrorCode("A0230", "用户登录已过期", "登录状态已失效，请重新登录");

    /** 访问未授权(附表 3 A0301)：登录用户角色不满足接口要求，HTTP 映射 403 */
    public static final ErrorCode FORBIDDEN =
            new ErrorCode("A0301", "访问未授权", "当前账号没有该操作权限");

    /** 系统执行出错(附表 3 一级宏观错误码 B0001)，未知异常兜底使用 */
    public static final ErrorCode SYSTEM_ERROR =
            new ErrorCode("B0001", "系统执行出错", "系统繁忙，请稍后重试");

    private GlobalErrorCodeConstants() {
    }
}
