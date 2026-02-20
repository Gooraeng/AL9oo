package com.back.global.security.jwt;

import com.back.domain.user.member.type.MemberRole;
import com.back.global.config.AppConfig;
import com.back.global.security.jwt.dto.AccessTokenDto;
import com.back.global.security.jwt.service.JwtTokenProvider;
import com.back.global.security.member.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtCookieHelper jwtCookieHelper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractAccessToken(request);

        if (token != null && !token.isBlank()) {
            try {
                AccessTokenDto accessTokenDto = jwtTokenProvider.getAccessTokenDto(token);

                Long userId = accessTokenDto.memberId();
                MemberRole role = accessTokenDto.role();

                // Authentication 설정
                CustomUserDetails userDetails = CustomUserDetails.fromJwt(userId, role);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );

                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                log.error("Failed to set user authentication in security context", e);
                String message = AppConfig.isDev() ? e.getMessage() : "Authentication Failed";
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, message);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Cookie와 Authorization 헤더 둘 다 지원
     */
    private String extractAccessToken(HttpServletRequest request) {
        // 1. Authorization Bearer 헤더 우선
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // 2. Request에서 cookie 추출
        return jwtCookieHelper.getAccessTokenFromRequest(request);
    }
}
