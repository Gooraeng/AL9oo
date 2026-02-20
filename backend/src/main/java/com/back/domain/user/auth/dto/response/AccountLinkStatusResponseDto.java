package com.back.domain.user.auth.dto.response;

import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 각 OAuth provider 연동 상태 응답 DTO.
 *
 * <p>연동되지 않은 provider는 {@code linkedAt}이 null로 반환됩니다.</p>
 */
public record AccountLinkStatusResponseDto(
        @Schema(description = "OAuth provider 이름", example = "GOOGLE")
        String provider,

        @Schema(description = "연동 시각. 연동되지 않은 경우 null", nullable = true)
        LocalDateTime linkedAt,

        @Schema(description = "현재 로그인에 사용된 provider 여부")
        Boolean currentProvider
) {
    public static AccountLinkStatusResponseDto linked(AuthAccount account, boolean isCurrent) {
        return new AccountLinkStatusResponseDto(
                account.getAuthProvider().name(),
                account.getLinkedAt(),
                isCurrent
        );
    }

    public static AccountLinkStatusResponseDto notLinked(AuthProvider provider) {
        return new AccountLinkStatusResponseDto(
                provider.name(),
                null,
                false
        );
    }
}
