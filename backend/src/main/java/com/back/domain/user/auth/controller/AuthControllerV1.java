package com.back.domain.user.auth.controller;

import com.back.domain.user.auth.dto.request.Oauth2SignUpRequestDto;
import com.back.domain.user.auth.dto.request.SignUpRequestDto;
import com.back.domain.user.auth.dto.response.NickNameCheckResultResponseDto;
import com.back.domain.user.auth.dto.response.SignUpResponseDto;
import com.back.domain.user.auth.useCase.Oauth2SignUpOrLoginCase;
import com.back.domain.user.member.service.MemberService;
import com.back.global.security.member.AuthenticatedMember;
import com.back.global.security.member.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 회원가입 및 닉네임 중복 검사 API 컨트롤러.
 *
 * <p>OAuth2 가입 완료 처리와 닉네임 사용 가능 여부 확인 엔드포인트를 제공합니다.</p>
 */
@Tag(name = "인증", description = "회원가입 및 인증 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthControllerV1 {

    private final Oauth2SignUpOrLoginCase oAuth2SignUpOrLoginCase;
    private final MemberService memberService;

    /**
     * 닉네임 사용 가능 여부를 확인합니다.
     *
     * @param name 검사할 닉네임
     * @return 닉네임 사용 가능 여부
     */
    @Operation(summary = "닉네임 중복 확인", description = "사용하려는 닉네임이 이미 사용 중인지 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확인 완료 (available 필드로 사용 가능 여부 반환)"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 닉네임 형식")
    })
    @GetMapping("/check-display-name/{name}")
    public ResponseEntity<NickNameCheckResultResponseDto> checkUserDisplayNameDuplication(
            @Parameter(name = "name", description = "중복 확인할 닉네임", example = "레이서_123")
            @PathVariable @NotNull String name
    ) {
        NickNameCheckResultResponseDto dto = memberService.checkDisplayNameDuplication(name);
        return ResponseEntity.ok(dto);
    }

    /**
     * OAuth2 가입 완료 처리를 수행합니다.
     *
     * @param requestDto  닉네임 및 약관 동의 정보
     * @param userDetails 현재 인증된 GUEST 사용자 정보
     * @return 가입 완료 메시지
     */
    @Operation(
            summary = "OAuth2 회원가입 완료",
            description = "OAuth2 최초 로그인 후 닉네임 설정 및 약관 동의를 통해 가입을 완료합니다. GUEST 권한이 필요합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "가입 완료"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "403", description = "이미 가입 완료된 사용자 (GENERAL 이상 권한 보유)"),
            @ApiResponse(responseCode = "409", description = "닉네임 중복 또는 이미 가입 완료된 경우")
    })
    @PostMapping("/oauth2/signup")
    @PreAuthorize("!hasRole('GENERAL')")
    public ResponseEntity<SignUpResponseDto> finalizedOAuthSignUp(
            @RequestBody @Valid Oauth2SignUpRequestDto requestDto,
            @AuthenticatedMember CustomUserDetails userDetails
    ) {
        SignUpResponseDto dto = oAuth2SignUpOrLoginCase.doSignUp(
                SignUpRequestDto.ofOauth2(requestDto, userDetails.getMemberId()));

        return ResponseEntity.ok(dto);
    }
}
