package com.back.global.security;

import com.back.global.config.AppConfig;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * SecurityConfig / TestSecurityConfig 에서 공유하는 HTTP 인가 규칙.
 */
public final class HttpRequestAuthorizationConfig {

    private static final List<String> PUBLIC_GET_ENDPOINTS = new ArrayList<>(
            List.of("/api/*/home/**",
                    "/favicon.ico",
                    "/api/*/member/{targetMemberId}",
                    "/api/*/auth/check-display-name/{name}"
            )
    );

    private static final List<String> DEV_ENDPOINTS =
            List.of("/h2-console/**", "/swagger-ui/**", "/v3/api-docs/**");

    static {
        if (AppConfig.isDev()) {
            PUBLIC_GET_ENDPOINTS.addAll(DEV_ENDPOINTS);
        }
    }

    public static void configure(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth
    ) {
        configureDefault(auth);
    }

    private static void configureDefault(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth
    ) {
        auth
                .requestMatchers(HttpMethod.OPTIONS).permitAll()
                .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS.toArray(new String[0])).permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/*/auth/session/refresh-token").permitAll()
                .requestMatchers(
                        "/api/*/auth/session/**",
                        "/api/*/auth/account-link/**",
                        "/api/*/member/me/**",
                        "/api/*/auth/oauth2/**").authenticated()
                .requestMatchers(
                        "/api/*/admin/**").hasRole("ADMIN")
                .requestMatchers(
                        "/api/*/auth/local/signup/**",
                        "/api/*/auth/local/login",
                        "/api/*/auth/*/signup/**",
                        "/api/*/admin/auth/login").anonymous()
                .anyRequest().authenticated();
    }

    private HttpRequestAuthorizationConfig() {}
}
