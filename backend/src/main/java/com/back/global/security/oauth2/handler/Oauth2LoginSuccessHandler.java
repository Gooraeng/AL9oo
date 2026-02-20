package com.back.global.security.oauth2.handler;

import com.back.domain.user.auth.useCase.AccountLinkCase;
import com.back.domain.user.auth.service.SessionManagementService;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.property.UrlProperty;
import com.back.global.security.constant.SecurityConstants;
import com.back.global.security.jwt.JwtCookieHelper;
import com.back.global.security.jwt.dto.CookieIssueResult;
import com.back.global.security.jwt.service.JwtTokenProvider;
import com.back.global.security.member.CustomUserDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * OAuth2 로그인 성공 핸들러.
 *
 * <p>일반 로그인과 계정 연동을 request attribute의 linkCode 존재 여부로 구분합니다.
 * 계정 연동 시 JWT로 현재 로그인된 사용자를 추가 검증하여 보안을 강화합니다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Oauth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UrlProperty urlProperty;
    private final JwtCookieHelper jwtCookieHelper;
    private final SessionManagementService sessionManagementService;
    private final AccountLinkCase accountLinkCase;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Assert.notNull(userDetails, "UserDetails cannot be null after successful authentication");

        // request attribute에서 linkCode 확인
        String linkCode = (String) request.getAttribute(SecurityConstants.LINK_CODE_ATTRIBUTE);

        if (linkCode == null) {
            handleLogin(request, response, userDetails);
        } else {
            handleAccountLink(request, response, userDetails);
        }
    }

    private void handleLogin(
            HttpServletRequest request,
            HttpServletResponse response,
            CustomUserDetails userDetails
    ) throws IOException, ServletException {
        Long memberId = userDetails.getMemberId();

        String redirectUrl = urlProperty.getFrontUrl();

        if (!userDetails.isRegistered()) {
            log.info("User {} is not registered yet.", memberId);
        }

        String rt = jwtCookieHelper.getRefreshTokenFromRequest(request);

        CookieIssueResult result = sessionManagementService.issueAllTokenCookies(
                memberId, userDetails.getRole().name(), rt
        );

        response.addCookie(result.accessTokenCookie());
        response.addCookie(result.refreshTokenCookie());

        log.info("OAuth2 login successful member: {}, provider: {}", memberId, userDetails.getProvider());

        response.sendRedirect(redirectUrl);
    }

    private void handleAccountLink(
            HttpServletRequest request,
            HttpServletResponse response,
            CustomUserDetails userDetails
    ) throws IOException {
        try {
            // 1. JWT로 현재 로그인된 사용자 검증 (linkCode 가로채기 방지)
            String accessToken = jwtCookieHelper.getAccessTokenFromRequest(request);

            if (accessToken == null || accessToken.isBlank()) {
                throw new ApiException(ErrorCode.SESSION_EXPIRED,
                        "Access token required for account linking");
            }

            Long currentMemberId = jwtTokenProvider.getAccessTokenDto(accessToken).memberId();

            // 2. memberId 일치 확인 (userDetails.getMemberId()는 Strategy의 linkData에서 설정됨)
            if (!userDetails.getMemberId().equals(currentMemberId)) {
                log.warn("Account link memberId mismatch: userDetails={}, jwt={}",
                        userDetails.getMemberId(), currentMemberId);
                throw new ApiException(ErrorCode.ACCESS_DENIED,
                        "Link session does not belong to current user");
            }

            // 3. 계정 연동 진행
            accountLinkCase.linkOauth2Account(
                    currentMemberId,
                    userDetails.getProvider(),
                    userDetails.getIssuerValue(),
                    userDetails.getSubjectValue(),
                    userDetails.getEmail()
            );

            // 4. linkCode 소비
            accountLinkCase.consumeLinkCodeByMemberId(currentMemberId);

            log.info("Account linking successful: member={}, provider={}",
                    currentMemberId, userDetails.getProvider());

            // 성공 페이지로 리다이렉트
            String redirectUrl = UriComponentsBuilder
                    .fromUriString(urlProperty.getFrontUrl())
                    .path("/account/linked")
                    .queryParam("provider", userDetails.getProvider().name().toLowerCase())
                    .queryParam("success", "true")
                    .build()
                    .toUriString();

            response.sendRedirect(redirectUrl);

        } catch (ApiException ex) {
            log.error("Account linking failed: {}", ex.getMessage());
            handleLinkingError(response, ex);
        } catch (Exception ex) {
            log.error("Unexpected error during account linking", ex);
            handleLinkingError(response,
                    new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to link account"));
        }
    }

    private void handleLinkingError(HttpServletResponse response, ApiException ex) throws IOException {
        String redirectUrl = UriComponentsBuilder
                .fromUriString(urlProperty.getFrontUrl())
                .path("/account/linked")
                .queryParam("success", "false")
                .queryParam("error", ex.getErrorCode().getCode())
                .queryParam("message", ex.getMessage())
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
