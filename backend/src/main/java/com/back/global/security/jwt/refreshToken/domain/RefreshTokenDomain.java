package com.back.global.security.jwt.refreshToken.domain;

import com.back.global.security.constant.SecurityConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Generic Object for refresh token to minimize impact to prevent domain changes.
 * */
@Getter
@Builder
@AllArgsConstructor
public class RefreshTokenDomain {

    // Core
    private final String id;
    private final Long memberId;
    private final String jti;
    private final String deviceId;
    private final Long ttl;

    // Date
    private LocalDateTime createdDate;
    private LocalDateTime expirationDate;
    private LocalDateTime lastCheckedDate;

    public static RefreshTokenDomain initial(
            String id,
            Long memberId,
            String jti,
            String deviceId,
            Long ttl
    ) {
        LocalDateTime now = LocalDateTime.now();

        return RefreshTokenDomain.builder()
                .id(id)
                .memberId(memberId)
                .jti(jti)
                .deviceId(deviceId)
                .createdDate(now)
                .expirationDate(now.plusSeconds(ttl))
                .lastCheckedDate(now)
                .build();
    }

    public void check() {
        lastCheckedDate = LocalDateTime.now();
    }

    public boolean isIdleExpired() {
        if (lastCheckedDate == null) return false;
        return Duration.between(lastCheckedDate, LocalDateTime.now()).toSeconds()
                > SecurityConstants.REFRESH_TOKEN_IDLE_TTL_SECONDS;
    }

    public Long getTtl() {
        Assert.notNull(expirationDate, "Expired date is null");

        return Duration
                .between(expirationDate, LocalDateTime.now())
                .toSeconds();
    }
}