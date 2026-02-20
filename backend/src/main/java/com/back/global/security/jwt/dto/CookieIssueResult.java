package com.back.global.security.jwt.dto;

import jakarta.servlet.http.Cookie;

public record CookieIssueResult(
        Cookie accessTokenCookie,
        Cookie refreshTokenCookie
) {
}
