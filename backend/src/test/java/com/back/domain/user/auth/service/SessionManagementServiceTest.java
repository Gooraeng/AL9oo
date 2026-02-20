package com.back.domain.user.auth.service;

import com.back.domain.user.auth.dto.response.SessionStatusResponseDto;
import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.repository.MemberRepository;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.jwt.JwtCookieHelper;
import com.back.global.security.jwt.dto.CookieIssueResult;
import com.back.global.security.jwt.dto.RefreshTokenDto;
import com.back.global.security.jwt.refreshToken.domain.RefreshTokenDomain;
import com.back.global.security.jwt.refreshToken.service.RefreshTokenGraceManager;
import com.back.global.security.jwt.refreshToken.service.RefreshTokenManager;
import com.back.global.security.jwt.service.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("SessionManagementService 테스트")
class SessionManagementServiceTest {

    @Mock private JwtCookieHelper jwtCookieHelper;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private MemberRepository memberRepository;
    @Mock private RefreshTokenManager refreshTokenManager;
    @Mock private RefreshTokenGraceManager graceManager;

    @InjectMocks private SessionManagementService sessionManagementService;

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    // Refresh Token
    private final String testPreviousRefreshToken = "refresh-token";
    private final String testRefreshTokenJti = "refresh-token-jti";
    private final String testDeviceId = "test-device-id";

    private Member testMember;
    private RefreshTokenDomain testRefreshDomain;

    @BeforeEach
    void setUp() {
        testMember = Member.builder()
                .displayName("GOOGLE-123456")
                .memberStatus(MemberStatus.ACTIVE)
                .essentialTermsAgreed(false)
                .role(MemberRole.GUEST)
                .authAccounts(new ArrayList<>())
                .build();
        ReflectionTestUtils.setField(testMember, "id", 1L);

        AuthAccount testGoogleAccount = AuthAccount.builder()
                .member(testMember)
                .authProvider(AuthProvider.GOOGLE)
                .issuer("google")
                .subject("123456")
                .email("test@example.com")
                .build();
        ReflectionTestUtils.setField(testGoogleAccount, "id", 1L);

        testMember.getAuthAccounts().add(testGoogleAccount);

        testRefreshDomain = RefreshTokenDomain.initial(
                "test_id",
                testMember.getId(),
                testRefreshTokenJti,
                testDeviceId,
                604_800L
        );
    }

    @Nested
    @DisplayName("rotateTokens() 테스트")
    class RotateTokensTests {

        @Test
        @DisplayName("성공 - AT/RT 재발급 및 grace period 등록")
        void success_rotate_tokens_and_register_grace_period() {
            // given
            RefreshTokenDto mockDto = new RefreshTokenDto(testRefreshTokenJti, 1L, testDeviceId);
            RefreshTokenDomain savedDomain = RefreshTokenDomain.initial(
                    "saved_id", 1L, "new-jti", testDeviceId, 604_800L);

            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willReturn(testPreviousRefreshToken);
            given(jwtTokenProvider.getRefreshTokenDto(testPreviousRefreshToken))
                    .willReturn(mockDto);
            given(memberRepository.findById(1L))
                    .willReturn(Optional.of(testMember));
            given(refreshTokenManager.findRefreshTokenByDeviceId(testDeviceId))
                    .willReturn(testRefreshDomain);
            given(jwtCookieHelper.createAccessTokenCookie(1L, MemberRole.GUEST.name()))
                    .willReturn(new Cookie("dev-pat", "at-value"));
            given(refreshTokenManager.issueOrUpdateRefreshToken(1L, testDeviceId))
                    .willReturn(savedDomain);
            given(jwtCookieHelper.createRefreshTokenCookie(1L, testDeviceId))
                    .willReturn(new Cookie("dev-prt", "rt-value"));

            // when
            CookieIssueResult result = sessionManagementService.rotateTokens(request);

            // then
            assertThat(result.accessTokenCookie()).isNotNull();
            assertThat(result.refreshTokenCookie()).isNotNull();
            verify(graceManager).addToGracePeriod(testDeviceId, testRefreshTokenJti);
        }

