package com.back.domain.user.auth.entity;

import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.member.entity.Member;
import com.back.global.entity.Editable;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Member 한 명이 가진 인증 계정.
 *
 * <p>하나의 Member는 여러 AuthAccount를 가질 수 있으며, 각각 다른 OAuth provider 또는 로컬 인증을 나타냅니다.
 * (provider, issuer, subject) 3-tuple이 전역 고유 키이며, (email, provider) 조합도 고유해야 합니다.</p>
 */
@Entity
@Table(
        name = "auth_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_identity_provider_issuer_subject",
                        columnNames = {"auth_provider", "issuer", "subject"}
                ),
                @UniqueConstraint(
                        // 동일 provider에서 같은 이메일로 중복 계정 생성 방지
                        name = "uk_member_identity_email_provider",
                        columnNames = {"email", "auth_provider"}
                )
        },
        indexes = {
                @Index(name = "idx_member_identities_member_id", columnList = "member_id")
        }
)
@Getter @Setter @Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthAccount extends Editable {

    /** 이 인증 계정이 속한 Member. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    /** 로컬 인증 전용 BCrypt 해시 비밀번호. OAuth 계정은 null. */
    @Column
    private String password;

    /** 인증 제공자 종류 (GOOGLE, DISCORD, LOCAL). */
    @Column(nullable = false, name = "auth_provider", length = 20)
    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

    /** OAuth 토큰 발급 기관 (예: https://accounts.google.com). 로컬은 앱 기본값. */
    @Column(nullable = false)
    private String issuer;

    /** Provider가 부여한 사용자 고유 식별자 (sub). */
    @Column(nullable = false)
    private String subject;

    /** 인증 계정에 연결된 이메일 주소. */
    @Column(length = 350, nullable = false)
    private String email;

    /** 이 인증 수단이 Member에 연동된 시각. */
    @Builder.Default
    private LocalDateTime linkedAt = LocalDateTime.now();

    /**
     * 연동 시각을 현재 시각으로 갱신합니다.
     */
    public void updateLinkedAt() {
        this.linkedAt = LocalDateTime.now();
    }
}


