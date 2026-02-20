package com.back.domain.user.auth.entity;

import com.back.global.security.constant.SecurityConstants;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 계정 연동 세션을 저장하는 Redis Hash.
 *
 * <p>linkCode를 @Id로 사용하여 OAuth2 state에서 직접 조회할 수 있습니다.
 * memberId는 @Indexed로 설정하여 동일 사용자의 기존 세션을 삭제할 수 있습니다.</p>
 */
@RedisHash
@Getter @Setter
public class AccountLinkRedisHash {

    /** OAuth2 state 파라미터로 전달되는 UUID 형식의 연동 코드. Redis 기본키. */
    @Id
    private String linkCode;

    /** 연동을 요청한 Member ID. secondary index로 사용자 기준 일괄 삭제 가능. */
    @Indexed
    private Long memberId;

    /** 연동하려는 OAuth provider 이름 (소문자, 예: "google", "discord"). */
    private String requestedProvider;

    /** Redis TTL (초). SecurityConstants.ACCOUNT_LINK_TTL_SECONDS 값으로 초기화. */
    @TimeToLive
    private Long timeToLive;

    /** 세션 생성 시각. */
    public LocalDateTime createdAt;

    @Builder
    public AccountLinkRedisHash(Long memberId, String requestedProvider) {
        this.linkCode = generateLinkCode();
        this.memberId = memberId;
        this.requestedProvider = requestedProvider;
        this.timeToLive = SecurityConstants.ACCOUNT_LINK_TTL_SECONDS;
        this.createdAt = LocalDateTime.now();
    }

    private String generateLinkCode() {
        return UUID.randomUUID().toString();
    }
}
