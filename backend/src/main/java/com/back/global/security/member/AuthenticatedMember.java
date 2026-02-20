package com.back.global.security.member;

import com.back.domain.user.member.type.MemberRole;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthenticatedMember {

    MemberRole required() default MemberRole.GUEST;
}
