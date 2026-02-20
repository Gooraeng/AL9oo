package com.back.domain.user.member.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record MemberUpdateRequestDto (
        @NotBlank(message = "Nickname must not be blank")
        @Length(min = 4, max = 20, message = "Nickname must be at from 4 to 20 characters long")
        String displayName
){
}
