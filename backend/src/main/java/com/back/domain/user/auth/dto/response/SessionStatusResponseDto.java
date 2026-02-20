package com.back.domain.user.auth.dto.response;

import com.back.global.security.jwt.refreshToken.domain.RefreshTokenDomain;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 단일 세션(기기) 정보 응답 DTO.
 */
public record SessionStatusResponseDto(
        @Schema(description = "기기 고유 식별자", example = "550e8400-e29b-41d4-a716-446655440000")
        String deviceId,

        @Schema(description = "해당 기기로 최초 로그인한 시각")
        LocalDateTime loginDate,

        @Schema(description = "현재 요청을 보낸 기기 여부")
        boolean currentSession
) {
    public static SessionStatusResponseDto fromDomain(RefreshTokenDomain domain, String deviceId) {
        boolean IsCurrentSession = deviceId.equals(domain.getDeviceId());

        return new SessionStatusResponseDto(
                domain.getDeviceId(),
                domain.getCreatedDate(),
                IsCurrentSession
        );
    }
}
