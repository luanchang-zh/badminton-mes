package com.badminton.mes.module.system.constants;

import com.badminton.mes.common.core.ErrorCode;

/**
 * 系统模块(认证、用户、角色)错误码，与业务代码就近维护。
 *
 * <p>编码复用《Java开发手册(黄山版)》附表 3 宏观错误码(ERRCODE-006)，
 * 业务细节由 message 承载(ERRCODE-008)。跨模块通用的 A0230/A0301
 * 放在 {@code GlobalErrorCodeConstants}。
 *
 * @author 张竹灏
 * @date 2026/07/09
 */
public final class SystemErrorCodeConstants {

    /**
     * 工号或密码错误(A0200 用户登录异常)。
     * 账户不存在与密码错误统一提示，防止撞库探测账号；服务端日志区分具体原因。
     */
    public static final ErrorCode LOGIN_CREDENTIALS_INVALID =
            new ErrorCode("A0200", "工号或密码错误", "工号或密码错误，请重新输入");

    /** 密码连续错误次数超限，登录临时锁定 */
    public static final ErrorCode LOGIN_LOCKED =
            new ErrorCode("A0211", "密码错误次数超限", "连续失败次数过多，请 15 分钟后重试");

    /** 账户已停用，密码校验通过后才提示，避免向未持凭据者泄露账号状态 */
    public static final ErrorCode LOGIN_USER_DISABLED =
            new ErrorCode("A0202", "账户已停用", "账号已被停用，请联系管理员");

    /** 用户不存在或已删除(用户管理接口) */
    public static final ErrorCode USER_NOT_EXISTS =
            new ErrorCode("A0201", "用户不存在", "用户不存在或已被删除，请刷新后重试");

    /** 工号重复(应用层先查校验，数据库唯一索引 uk_user_no 兜底) */
    public static final ErrorCode USER_NO_DUPLICATE =
            new ErrorCode("A0506", "工号已存在", "工号重复，请更换工号后重试");

    /** 修改本人密码时旧密码校验失败 */
    public static final ErrorCode USER_OLD_PASSWORD_MISMATCH =
            new ErrorCode("A0210", "旧密码错误", "旧密码不正确，请重新输入");

    /** 新增用户缺少初始密码(创建与修改共用 VO，密码仅创建时必填) */
    public static final ErrorCode USER_INIT_PASSWORD_REQUIRED =
            new ErrorCode("A0402", "新增用户必须填写初始密码", "请填写初始密码");

    /** 分配的角色不存在、已删除或已停用 */
    public static final ErrorCode USER_ROLE_INVALID =
            new ErrorCode("A0402", "所选角色不存在或已停用", "所选角色不可用，请重新选择");

    /** 非管理员不能授予或撤销 ADMIN。 */
    public static final ErrorCode USER_ADMIN_ROLE_PROTECTED =
            new ErrorCode("A0440", "非管理员不能修改 ADMIN 角色", "只有管理员可以分配管理员职位");

    /** 非 ADMIN 用户至少需要保留一个职位。 */
    public static final ErrorCode USER_ROLE_REQUIRED =
            new ErrorCode("A0402", "用户至少需要一个职位", "请至少选择一个职位");

    /** 用户所属车间或产线不存在、未启用或层级不一致 */
    public static final ErrorCode USER_ORGANIZATION_INVALID =
            new ErrorCode("A0402", "用户所属车间或产线不可用", "请选择层级一致的启用车间和产线");

    /** 角色不存在或已删除(按角色反查用户等接口) */
    public static final ErrorCode ROLE_NOT_EXISTS =
            new ErrorCode("A0402", "角色不存在", "角色不存在或已被删除，请刷新后重试");

    /** 不能停用或删除当前登录账号，防止管理员误操作后无人能恢复 */
    public static final ErrorCode USER_OPERATE_SELF_FORBIDDEN =
            new ErrorCode("A0440", "不能停用或删除当前登录账号", "不能对当前登录账号执行该操作");

    /** 微信登录 code 无效或已过期 */
    public static final ErrorCode WECHAT_CODE_INVALID =
            new ErrorCode("A0203", "微信登录 code 无效", "微信登录凭证已失效，请重新登录");

