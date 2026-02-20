package com.back.global.security.jwt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccessTokenCreationRequestDto(
        @NotNull
        Long memberId,

        @NotBlank
        String role
) {
}