        @Test
        @DisplayName("성공 - Grace period 내 RT 재사용 허용")
        void success_reuse_within_grace_period() {
            // given
            String staleJti = "stale-jti";
            RefreshTokenDto mockDto = new RefreshTokenDto(staleJti, 1L, testDeviceId);
            RefreshTokenDomain savedDomain = RefreshTokenDomain.initial(
                    "saved_id", 1L, "new-jti", testDeviceId, 604_800L);

            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willReturn(testPreviousRefreshToken);
            given(jwtTokenProvider.getRefreshTokenDto(testPreviousRefreshToken))
                    .willReturn(mockDto);
            given(memberRepository.findById(1L))
                    .willReturn(Optional.of(testMember));
            given(refreshTokenManager.findRefreshTokenByDeviceId(testDeviceId))
                    .willReturn(testRefreshDomain);
            given(graceManager.isInGracePeriod(testDeviceId, staleJti))
                    .willReturn(true);
            given(jwtCookieHelper.createAccessTokenCookie(1L, MemberRole.GUEST.name()))
                    .willReturn(new Cookie("dev-pat", "at-value"));
            given(refreshTokenManager.issueOrUpdateRefreshToken(1L, testDeviceId))
                    .willReturn(savedDomain);
            given(jwtCookieHelper.createRefreshTokenCookie(1L, testDeviceId))
                    .willReturn(new Cookie("dev-prt", "rt-value"));

            // when
            CookieIssueResult result = sessionManagementService.rotateTokens(request);

            // then
            assertThat(result.accessTokenCookie()).isNotNull();
            assertThat(result.refreshTokenCookie()).isNotNull();
            verify(graceManager, never()).addToGracePeriod(any(), any());
        }

        @Test
        @DisplayName("실패 - 401 - Idle TTL 만료로 세션 만료")
        void fail_401_idle_ttl_expired() {
            // given
            RefreshTokenDto mockDto = new RefreshTokenDto(testRefreshTokenJti, 1L, testDeviceId);

            RefreshTokenDomain idleExpiredDomain = RefreshTokenDomain.initial(
                    "test_id", 1L, testRefreshTokenJti, testDeviceId, 604_800L);
            ReflectionTestUtils.setField(idleExpiredDomain, "lastCheckedDate",
                    LocalDateTime.now().minusDays(8));

            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willReturn(testPreviousRefreshToken);
            given(jwtTokenProvider.getRefreshTokenDto(testPreviousRefreshToken))
                    .willReturn(mockDto);
            given(memberRepository.findById(1L))
                    .willReturn(Optional.of(testMember));
            given(refreshTokenManager.findRefreshTokenByDeviceId(testDeviceId))
                    .willReturn(idleExpiredDomain);

            // when & then
            assertThatThrownBy(() -> sessionManagementService.rotateTokens(request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SESSION_EXPIRED));

            verify(refreshTokenManager).deleteAllRefreshTokensByMemberId(1L);
        }

        @Test
        @DisplayName("실패 - 401 - Grace period 외 RT 재사용 (토큰 탈취)")
        void fail_401_reuse_outside_grace_period() {
            // given
            String staleJti = "stale-jti";
            RefreshTokenDto mockDto = new RefreshTokenDto(staleJti, 1L, testDeviceId);

            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willReturn(testPreviousRefreshToken);
            given(jwtTokenProvider.getRefreshTokenDto(testPreviousRefreshToken))
                    .willReturn(mockDto);
            given(memberRepository.findById(1L))
                    .willReturn(Optional.of(testMember));
            given(refreshTokenManager.findRefreshTokenByDeviceId(testDeviceId))
                    .willReturn(testRefreshDomain);
            given(graceManager.isInGracePeriod(testDeviceId, staleJti))
                    .willReturn(false);

            // when & then
            assertThatThrownBy(() -> sessionManagementService.rotateTokens(request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SESSION_EXPIRED));

            verify(refreshTokenManager).deleteAllRefreshTokensByMemberId(1L);
        }

        @Test
        @DisplayName("실패 - 401 - JWT 파싱 실패")
        void fail_401_jwt_parsing_failure() {
            // given
            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willReturn(testPreviousRefreshToken);
            given(jwtTokenProvider.getRefreshTokenDto(testPreviousRefreshToken))
                    .willThrow(new ApiException(ErrorCode.SESSION_EXPIRED));

            // when & then
            assertThatThrownBy(() -> sessionManagementService.rotateTokens(request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SESSION_EXPIRED));
        }

        @Test
        @DisplayName("실패 - 404 - Member 미존재")
        void fail_404_member_not_found() {
            // given
            RefreshTokenDto mockDto = new RefreshTokenDto(testRefreshTokenJti, 1L, testDeviceId);

            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willReturn(testPreviousRefreshToken);
            given(jwtTokenProvider.getRefreshTokenDto(testPreviousRefreshToken))
                    .willReturn(mockDto);
            given(memberRepository.findById(1L))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sessionManagementService.rotateTokens(request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }

        @Test
        @DisplayName("실패 - 401 - Redis에 RT 없음")
        void fail_401_refresh_token_not_in_redis() {
            // given
            RefreshTokenDto mockDto = new RefreshTokenDto(testRefreshTokenJti, 1L, testDeviceId);

            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willReturn(testPreviousRefreshToken);
            given(jwtTokenProvider.getRefreshTokenDto(testPreviousRefreshToken))
                    .willReturn(mockDto);
            given(memberRepository.findById(1L))
                    .willReturn(Optional.of(testMember));
            given(refreshTokenManager.findRefreshTokenByDeviceId(testDeviceId))
                    .willThrow(new ApiException(ErrorCode.SESSION_EXPIRED));

            // when & then
            assertThatThrownBy(() -> sessionManagementService.rotateTokens(request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SESSION_EXPIRED));
        }
    }

