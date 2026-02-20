package com.back.domain.user.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import org.springframework.util.Assert;

/**
 * 로컬/OAuth 공통 회원가입 요청 DTO.
 *
 * <p>직접 생성하지 않고 {@link #ofOauth2} 팩토리 메서드를 사용하는 것을 권장합니다.
 * OAuth2 흐름에서는 email, password, passwordConfirm 필드가 null이어야 합니다.</p>
 */
@Getter
public class SignUpRequestDto {

    private Long memberId;

    private String email;

    private String password;

    private String passwordConfirm;

    @NotBlank
    private String displayName;

    private boolean essentialTermsAgreed;

    public static SignUpRequestDto ofOauth2(
            @NotNull Oauth2SignUpRequestDto dto, @NotNull Long memberId
    ) {
        SignUpRequestDto builtDto = SignUpRequestDto.builder()
                .memberId(memberId)
                .displayName(dto.displayName())
                .essentialTermsAgreed(dto.essentialTermsAgreed())
                .build();

        // should be not set
        Assert.isNull(builtDto.getEmail(), "Email is not allowed to be set.");
        Assert.isNull(builtDto.getPassword(), "Password is not allowed to be set.");
        Assert.isNull(builtDto.getPasswordConfirm(), "Password confirmation is not allowed to be set.");

        // not null
        Assert.notNull(builtDto.getMemberId(), "Member ID is required.");
        Assert.notNull(builtDto.getDisplayName(), "Display name is required.");

        return builtDto;
    }

    @Builder
    private SignUpRequestDto(
            Long memberId,
            String email,
            String password,
            String passwordConfirm,
            String displayName,
            boolean essentialTermsAgreed
    ) {
        this.memberId = memberId;
        this.email = email;
        this.password = password;
        this.passwordConfirm = passwordConfirm;
        this.displayName = displayName;
        this.essentialTermsAgreed = essentialTermsAgreed;
    }

    private SignUpRequestDto() {}
}
