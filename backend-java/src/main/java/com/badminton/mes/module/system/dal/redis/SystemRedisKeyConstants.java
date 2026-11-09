package com.badminton.mes.module.system.dal.redis;

import java.time.Duration;

/**
 * 系统模块 Redis Key 常量，集中管理 Key 格式与 TTL。
 *
 * @author 张竹灏
 * @date 2026/07/09
 */
public final class SystemRedisKeyConstants {

    /** 登录会话正查 Key 模板，参数为 token，值为 LoginUser JSON */
    public static final String LOGIN_TOKEN = "mes:system:login:token:%s";

    /** 登录会话反向索引 Key 模板，参数为用户 id，值为该用户当前 token(单设备登录) */
    public static final String LOGIN_USER_INDEX = "mes:system:login:user:%d";

    /** 会话 TTL，拦截器命中且剩余不足一半时滑动续期 */
    public static final Duration LOGIN_SESSION_TTL = Duration.ofHours(8);

    /** 登录失败计数 Key 模板，参数为工号 */
    public static final String LOGIN_FAIL = "mes:system:login:fail:%s";

    /** 登录失败锁定窗口，自最后一次失败起算 */
    public static final Duration LOGIN_FAIL_TTL = Duration.ofMinutes(15);

    /** 登录失败锁定阈值：连续失败达到该次数后锁定 */
    public static final int LOGIN_FAIL_MAX = 5;

    /** 微信小程序绑定票据 Key 模板，参数为随机票据 */
    public static final String MINI_APP_BIND_TICKET = "mes:system:wechat-bind:ticket:%s";

    /** 微信绑定结果 Key 模板，值为 WechatBindingResult JSON。 */
    public static final String MINI_APP_BIND_RESULT = "mes:system:wechat-bind:result:%s";

    /** 微信 access_token Key 模板，按 AppID 隔离。 */
    public static final String WECHAT_ACCESS_TOKEN = "mes:system:wechat:access-token:%s";

    /** 人脸登录按来源 IP 的失败计数 Key 模板，参数为 IP；固定窗口，首次失败时设置过期 */
    public static final String FACE_LOGIN_FAIL_IP = "mes:system:face:fail:ip:%s";

    /** 人脸写操作单用户互斥锁 Key 模板，参数为用户 id，值为加锁方随机令牌 */
    public static final String FACE_USER_LOCK = "mes:system:face:lock:user:%d";

    /** 人脸单用户锁过期时间，覆盖 InsightFace 最长读超时并留出余量 */
    public static final Duration FACE_USER_LOCK_TTL = Duration.ofSeconds(60);

    /**
     * 构造会话正查 Key。
     *
     * @param token 登录令牌
     * @return 会话 Key
     */
    public static String loginTokenKey(String token) {
        return String.format(LOGIN_TOKEN, token);
    }

    /**
     * 构造会话反向索引 Key。
     *
     * @param userId 用户主键
     * @return 反向索引 Key
     */
    public static String loginUserIndexKey(Long userId) {
        return String.format(LOGIN_USER_INDEX, userId);
    }

    /**
     * 构造登录失败计数 Key。
     *
     * @param userNo 工号
     * @return 失败计数 Key
     */
    public static String loginFailKey(String userNo) {
        return String.format(LOGIN_FAIL, userNo);
    }

    /**
     * 构造微信小程序绑定票据 Key。
     *
     * @param ticket 随机票据
     * @return Redis Key
     */
    public static String miniAppBindTicketKey(String ticket) {
        return String.format(MINI_APP_BIND_TICKET, ticket);
    }

    public static String miniAppBindResultKey(String ticket) {
        return String.format(MINI_APP_BIND_RESULT, ticket);
    }

    public static String wechatAccessTokenKey(String appId) {
        return String.format(WECHAT_ACCESS_TOKEN, appId);
    }

    /**
     * 构造人脸登录来源 IP 失败计数 Key。
     *
     * @param ip 来源 IP
     * @return Redis Key
     */
    public static String faceLoginFailIpKey(String ip) {
        return String.format(FACE_LOGIN_FAIL_IP, ip);
    }

    /**
     * 构造人脸写操作单用户锁 Key。
     *
     * @param userId 用户主键
     * @return Redis Key
     */
    public static String faceUserLockKey(Long userId) {
        return String.format(FACE_USER_LOCK, userId);
    }

    private SystemRedisKeyConstants() {
    }
}
