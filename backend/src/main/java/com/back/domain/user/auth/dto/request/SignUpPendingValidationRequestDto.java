package com.back.domain.user.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 이메일 인증 코드 검증 요청 DTO.
 *
 * <p>로컬 회원가입 시 이메일로 발송된 인증 코드와 nonce를 함께 제출합니다.</p>
 */
public record SignUpPendingValidationRequestDto(
        @Schema(description = "이메일로 발송된 6자리 인증 코드", example = "482951")
        String code,

        @Schema(description = "인증 세션 식별용 nonce 값")
        String nonce
) {
}
