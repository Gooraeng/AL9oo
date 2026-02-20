package com.back.domain.user.auth.dto.response;

import jakarta.servlet.http.Cookie;
import jakarta.validation.constraints.NotNull;

/**
 * 로컬 로그인 응답 DTO.
 *
 * <p>AT/RT 쿠키를 HTTP 응답에 직접 설정하기 위한 내부 전달 객체입니다.
 * 이 DTO는 HTTP 응답 바디가 아닌 쿠키를 통해 토큰을 전달합니다.</p>
 */
public record LocalLoginResponseDto(
        @NotNull
        Cookie accessTokenCookie,

        @NotNull
        Cookie refreshTokenCookie,

        @NotNull
        String nickname
) {
}
