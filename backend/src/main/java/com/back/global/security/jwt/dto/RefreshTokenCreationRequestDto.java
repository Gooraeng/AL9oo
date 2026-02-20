package com.back.global.security.jwt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RefreshTokenCreationRequestDto(
        @NotNull
        Long memberId,

        @NotBlank
        String deviceId
) {
}
