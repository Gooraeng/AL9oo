package com.back.domain.user.auth.type;

import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 지원하는 인증 제공자.
 *
 * <p>소셜 로그인(OAuth2)과 로컬 이메일/비밀번호 인증을 구분합니다.</p>
 */
@Getter
public enum AuthProvider {

    /** Google OAuth2 인증. */
    GOOGLE,

    /** Discord OAuth2 인증. */
    DISCORD,

    /** 이메일/비밀번호 기반 로컬 인증. */
    LOCAL;

    /**
     * OAuth2 provider 목록. LOCAL은 OAuth2가 아니므로 제외됩니다.
     * 계정 연동 상태 조회 등 OAuth2 provider만 대상으로 하는 로직에서 사용합니다.
     */
    public static final List<AuthProvider> oAuth2Providers = Arrays
            .stream(AuthProvider.values())
            .filter(it -> !it.equals(AuthProvider.LOCAL))
            .toList();

    /**
     * 문자열을 AuthProvider로 변환합니다 (대소문자 무시).
     *
     * @param value 변환할 문자열 (예: "google", "DISCORD")
     * @return 매핑되는 AuthProvider 상수
     * @throws ApiException 매핑되는 상수가 없을 경우 {@code ErrorCode.BAD_REQUEST}
     */
    public static AuthProvider of(String value) {
        try {
            return AuthProvider.valueOf(value.toUpperCase());
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST);
        }
    }

}
