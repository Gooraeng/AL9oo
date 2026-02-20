package com.back.global.security.constant;

/**
 * 상수 모음 컴포넌트
 */
public final class SecurityConstants {

    /**
     * Refresh Token TTL seconds
     */
    public static final long REFRESH_TOKEN_TTL_SECONDS = 1209600L;

    /**
     * Refresh Token Idle TTL seconds (7 days).
     * If a refresh token is not used within this period, it is considered expired.
     */
    public static final long REFRESH_TOKEN_IDLE_TTL_SECONDS = 604_800L;

    /**
     * Account Link TTL seconds
    */
    public static final Long ACCOUNT_LINK_TTL_SECONDS = 300L;

    /**
     * Max devices per member
     */
    public static final int MAX_DEVICES_PER_MEMBER = 5;

    // Cookie
    /**
     * Account Link Code Request Attribute Name.
     * OAuth2 callback 시 linkCode를 request attribute로 전달할 때 사용.
     */
    public static final String LINK_CODE_ATTRIBUTE = "al9oo.accountLink.linkCode";

    private SecurityConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}
