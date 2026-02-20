package com.back.domain.user.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 가입 완료 응답 DTO.
 */
public record SignUpResponseDto(
        @Schema(description = "가입 완료 환영 메시지", example = "Welcome, 레이서_123!")
        @NotBlank String message
) {
}
