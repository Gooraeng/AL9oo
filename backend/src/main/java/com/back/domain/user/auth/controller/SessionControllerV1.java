package com.back.domain.user.auth.controller;

import com.back.domain.user.auth.dto.response.SessionStatusResponseDto;
import com.back.domain.user.auth.service.SessionManagementService;
import com.back.global.security.jwt.dto.CookieIssueResult;
import com.back.global.security.member.AuthenticatedMember;
import com.back.global.security.member.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 세션(토큰) 관리 API 컨트롤러.
 *
 * <p>GUEST 이상 권한을 가진 모든 인증 사용자가 접근할 수 있습니다.
 * 로그아웃, 토큰 갱신(RTR), 기기별 세션 조회·삭제 기능을 제공합니다.</p>
 */
@Tag(name = "세션", description = "JWT 세션 관리 API (GUEST 이상 접근 가능)")
@RestController
@RequestMapping("/api/v1/auth/session")
@RequiredArgsConstructor
@PreAuthorize("hasRole('GUEST')")
public class SessionControllerV1 {

    private final SessionManagementService sessionManagementService;

    /**
     * 현재 기기에서 로그아웃합니다.
     *
     * @param request     HTTP 요청 (RT 쿠키 포함)
     * @param response    HTTP 응답 (쿠키 무효화용)
     * @param userDetails 현재 인증된 사용자
     * @return 로그아웃 완료 메시지
     */
    @Operation(summary = "로그아웃", description = "현재 기기의 세션을 종료하고 AT/RT 쿠키를 무효화합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticatedMember CustomUserDetails userDetails
    ) {
        sessionManagementService.logoutCurrentDevice(userDetails.getMemberId(), request, response);

        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    /**
     * RTR 방식으로 AT/RT 토큰을 갱신합니다.
     *
     * @param request  HTTP 요청 (기존 RT 쿠키 포함)
     * @param response HTTP 응답 (새 AT/RT 쿠키 설정)
     * @return 200 OK (새 쿠키는 응답 헤더에 포함)
     */
    @Operation(
            summary = "토큰 갱신 (RTR)",
            description = "기존 Refresh Token을 사용해 새 Access Token과 Refresh Token을 발급합니다. 토큰 재사용 감지 시 전체 세션이 무효화됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 갱신 성공 (새 쿠키가 응답에 포함)"),
            @ApiResponse(responseCode = "401", description = "RT가 유효하지 않거나 만료됨")
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<?> reissueRefreshTokens(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        CookieIssueResult result = sessionManagementService.rotateTokens(request);

        response.addCookie(result.accessTokenCookie());
        response.addCookie(result.refreshTokenCookie());

        return ResponseEntity.ok().build();
    }

    /**
     * 특정 기기의 세션을 삭제합니다.
     *
     * @param request     HTTP 요청 (현재 기기의 RT 포함)
     * @param userDetails 현재 인증된 사용자
     * @param deviceId    삭제할 기기 ID
     * @return 200 OK
     */
    @Operation(summary = "특정 기기 세션 삭제", description = "지정한 deviceId의 세션을 삭제합니다. 현재 기기는 삭제할 수 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "세션 삭제 성공"),
            @ApiResponse(responseCode = "400", description = "현재 기기의 deviceId를 지정한 경우"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @DeleteMapping("/invalidate")
    public ResponseEntity<?> removeSession(
            HttpServletRequest request,
            @AuthenticatedMember CustomUserDetails userDetails,
            @Parameter(name = "deviceId", description = "삭제할 기기 ID", example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestParam String deviceId
    ) {
        sessionManagementService.removeDevice(userDetails.getMemberId(), deviceId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 모든 기기의 세션을 일괄 삭제합니다.
     *
     * @param userDetails 현재 인증된 사용자
     * @return 200 OK
     */
    @Operation(summary = "전체 세션 삭제", description = "현재 Member의 모든 기기 세션을 삭제합니다. 이후 모든 기기에서 재로그인이 필요합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "전체 세션 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @DeleteMapping("/invalidate/all")
    public ResponseEntity<?> removeAllSession(
            @AuthenticatedMember CustomUserDetails userDetails
    ) {
        sessionManagementService.cleanUpAllDevices(userDetails.getMemberId());
        return ResponseEntity.ok().build();
    }

    /**
     * 현재 Member의 모든 세션 목록을 반환합니다.
     *
     * @param request     HTTP 요청 (현재 기기의 RT 포함)
     * @param userDetails 현재 인증된 사용자
     * @return 기기별 세션 상태 목록 (현재 기기 여부 포함)
     */
    @Operation(summary = "세션 목록 조회", description = "로그인된 모든 기기의 세션 목록을 반환합니다. 현재 기기는 currentSession=true로 표시됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "세션 목록 반환 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @GetMapping
    public ResponseEntity<List<SessionStatusResponseDto>> getAllSession(
            HttpServletRequest request,
            @AuthenticatedMember CustomUserDetails userDetails
    ) {
        List<SessionStatusResponseDto> allCurrentSession = sessionManagementService
                .getAllExistingSession(userDetails.getMemberId(), request);
        return ResponseEntity.ok().body(allCurrentSession);
    }
}
