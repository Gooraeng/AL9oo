package com.back.domain.user.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 이메일 인증 코드 응답 DTO.
 *
 * <p>개발/테스트 환경에서 이메일 발송 없이 인증 코드를 확인할 때 사용합니다.</p>
 */
public record VerificationCode(
        @Schema(description = "이메일로 발송된 인증 코드", example = "482951")
        String code
) {
}
