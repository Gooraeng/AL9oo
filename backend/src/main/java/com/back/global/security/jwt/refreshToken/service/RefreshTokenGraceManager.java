package com.back.global.security.jwt.refreshToken.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshTokenGraceManager {

    private static final String GRACE_KEY_PREFIX = "grace:rt:";
    private static final Duration GRACE_PERIOD = Duration.ofSeconds(10);

    private final RedisTemplate<String, Object> redisTemplate;

    public void addToGracePeriod(String deviceId, String jti) {
        String key = buildKey(deviceId, jti);
        redisTemplate.opsForValue().set(key, "1", GRACE_PERIOD);
    }

    public boolean isInGracePeriod(String deviceId, String jti) {
        String key = buildKey(deviceId, jti);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private String buildKey(String deviceId, String jti) {
        return GRACE_KEY_PREFIX + deviceId + ":" + jti;
    }
}