    /** 微信服务暂时不可用 */
    public static final ErrorCode WECHAT_SERVICE_UNAVAILABLE =
            new ErrorCode("C0001", "调用微信登录服务失败", "微信服务暂时不可用，请稍后重试");

    /** 微信小程序配置缺失 */
    public static final ErrorCode WECHAT_CONFIG_MISSING =
            new ErrorCode("B0001", "微信小程序 AppID 或 AppSecret 未配置", "微信登录尚未配置，请联系管理员");

    /** 短期绑定票据无效 */
    public static final ErrorCode WECHAT_BIND_TICKET_INVALID =
            new ErrorCode("A0203", "微信绑定票据无效或已过期", "绑定页面已过期，请重新进行微信登录");

    /** 微信身份或 MES 用户绑定冲突 */
    public static final ErrorCode WECHAT_BINDING_CONFLICT =
            new ErrorCode("A0111", "微信身份已绑定其他 MES 账号", "该微信已绑定其他账号，请联系管理员");

    /** MES 用户已绑定微信身份 */
    public static final ErrorCode WECHAT_USER_ALREADY_BOUND =
            new ErrorCode("A0111", "MES 用户已绑定其他微信身份", "该工号已绑定其他微信，请先解除绑定");

    /** 人脸登录未通过：无候选、分数不足或与第二名过近统一提示，不泄露库中是否有此人 */
    public static final ErrorCode FACE_LOGIN_REJECTED =
            new ErrorCode("A0200", "人脸识别未通过", "人脸识别未通过，请重试或改用密码登录");

    /** 同一来源 IP 人脸登录失败次数超限 */
    public static final ErrorCode FACE_LOGIN_TOO_FREQUENT =
            new ErrorCode("A0501", "人脸登录失败次数超限", "尝试过于频繁，请 15 分钟后再试");

    /** 同一用户的人脸写操作正在进行(单用户 Redis 锁未获取到) */
    public static final ErrorCode FACE_OPERATION_IN_PROGRESS =
            new ErrorCode("A0503", "人脸操作正在进行", "上一次操作尚未完成，请稍后再试");

    /** 已存在有效人脸绑定，不能重复注册 */
    public static final ErrorCode FACE_ALREADY_ENROLLED =
            new ErrorCode("A0111", "已注册人脸登录", "已经注册，请重新采集或追加照片");

    /** 未注册人脸时执行追加、删除样本等操作 */
    public static final ErrorCode FACE_NOT_ENROLLED =
            new ErrorCode("A0440", "尚未注册人脸", "尚未注册人脸，请先注册");

    /** 要删除的人脸样本不存在 */
    public static final ErrorCode FACE_SAMPLE_NOT_FOUND =
            new ErrorCode("A0402", "人脸样本不存在", "样本不存在或已删除，请刷新后重试");

    /** 人脸样本数量达到上限 */
    public static final ErrorCode FACE_SAMPLE_LIMIT =
            new ErrorCode("A0425", "人脸样本数量超限", "样本数量已达上限");

    /** 登录照片中没有可用人脸或无法解码 */
    public static final ErrorCode FACE_IMAGE_REJECTED =
            new ErrorCode("A0700", "照片不合格", "照片不合格，请重新拍摄，并保证画面中只有本人");

    /** 注册人脸前未勾选个人敏感信息处理同意 */
    public static final ErrorCode FACE_CONSENT_REQUIRED =
            new ErrorCode("A0400", "未同意人脸信息处理说明", "请先阅读并同意人脸信息处理说明");

    /** 上传照片张数不符合接口要求 */
    public static final ErrorCode FACE_IMAGE_COUNT_INVALID =
            new ErrorCode("A0400", "人脸照片张数不符合要求", "照片张数不符合要求，请按提示重新拍摄");

    /** InsightFace 连接失败、认证失败或返回 5xx */
    public static final ErrorCode FACE_SERVICE_UNAVAILABLE =
            new ErrorCode("C0001", "调用人脸服务失败", "人脸服务暂时不可用，请稍后重试或使用密码登录");

    /** InsightFace 读超时 */
    public static final ErrorCode FACE_SERVICE_TIMEOUT =
            new ErrorCode("C0200", "人脸服务响应超时", "人脸服务响应超时，请稍后重试或使用密码登录");

    private SystemErrorCodeConstants() {
    }
}
