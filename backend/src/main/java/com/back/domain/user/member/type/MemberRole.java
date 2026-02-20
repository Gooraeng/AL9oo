package com.back.domain.user.member.type;

import lombok.Getter;

@Getter
public enum MemberRole {
    ADMIN(Integer.MAX_VALUE),
    MODERATOR(ADMIN.getPriority() - 1),
    GENERAL(0),
    GUEST(Integer.MIN_VALUE + 1),
    NONE(Integer.MIN_VALUE);

    private final int priority;

    MemberRole(int priority) {
        this.priority = priority;
    }

    public static MemberRole of(String value) {
        return MemberRole.valueOf(value.toUpperCase());
    }

    public static boolean isMod(MemberRole role) {
        return MemberRole.MODERATOR.priority >= role.priority ;
    }
}
