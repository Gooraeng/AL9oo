package com.back.domain.user.member.controller;

import com.back.domain.user.member.dto.MemberUpdateRequestDto;
import com.back.domain.user.member.dto.response.PublicMemberDataResponseDto;
import com.back.domain.user.member.dto.response.PrivateMemberDataResponseDto;
import com.back.domain.user.member.useCase.MemberInfoUseCase;
import com.back.global.security.member.AuthenticatedMember;
import com.back.global.security.member.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/member")
@RequiredArgsConstructor
public class MemberControllerV1 {

    private final MemberInfoUseCase memberInfoUseCase;

    // Todo: if CustomUserDetails exists and the {id} equals to memberId,
    //  probably return member's private info.
    @GetMapping("/{targetMemberId}")
    public ResponseEntity<PublicMemberDataResponseDto> getPublicMemberInfo(
            @PathVariable Long targetMemberId
    ){
        PublicMemberDataResponseDto dto = memberInfoUseCase
                .getPublicMemberInfo(targetMemberId);

        return ResponseEntity.ok(dto);
    }

    // Todo: you must be authenticated to access this endpoint.
    //  FE calls it on every request so cache could be needed.
    //  Consider using @Cachable annotation.
    @GetMapping("/me")
    public ResponseEntity<PrivateMemberDataResponseDto> getMyInfo(
            @AuthenticatedMember CustomUserDetails userDetails
    ) {
        PrivateMemberDataResponseDto dto = memberInfoUseCase
                .getPrivateMemberInfo(userDetails.getMemberId());

        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/me")
    public ResponseEntity<?> updateMyProfile(
            @RequestBody @Valid MemberUpdateRequestDto dto,
            @AuthenticatedMember CustomUserDetails userDetails
    ) {
        memberInfoUseCase.updateMemberInfo(userDetails.getMemberId(), dto);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> withdrawService(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        memberInfoUseCase.withdrawUsingService(userDetails.getMemberId());

        return ResponseEntity.ok().build();
    }
}
