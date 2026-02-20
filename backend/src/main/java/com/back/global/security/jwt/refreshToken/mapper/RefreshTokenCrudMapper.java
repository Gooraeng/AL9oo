package com.back.global.security.jwt.refreshToken.mapper;

import com.back.global.mapper.BaseMapper;
import com.back.global.security.jwt.refreshToken.domain.RefreshTokenDomain;
import com.back.global.security.jwt.refreshToken.crud.RefreshTokenRedisHash;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RefreshTokenCrudMapper implements BaseMapper<RefreshTokenDomain, RefreshTokenRedisHash> {

    @Override
    public RefreshTokenRedisHash toEntity(RefreshTokenDomain domain) {
        return RefreshTokenRedisHash.builder()
                .id(domain.getId())
                .memberId(domain.getMemberId())
                .deviceId(domain.getDeviceId())
                .jti(domain.getJti())
                .createdDate(domain.getCreatedDate())
                .lastCheckedDate(domain.getLastCheckedDate())
                .build();
    }

    @Override
    public RefreshTokenDomain toDomain(RefreshTokenRedisHash entity) {
        LocalDateTime expirationDate = LocalDateTime.now().plusSeconds(entity.getTtl());

        return RefreshTokenDomain.builder()
                .id(entity.getId())
                .memberId(entity.getMemberId())
                .deviceId(entity.getDeviceId())
                .jti(entity.getJti())
                .createdDate(entity.getCreatedDate())
                .lastCheckedDate(entity.getLastCheckedDate())
                .expirationDate(expirationDate)
                .build();
    }
}
