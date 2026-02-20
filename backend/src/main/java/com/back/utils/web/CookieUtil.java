package com.back.utils.web;

import com.back.global.config.AppConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public class CookieUtil {

    public static Cookie invalidateCookie(
            String name,
            String domain,
            String path,
            boolean httpOnly,
            boolean secure,
            String sameSite
    ) {
        return createCookie(
                name,
                null,
                domain,
                path,
                httpOnly,
                secure,
                0,
                sameSite
        );
    }

    public static Cookie createCookie(
            String name,
            String value,
            String domain,
            String path,
            boolean httpOnly,
            boolean secure,
            int maxAgeSeconds,
            String sameSite
    ) {
        Cookie cookie = createCookie(name, value, domain, path, httpOnly, secure, sameSite);
        cookie.setMaxAge(maxAgeSeconds);

        validate(cookie);

        return cookie;
    }

    private static Cookie createCookie(
            String name,
            String value,
            String domain,
            String path,
            Boolean httpOnly,
            boolean secure,
            String sameSite
    ) {
        Cookie cookie = new Cookie(name, value);

        // __Host- prefix 쿠키는 Domain 속성을 설정하면 안 됨 (RFC 6265)
        if (!AppConfig.isProd() &&
            domain != null && !domain.isBlank()
        ) cookie.setDomain(domain);

        cookie.setPath(path);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secure);
        cookie.setAttribute("SameSite", sameSite);

        return cookie;
    }

    public static Optional<Cookie> findCookieFromRequestByName(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieName)) {
                    return Optional.of(cookie);
                }
            }
        }

        return Optional.empty();
    }

    public static String findCookieValueFromRequestByName(HttpServletRequest request, String cookieName) {
        return findCookieFromRequestByName(request, cookieName)
                .map(Cookie::getValue)
                .orElse(null);
    }

    private static void validate(Cookie cookie) {
        assert cookie.getName() != null && !cookie.getName().isBlank();
        assert cookie.getMaxAge() >= -1;
        assert !AppConfig.isProd() || cookie.getSecure();
    }
}
