package com.back.domain.user.auth.controller;

import com.back.domain.user.auth.dto.response.AccountLinkIssueResponseDto;
import com.back.domain.user.auth.dto.response.AccountLinkStatusResponseDto;
import com.back.domain.user.auth.useCase.AccountLinkCase;
import com.back.global.security.member.AuthenticatedMember;
import com.back.global.security.member.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 계정 연동 API 컨트롤러.
 *
 * <p>로그인된 사용자가 다른 OAuth provider를 연동하거나 연동을 해제할 때 사용합니다.
 * linkCode는 OAuth2 state 파라미터를 통해 전달되며, 쿠키는 사용하지 않습니다.</p>
 *
 * <p>L3 충돌 시 병합 플로우 관련 API는 추가 논의 후 구현 예정입니다.</p>
 */
@Tag(name = "계정 연동", description = "OAuth provider 계정 연동·해제 API (GENERAL 이상 접근 가능)")
@RestController
@RequestMapping("/api/v1/auth/account-link")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('GENERAL')")
public class AccountLinkControllerV1 {

    private final AccountLinkCase accountLinkCase;

    /**
     * 현재 Member의 OAuth provider 연동 상태를 조회합니다.
     *
     * @param userDetails 현재 인증된 사용자
     * @return 각 OAuth provider별 연동 상태 목록
     */
    @Operation(summary = "계정 연동 상태 조회", description = "지원하는 OAuth provider별로 연동 여부와 연동 시각을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "연동 상태 반환 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "GENERAL 미만 권한")
    })
    @GetMapping
    public ResponseEntity<?> getAccountLinkingStatus(
            @AuthenticatedMember CustomUserDetails userDetails
    ) {
        List<AccountLinkStatusResponseDto> currentAccountLinkStatus = accountLinkCase.getCurrentAccountLinkStatus(
                userDetails.getMemberId(), userDetails.getProvider()
        );

        return ResponseEntity.ok(currentAccountLinkStatus);
    }

    /**
     * 계정 연동을 시작합니다.
     *
     * <p>응답의 oauthUrl에 linkCode가 쿼리 파라미터로 포함되어 있습니다.
     * 클라이언트는 이 URL로 리다이렉트하면 됩니다.</p>
     *
     * @param userDetails 현재 로그인된 사용자
     * @param provider    연동할 OAuth provider
     * @return linkCode와 oauthUrl을 포함한 응답
     */
    @PostMapping("/start")
    public ResponseEntity<AccountLinkIssueResponseDto> startAccountLinking(
            @AuthenticatedMember CustomUserDetails userDetails,
            @RequestParam String provider
    ) {
        AccountLinkIssueResponseDto responseDto = accountLinkCase.startAccountLinking(
                userDetails.getMemberId(),
                provider
        );

        log.info("Account link started: member={}", userDetails.getMemberId());

        return ResponseEntity.ok(responseDto);
    }

    /**
     * 특정 OAuth provider 연동을 해제합니다.
     *
     * @param userDetails 현재 인증된 사용자
     * @param provider    연동 해제할 OAuth provider 이름 (예: "google", "discord")
     * @return 200 OK
     */
    @Operation(summary = "계정 연동 해제", description = "지정한 OAuth provider와의 연동을 해제합니다. 마지막 남은 provider는 해제할 수 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "연동 해제 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 provider 값 또는 마지막 provider 해제 시도"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "GENERAL 미만 권한")
    })
    @DeleteMapping("/unlink")
    public ResponseEntity<?> unLinkAccount(
            @AuthenticatedMember CustomUserDetails userDetails,
            @Parameter(name = "provider", description = "연동 해제할 OAuth provider", example = "google")
            @RequestParam String provider
    ) {
        accountLinkCase.unlinkAccount(userDetails.getMemberId(), provider);
        return ResponseEntity.ok().build();
    }
}
