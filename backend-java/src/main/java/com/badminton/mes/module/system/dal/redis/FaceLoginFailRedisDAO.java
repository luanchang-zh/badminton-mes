package com.badminton.mes.module.system.dal.redis;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 人脸登录按来源 IP 的失败计数。
 *
 * <p>固定窗口：第一次失败时设置过期时间，之后的失败只累加不续期。登录成功不清零，
 * 否则攻击者可以穿插一次本人登录把失败次数归零。和工号密码登录的
 * {@code mes:system:login:fail:{userNo}} 互不影响。
 *
 * @author xxy-lh
 * @date 2026/11/09
 */
@Component
public class FaceLoginFailRedisDAO {

    /** INCR 与首次 PEXPIRE 放在一个脚本里，避免进程在两步之间退出留下永不过期的计数 */
    private static final DefaultRedisScript<Long> RECORD_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; return count;",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 构造失败计数 DAO。
     *
     * @param redisTemplate Redis 操作模板
     */
    public FaceLoginFailRedisDAO(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 查询当前窗口内的失败次数。
     *
     * @param ip 来源 IP
     * @return 失败次数，没有记录时为 0
     */
    public long getFailCount(String ip) {
        String value = redisTemplate.opsForValue().get(SystemRedisKeyConstants.faceLoginFailIpKey(ip));
        return StringUtils.hasText(value) ? Long.parseLong(value) : 0L;
    }

    /**
     * 失败次数加 1，首次失败时开始计时。
     *
     * @param ip 来源 IP
     * @param window 固定窗口时长
     * @return 累加后的失败次数
     */
    public long recordFail(String ip, Duration window) {
        Long count = redisTemplate.execute(RECORD_SCRIPT,
                List.of(SystemRedisKeyConstants.faceLoginFailIpKey(ip)), String.valueOf(window.toMillis()));
        return count == null ? 0L : count;
    }
}
