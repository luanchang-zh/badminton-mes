package com.badminton.mes.module.system.dal.redis;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 人脸写操作的单用户互斥锁。
 *
 * <p>注册、追加、删除样本、关闭人脸登录、删除用户共用同一把锁，保证「先查样本数再追加」
 * 这类检查不会被并发请求打穿。加锁用 {@code SET NX EX}，值为随机令牌；释放时用 Lua 比对令牌，
 * 只删自己加的锁，避免超时后误删别人重新加上的锁。
 *
 * @author xxy-lh
 * @date 2026/11/09
 */
@Component
public class FaceUserLockRedisDAO {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]); end; return 0;",
            Long.class);

    private static final int TOKEN_BYTES = 16;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    /**
     * 构造锁 DAO。
     *
     * @param redisTemplate Redis 操作模板
     */
    public FaceUserLockRedisDAO(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试加锁。
     *
     * @param userId 用户主键
     * @return 加锁成功时返回令牌，释放时需要原样传回；锁被占用时为空
     */
    public Optional<String> tryLock(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                SystemRedisKeyConstants.faceUserLockKey(userId), token,
                SystemRedisKeyConstants.FACE_USER_LOCK_TTL);
        return Boolean.TRUE.equals(locked) ? Optional.of(token) : Optional.empty();
    }

    /**
     * 释放锁；锁已过期或已被他人持有时什么也不做。
     *
     * @param userId 用户主键
     * @param token {@link #tryLock(Long)} 返回的令牌
     */
    public void unlock(Long userId, String token) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(SystemRedisKeyConstants.faceUserLockKey(userId)), token);
    }
}
