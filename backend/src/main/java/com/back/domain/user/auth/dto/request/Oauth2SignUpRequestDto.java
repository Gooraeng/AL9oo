package com.back.domain.user.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OAuth2 가입 완료 요청 DTO.
 *
 * <p>OAuth2 최초 로그인 후 GUEST 사용자가 닉네임과 약관 동의를 제출할 때 사용합니다.</p>
 */
public record Oauth2SignUpRequestDto(
        @Schema(description = "사용할 닉네임 (공백 불가, 최대 30자)", example = "레이서_123")
        String displayName,

        @Schema(description = "필수 약관 동의 여부. true여야 가입이 완료됩니다.")
        boolean essentialTermsAgreed
) {
}
