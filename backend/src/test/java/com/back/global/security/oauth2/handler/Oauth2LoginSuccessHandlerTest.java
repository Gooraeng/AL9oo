package com.back.global.security.oauth2.handler;

import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.auth.useCase.AccountLinkCase;
import com.back.domain.user.auth.service.SessionManagementService;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.property.UrlProperty;
import com.back.global.security.constant.SecurityConstants;
import com.back.global.security.jwt.JwtCookieHelper;
import com.back.global.security.jwt.dto.AccessTokenDto;
import com.back.global.security.jwt.dto.CookieIssueResult;
import com.back.global.security.jwt.service.JwtTokenProvider;
import com.back.global.security.member.CustomUserDetails;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Oauth2LoginSuccessHandler 테스트")
class Oauth2LoginSuccessHandlerTest {

    @Mock private UrlProperty urlProperty;
    @Mock private JwtCookieHelper jwtCookieHelper;
    @Mock private SessionManagementService sessionManagementService;
    @Mock private AccountLinkCase accountLinkCase;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks private Oauth2LoginSuccessHandler handler;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CustomUserDetails userDetails;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        Member member = Member.builder()
                .displayName("testUser")
                .memberStatus(MemberStatus.ACTIVE)
                .essentialTermsAgreed(true)
                .role(MemberRole.GENERAL)
                .build();
        ReflectionTestUtils.setField(member, "id", 1L);

        AuthAccount account = AuthAccount.builder()
                .member(member)
                .authProvider(AuthProvider.DISCORD)
                .issuer("discord")
                .subject("discord-123")
                .email("test@discord.com")
                .build();

        userDetails = new CustomUserDetails(account);
        authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Nested
    @DisplayName("handleAccountLink 테스트")
    class HandleAccountLinkTests {

        @Test
        @DisplayName("실패 - accessToken null 시 SESSION_EXPIRED")
        void fail_null_access_token_throws_session_expired() throws Exception {
            // Given
            String linkCode = "test-link-code";
            request.setAttribute(SecurityConstants.LINK_CODE_ATTRIBUTE, linkCode);

            given(jwtCookieHelper.getAccessTokenFromRequest(request)).willReturn(null);
            given(urlProperty.getFrontUrl()).willReturn("http://localhost:3000");

            // When
            handler.onAuthenticationSuccess(request, response, authentication);

            // Then
            assertThat(response.getRedirectedUrl()).contains("success=false");
            assertThat(response.getRedirectedUrl()).contains("error=AUTH006");
            verify(jwtTokenProvider, never()).getAccessTokenDto(any());
        }

        @Test
        @DisplayName("실패 - accessToken blank 시 SESSION_EXPIRED")
        void fail_blank_access_token_throws_session_expired() throws Exception {
            // Given
            String linkCode = "test-link-code";
            request.setAttribute(SecurityConstants.LINK_CODE_ATTRIBUTE, linkCode);

            given(jwtCookieHelper.getAccessTokenFromRequest(request)).willReturn("   ");
            given(urlProperty.getFrontUrl()).willReturn("http://localhost:3000");

            // When
            handler.onAuthenticationSuccess(request, response, authentication);

            // Then
            assertThat(response.getRedirectedUrl()).contains("success=false");
            assertThat(response.getRedirectedUrl()).contains("error=AUTH006");
            verify(jwtTokenProvider, never()).getAccessTokenDto(any());
        }

        @Test
        @DisplayName("실패 - memberId mismatch 시 ACCESS_DENIED")
        void fail_member_id_mismatch_throws_access_denied() throws Exception {
            // Given: userDetails.memberId=1L, JWT가 99L 반환 → 불일치
            String linkCode = "test-link-code";
            request.setAttribute(SecurityConstants.LINK_CODE_ATTRIBUTE, linkCode);

            given(jwtCookieHelper.getAccessTokenFromRequest(request)).willReturn("valid-token");
            given(jwtTokenProvider.getAccessTokenDto("valid-token"))
                    .willReturn(new AccessTokenDto("jti", 99L, MemberRole.GENERAL));
            given(urlProperty.getFrontUrl()).willReturn("http://localhost:3000");

            // When
            handler.onAuthenticationSuccess(request, response, authentication);

            // Then
            assertThat(response.getRedirectedUrl()).contains("success=false");
            assertThat(response.getRedirectedUrl()).contains("error=COM002");
            verify(accountLinkCase, never()).linkOauth2Account(anyLong(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("성공 - 유효한 link 시 성공 URL로 리다이렉트")
        void success_valid_link_redirects_to_success_url() throws Exception {
            // Given
            String linkCode = "test-link-code";
            request.setAttribute(SecurityConstants.LINK_CODE_ATTRIBUTE, linkCode);

            given(jwtCookieHelper.getAccessTokenFromRequest(request)).willReturn("valid-token");
            given(jwtTokenProvider.getAccessTokenDto("valid-token"))
                    .willReturn(new AccessTokenDto("jti", 1L, MemberRole.GENERAL));
            given(urlProperty.getFrontUrl()).willReturn("http://localhost:3000");

            AuthAccount linkedAccount = AuthAccount.builder()
                    .authProvider(AuthProvider.DISCORD)
                    .issuer("discord")
                    .subject("discord-123")
                    .email("test@discord.com")
                    .build();
            given(accountLinkCase.linkOauth2Account(eq(1L), eq(AuthProvider.DISCORD), any(), any(), any()))
                    .willReturn(linkedAccount);

            // When
            handler.onAuthenticationSuccess(request, response, authentication);

            // Then
            assertThat(response.getRedirectedUrl()).contains("/account/linked");
            assertThat(response.getRedirectedUrl()).contains("success=true");
            assertThat(response.getRedirectedUrl()).contains("provider=discord");
            verify(accountLinkCase).linkOauth2Account(eq(1L), eq(AuthProvider.DISCORD), any(), any(), any());
            verify(accountLinkCase).consumeLinkCodeByMemberId(1L);
            verify(accountLinkCase, never()).verifyLinkCode(any());
        }
    }

    @Nested
    @DisplayName("handleLogin 테스트")
    class HandleLoginTests {

        @Test
        @DisplayName("성공 - 로그인 시 쿠키 발급 및 리다이렉트")
        void success_login_issues_cookies_and_redirects() throws Exception {
            // Given: linkCode 없음 (일반 로그인)
            given(urlProperty.getFrontUrl()).willReturn("http://localhost:3000");
            given(jwtCookieHelper.getRefreshTokenFromRequest(request)).willReturn(null);

            Cookie atCookie = new Cookie("dev-pat", "access-token-value");
            Cookie rtCookie = new Cookie("dev-prt", "refresh-token-value");
            CookieIssueResult cookieResult = new CookieIssueResult(atCookie, rtCookie);

            given(sessionManagementService.issueAllTokenCookies(1L, "GENERAL", null))
                    .willReturn(cookieResult);

            // When
            handler.onAuthenticationSuccess(request, response, authentication);

            // Then
            assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000");
            assertThat(response.getCookies()).hasSize(2);
            verify(sessionManagementService).issueAllTokenCookies(1L, "GENERAL", null);
        }
    }
}
