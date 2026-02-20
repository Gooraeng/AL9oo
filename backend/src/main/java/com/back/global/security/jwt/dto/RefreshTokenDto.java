package com.back.global.security.jwt.dto;

public record RefreshTokenDto(
        String jti,
        Long memberId,
        String deviceId
) {
}
