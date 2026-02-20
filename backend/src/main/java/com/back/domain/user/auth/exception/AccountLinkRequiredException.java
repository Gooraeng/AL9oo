package com.back.domain.user.auth.exception;

import lombok.Getter;

/**
 * 동일 이메일이 다른 OAuth provider에 이미 연결되어 있을 때 발생하는 예외.
 *
 * <p>OAuth 로그인 시 같은 이메일로 가입된 기존 계정이 발견되면 자동 통합 대신
 * 사용자에게 계정 연동 동의를 요청해야 합니다. 이 예외는 그 흐름으로 유도할 때 사용합니다.</p>
 *
 * <p>예외 핸들러는 {@code redirectUrl}로 클라이언트를 리다이렉트하며,
 * {@code token}을 통해 연동 동의 후 처리를 계속합니다.</p>
 */
@Getter
public class AccountLinkRequiredException extends RuntimeException {

    /** 연동 흐름 재개에 사용되는 임시 토큰. */
    private final String token;

    /** 연동 동의 UI 주소. 핸들러가 이 URL로 리다이렉트합니다. */
    private final String redirectUrl;

    /** 충돌이 발생한 이메일 주소. */
    private final String email;

    /** 현재 시도 중인 OAuth provider 이름. */
    private final String newProvider;

    /** 이미 해당 이메일로 연결된 기존 OAuth provider 이름. */
    private final String existingProvider;

    public AccountLinkRequiredException(
            String token,
            String redirectUrl,
            String email,
            String newProvider,
            String existingProvider
    ) {
        super("Member's Approval is required to link account");
        this.token = token;
        this.redirectUrl = redirectUrl;
        this.email = email;
        this.newProvider = newProvider;
        this.existingProvider = existingProvider;
    }
}
