package com.back.global.security.jwt;

import com.back.global.property.JwtProperty;
import com.back.global.security.jwt.service.JwtTokenProvider;
import com.back.utils.web.CookieUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtCookieHelper {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperty jwtProperty;

    public Cookie createAccessTokenCookie(Long memberId, String role) {
        String accessToken = jwtTokenProvider.generateAccessToken(memberId, role);

        JwtProperty.AccessToken atp = jwtProperty.getAccessToken();

        return CookieUtil.createCookie(
                atp.getName(),
                accessToken,
                atp.getDomain(),
                atp.getPath(),
                atp.isHttpOnly(),
                atp.isSecure(),
                (int) atp.getDuration().toSeconds(),
                atp.getSameSite()
        );
    }

    public Cookie createRefreshTokenCookie(Long memberId, String deviceId) {
        String refreshToken = jwtTokenProvider.generateRefreshToken(memberId, deviceId);

        JwtProperty.RefreshToken rtp = jwtProperty.getRefreshToken();

        return CookieUtil.createCookie(
                rtp.getName(),
                refreshToken,
                rtp.getDomain(),
                rtp.getPath(),
                rtp.isHttpOnly(),
                rtp.isSecure(),
                (int) rtp.getDuration().toSeconds(),
                rtp.getSameSite()
        );
    }

    public String getAccessTokenFromRequest(HttpServletRequest request) {
        return CookieUtil.findCookieValueFromRequestByName(
                request,
                jwtProperty.getAccessToken().getName()
        );
    }

    public String getRefreshTokenFromRequest(HttpServletRequest request) {
        return CookieUtil.findCookieValueFromRequestByName(
                request,
                jwtProperty.getRefreshToken().getName()
        );
    }

    public Cookie invalidateAccessTokenCookie() {
        JwtProperty.AccessToken atp = jwtProperty.getAccessToken();

        return CookieUtil.invalidateCookie(
                atp.getName(),
                atp.getDomain(),
                "/",
                atp.isHttpOnly(),
                atp.isSecure(),
                atp.getSameSite()
        );
    }

    public Cookie invalidateRefreshTokenCookie() {
        JwtProperty.RefreshToken rtp = jwtProperty.getRefreshToken();

        return CookieUtil.invalidateCookie(
                rtp.getName(),
                rtp.getDomain(),
                "/",
                rtp.isHttpOnly(),
                rtp.isSecure(),
                rtp.getSameSite()
        );
    }

    public void invalidateAuthCookies(HttpServletResponse response) {
        response.addCookie(invalidateAccessTokenCookie());
        response.addCookie(invalidateRefreshTokenCookie());
    }
}