    @Nested
    @DisplayName("issueAllTokenCookies() 테스트")
    class IssueAllTokenCookiesTests {

        @Test
        @DisplayName("성공 - AT, RT 쿠키 발급")
        void success_issue_both_cookies() {
            // given
            RefreshTokenDomain savedDomain = RefreshTokenDomain.initial(
                    "saved_id", 1L, "new-jti", testDeviceId, 604_800L);

            given(jwtCookieHelper.createAccessTokenCookie(1L, MemberRole.GUEST.name()))
                    .willReturn(new Cookie("dev-pat", "at-value"));
            given(refreshTokenManager.issueOrUpdateRefreshToken(1L, testDeviceId))
                    .willReturn(savedDomain);
            given(jwtCookieHelper.createRefreshTokenCookie(1L, testDeviceId))
                    .willReturn(new Cookie("dev-prt", "rt-value"));

            // when
            CookieIssueResult result = sessionManagementService
                    .issueAllTokenCookies(1L, MemberRole.GUEST.name(), testDeviceId);

            // then
            assertThat(result.accessTokenCookie()).isNotNull();
            assertThat(result.accessTokenCookie().getName()).isEqualTo("dev-pat");
            assertThat(result.refreshTokenCookie()).isNotNull();
            assertThat(result.refreshTokenCookie().getName()).isEqualTo("dev-prt");
        }

        @Test
        @DisplayName("성공 - deviceId null 시 새 디바이스 발급")
        void success_issue_with_null_device_id() {
            // given
            String newDeviceId = "new-device-id";
            RefreshTokenDomain savedDomain = RefreshTokenDomain.initial(
                    "saved_id", 1L, "new-jti", newDeviceId, 604_800L);

            given(jwtCookieHelper.createAccessTokenCookie(1L, MemberRole.GUEST.name()))
                    .willReturn(new Cookie("dev-pat", "at-value"));
            given(refreshTokenManager.issueOrUpdateRefreshToken(1L, null))
                    .willReturn(savedDomain);
            given(jwtCookieHelper.createRefreshTokenCookie(1L, newDeviceId))
                    .willReturn(new Cookie("dev-prt", "rt-value"));

            // when
            CookieIssueResult result = sessionManagementService
                    .issueAllTokenCookies(1L, MemberRole.GUEST.name(), null);

            // then
            assertThat(result.accessTokenCookie()).isNotNull();
            assertThat(result.refreshTokenCookie()).isNotNull();
            verify(refreshTokenManager).issueOrUpdateRefreshToken(1L, null);
        }
    }

    @Nested
    @DisplayName("getAllExistingSession() 테스트")
    class GetAllExistingSessionTests {

        @Test
        @DisplayName("성공 - 세션 목록 반환 및 현재 세션 표시")
        void success_return_sessions_with_current_flag() {
            // given
            String otherDeviceId = "other-device-id";
            RefreshTokenDto mockDto = new RefreshTokenDto(testRefreshTokenJti, 1L, testDeviceId);

            RefreshTokenDomain otherDomain = RefreshTokenDomain.initial(
                    "other_id", 1L, "other-jti", otherDeviceId, 604_800L);

            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willReturn(testPreviousRefreshToken);
            given(jwtTokenProvider.getRefreshTokenDto(testPreviousRefreshToken))
                    .willReturn(mockDto);
            given(refreshTokenManager.findAllRefreshTokens(1L))
                    .willReturn(List.of(testRefreshDomain, otherDomain));

            // when
            List<SessionStatusResponseDto> sessions = sessionManagementService
                    .getAllExistingSession(1L, request);

            // then
            assertThat(sessions).hasSize(2);

            SessionStatusResponseDto currentSession = sessions.stream()
                    .filter(SessionStatusResponseDto::currentSession)
                    .findFirst().orElseThrow();
            assertThat(currentSession.deviceId()).isEqualTo(testDeviceId);

            SessionStatusResponseDto otherSession = sessions.stream()
                    .filter(s -> !s.currentSession())
                    .findFirst().orElseThrow();
            assertThat(otherSession.deviceId()).isEqualTo(otherDeviceId);
        }

