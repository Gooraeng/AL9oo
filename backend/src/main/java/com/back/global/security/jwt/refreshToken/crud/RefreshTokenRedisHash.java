package com.back.global.security.jwt.refreshToken.crud;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDateTime;

@RedisHash
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRedisHash {

    @Id
    private String id;

    // sub
    @Indexed
    private Long memberId;

    private String jti;

    private String deviceId;

    @TimeToLive
    private Long ttl;

    private LocalDateTime createdDate;

    private LocalDateTime lastCheckedDate;

}
