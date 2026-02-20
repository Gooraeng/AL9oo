package com.back.domain.user.auth.service;

import com.back.domain.user.auth.dto.response.SessionStatusResponseDto;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.repository.MemberRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.List;

/**
 * JWT 토큰 발급·갱신·무효화 및 다기기 세션 관리 서비스.
 *
 * <p>Access Token(AT)과 Refresh Token(RT)을 쿠키로 관리하며,
 * RTR(Refresh Token Rotation) 방식으로 토큰 재사용을 감지합니다.
 * 기기별 독립 세션을 지원하므로 한 계정에서 여러 기기 동시 로그인이 가능합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManagementService {

    private final JwtCookieHelper jwtCookieHelper;
    private final JwtTokenProvider jwtTokenProvider;

    private final MemberRepository memberRepository;

    private final RefreshTokenManager refreshTokenManager;
    private final RefreshTokenGraceManager graceManager;

    /**
     * RTR 방식으로 AT/RT를 갱신합니다.
     *
     * <p>기존 RT를 검증하고 새 AT/RT를 발급합니다. JTI가 Redis에 저장된 값과 다를 경우
     * Grace Period를 확인하며, Grace Period도 지났다면 토큰 탈취로 판단하여
     * 해당 Member의 전체 세션을 강제 삭제합니다.</p>
     *
     * @param request RT 쿠키가 포함된 HTTP 요청
     * @return 새로 발급된 AT/RT 쿠키 묶음
     * @throws ApiException RT 검증 실패 또는 재사용 감지 시 {@code ErrorCode.SESSION_EXPIRED}
     */
    public CookieIssueResult rotateTokens(HttpServletRequest request) {
        String previousRtValue = jwtCookieHelper.getRefreshTokenFromRequest(request);

        // 401 will be raised when validation fails
        RefreshTokenDto refreshTokenDto = jwtTokenProvider.getRefreshTokenDto(previousRtValue);

        String jti = refreshTokenDto.jti();
        Long memberId = refreshTokenDto.memberId();
        String deviceId = refreshTokenDto.deviceId();

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Member not found."));

        RefreshTokenDomain storedRt = refreshTokenManager.findRefreshTokenByDeviceId(deviceId);

        if (storedRt.isIdleExpired()) {
            cleanUpAllDevices(memberId);
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }

        if (!jti.equals(storedRt.getJti())) {
            if (!graceManager.isInGracePeriod(deviceId, jti)) {
                log.error("RT reuse detected! Potential token theft. memberId={}", memberId);
                cleanUpAllDevices(memberId);
                throw new ApiException(ErrorCode.SESSION_EXPIRED);
            }
            log.warn("RT reuse within grace period. memberId={}, deviceId={}", memberId, deviceId);
        } else graceManager.addToGracePeriod(deviceId, storedRt.getJti());

        return issueAllTokenCookies(memberId, member.getRole().name(), deviceId);
    }

    /**
     * 모든 인증 쿠키(AT, RT)를 생성하고 response에 추가합니다.
     * OAuth 로그인, 로컬 로그인, RTR 등에서 공통으로 사용됩니다.
     *
     * @param memberId    회원 ID
     * @param role        권한
     * @param deviceId    기존 deviceId (null이면 새로 생성)
     */
    public CookieIssueResult issueAllTokenCookies(
            Long memberId,
            String role,
            @Nullable String deviceId
    ) {
        Cookie accessTokenCookie = jwtCookieHelper.createAccessTokenCookie(memberId, role);

        RefreshTokenDomain saved = refreshTokenManager
                .issueOrUpdateRefreshToken(memberId, deviceId);

        Cookie refreshTokenCookie = jwtCookieHelper.createRefreshTokenCookie(memberId, saved.getDeviceId());

        return new CookieIssueResult(accessTokenCookie, refreshTokenCookie);
    }

    /**
     * 현재 요청의 RT에서 deviceId를 추출하여, 해당 기기를 현재 세션으로 표시한 전체 세션 목록을 반환합니다.
     *
     * @param memberId Member ID
     * @param request  RT 쿠키가 포함된 HTTP 요청
     * @return 기기별 세션 상태 목록 (현재 기기 여부 포함)
     */
    public List<SessionStatusResponseDto> getAllExistingSession(Long memberId, HttpServletRequest request) {
        String refreshToken = jwtCookieHelper.getRefreshTokenFromRequest(request);
        String deviceId = jwtTokenProvider
                .getRefreshTokenDto(refreshToken)
                .deviceId();

        return getAllExistingSession(memberId, deviceId);
    }

    private List<SessionStatusResponseDto> getAllExistingSession(Long memberId, String deviceId) {
        return getAllRefreshTokens(memberId)
                .stream()
                .map(r -> SessionStatusResponseDto.fromDomain(r, deviceId))
                .toList();
    }

    private List<RefreshTokenDomain> getAllRefreshTokens(Long memberId) {
        List<RefreshTokenDomain> allRefreshTokens = refreshTokenManager.findAllRefreshTokens(memberId);

        if (allRefreshTokens.isEmpty())
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "You have no active sessions. Please login again.");

        return allRefreshTokens;
    }

    /**
     * 현재 기기를 제외한 특정 기기의 세션을 삭제합니다.
     *
     * <p>현재 기기(요청 RT의 deviceId)와 동일한 deviceId로 삭제 요청이 오면 거부합니다.
     * 현재 기기 로그아웃은 {@link #logoutCurrentDevice}를 사용해야 합니다.</p>
     *
     * @param memberId Member ID
     * @param deviceId 삭제할 기기 ID
     * @param request  RT 쿠키가 포함된 HTTP 요청
     * @throws ApiException 현재 기기와 동일한 deviceId인 경우 {@code ErrorCode.UNSUPPORTED_WAY}
     */
    public void removeDevice(Long memberId, String deviceId, HttpServletRequest request) {
        String refreshToken = jwtCookieHelper.getRefreshTokenFromRequest(request);
        String originDeviceId = jwtTokenProvider
                .getRefreshTokenDto(refreshToken)
                .deviceId();

        if (originDeviceId.equals(deviceId))
            throw new ApiException(ErrorCode.UNSUPPORTED_WAY, "Logout from current session is invalid with this way.");

        removeDevice(memberId, deviceId);
    }

    public void removeDevice(Long memberId, String deviceId) {
        refreshTokenManager.deleteRefreshTokenWithMemberId(deviceId, memberId);
    }

    /**
     * 현재 기기에서 로그아웃합니다.
     *
     * <p>RT를 파싱하여 현재 기기의 세션을 삭제하고, 응답 쿠키를 무효화합니다.
     * RT 파싱 실패(쿠키 없음, 만료 등) 시에도 쿠키 무효화는 반드시 수행됩니다.
     * memberId가 RT의 memberId와 다를 경우 해당 Member의 전체 세션을 삭제합니다.</p>
     *
     * @param memberId Member ID
     * @param request  RT 쿠키가 포함된 HTTP 요청
     * @param response 쿠키 무효화를 위한 HTTP 응답
     */
    public void logoutCurrentDevice(
            Long memberId,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            String rf = jwtCookieHelper.getRefreshTokenFromRequest(request);
            RefreshTokenDto refreshTokenDto = jwtTokenProvider.getRefreshTokenDto(rf);

            if (!memberId.equals(refreshTokenDto.memberId())) {
                log.warn("Member ID does not match. Logout from all device of member.");
                cleanUpAllDevices(memberId);
                return;
            }
            removeDevice(memberId, refreshTokenDto.deviceId());
        } catch (Exception _) {
            // refresh token이 없거나 파싱 실패 시 무시 (쿠키는 컨트롤러에서 삭제됨)
        } finally {
            jwtCookieHelper.invalidateAuthCookies(response);
        }
    }

    /**
     * 해당 Member의 모든 기기 세션을 강제 삭제합니다.
     *
     * <p>보안 위협(토큰 재사용 감지, 이상 행동 등) 발생 시 전체 세션을 일괄 삭제합니다.
     * 삭제 후 모든 기기에서 재인증이 필요합니다.</p>
     *
     * @param memberId 세션을 삭제할 Member ID
     */
    public void cleanUpAllDevices(Long memberId) {
        refreshTokenManager.deleteAllRefreshTokensByMemberId(memberId);
    }
}