        @Test
        @DisplayName("실패 - 404 - 활성 세션 없음")
        void fail_404_no_active_sessions() {
            // given
            RefreshTokenDto mockDto = new RefreshTokenDto(testRefreshTokenJti, 1L, testDeviceId);

            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willReturn(testPreviousRefreshToken);
            given(jwtTokenProvider.getRefreshTokenDto(testPreviousRefreshToken))
                    .willReturn(mockDto);
            given(refreshTokenManager.findAllRefreshTokens(1L))
                    .willReturn(List.of());

            // when & then
            assertThatThrownBy(() -> sessionManagementService.getAllExistingSession(1L, request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("removeDevice() 테스트")
    class RemoveDeviceTests {

        @Test
        @DisplayName("성공 - 다른 디바이스 세션 제거")
        void success_remove_other_device() {
            // given
            String targetDeviceId = "target-device-id";
            RefreshTokenDto mockDto = new RefreshTokenDto(testRefreshTokenJti, 1L, testDeviceId);

            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willReturn(testPreviousRefreshToken);
            given(jwtTokenProvider.getRefreshTokenDto(testPreviousRefreshToken))
                    .willReturn(mockDto);

            // when
            sessionManagementService.removeDevice(1L, targetDeviceId, request);

            // then
            verify(refreshTokenManager).deleteRefreshTokenWithMemberId(targetDeviceId, 1L);
        }

        @Test
        @DisplayName("실패 - 400 - 현재 세션 제거 시도")
        void fail_400_remove_current_session() {
            // given
            RefreshTokenDto mockDto = new RefreshTokenDto(testRefreshTokenJti, 1L, testDeviceId);

            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willReturn(testPreviousRefreshToken);
            given(jwtTokenProvider.getRefreshTokenDto(testPreviousRefreshToken))
                    .willReturn(mockDto);

            // when & then
            assertThatThrownBy(() -> sessionManagementService.removeDevice(1L, testDeviceId, request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNSUPPORTED_WAY));
        }
    }

    @Nested
    @DisplayName("logoutCurrentDevice() 테스트")
    class LogoutCurrentDeviceTests {

        @Test
        @DisplayName("성공 - 현재 디바이스 로그아웃")
        void success_logout_current_device() {
            // given
            RefreshTokenDto mockDto = new RefreshTokenDto(testRefreshTokenJti, 1L, testDeviceId);

            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willReturn(testPreviousRefreshToken);
            given(jwtTokenProvider.getRefreshTokenDto(testPreviousRefreshToken))
                    .willReturn(mockDto);

            // when
            sessionManagementService.logoutCurrentDevice(1L, request, response);

            // then
            verify(refreshTokenManager).deleteRefreshTokenWithMemberId(testDeviceId, 1L);
            verify(jwtCookieHelper).invalidateAuthCookies(response);
        }

        @Test
        @DisplayName("성공 - memberId 불일치 시 전체 세션 정리")
        void success_cleanup_all_when_member_id_mismatch() {
            // given
            RefreshTokenDto mockDto = new RefreshTokenDto(testRefreshTokenJti, 999L, testDeviceId);

            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willReturn(testPreviousRefreshToken);
            given(jwtTokenProvider.getRefreshTokenDto(testPreviousRefreshToken))
                    .willReturn(mockDto);

            // when
            sessionManagementService.logoutCurrentDevice(1L, request, response);

            // then
            verify(refreshTokenManager).deleteAllRefreshTokensByMemberId(1L);
            verify(refreshTokenManager, never()).deleteRefreshTokenWithMemberId(any(), any());
            verify(jwtCookieHelper).invalidateAuthCookies(response);
        }

        @Test
        @DisplayName("성공 - RT 파싱 실패 시 쿠키만 무효화")
        void success_invalidate_cookies_on_parse_failure() {
            // given
            given(jwtCookieHelper.getRefreshTokenFromRequest(request))
                    .willThrow(new RuntimeException("No cookie"));

            // when
            sessionManagementService.logoutCurrentDevice(1L, request, response);

            // then
            verify(jwtCookieHelper).invalidateAuthCookies(response);
            verify(refreshTokenManager, never()).deleteRefreshTokenWithMemberId(any(), any());
            verify(refreshTokenManager, never()).deleteAllRefreshTokensByMemberId(any());
        }
    }

    @Nested
    @DisplayName("cleanUpAllDevices() 테스트")
    class CleanUpAllDevicesTests {

        @Test
        @DisplayName("성공 - 전체 디바이스 RT 삭제")
        void success_cleanup_all_devices() {
            // when
            sessionManagementService.cleanUpAllDevices(1L);

            // then
            verify(refreshTokenManager).deleteAllRefreshTokensByMemberId(1L);
        }
    }
}
