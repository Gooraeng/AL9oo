package com.back.domain.user.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 닉네임 사용 가능 여부 응답 DTO.
 */
public record NickNameCheckResultResponseDto(
        @Schema(description = "닉네임 사용 가능 여부. true이면 사용 가능", example = "true")
        boolean available
) {
}
