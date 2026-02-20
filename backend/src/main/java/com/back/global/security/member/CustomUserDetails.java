package com.back.global.security.member;

import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Getter
public class CustomUserDetails implements OAuth2User, UserDetails {

    private final Long memberId;
    private final String email;
    private final String displayName;
    private final String issuerValue;  // issuer 문자열 (getIssuer()와 충돌 방지)
    private final String subjectValue; // subject 문자열
    private final MemberRole role;
    private final AuthProvider provider;
    private final MemberStatus memberStatus;
    private final Boolean essentialTermsAgreed;
    private final Map<String, Object> attributes;

    public CustomUserDetails(AuthAccount account) {
        this.memberId = account.getMember().getId();
        this.email = account.getEmail();
        this.displayName = account.getMember().getDisplayName();
        this.issuerValue = account.getIssuer();
        this.subjectValue = account.getSubject();
        this.role = account.getMember().getRole();
        this.provider = account.getAuthProvider();
        this.memberStatus = account.getMember().getMemberStatus();
        this.essentialTermsAgreed = account.getMember().isEssentialTermsAgreed();
        this.attributes = null;
    }

    // Public 정적 팩토리 메서드 - OAuth2 로그인용 (Discord 등)
    public static CustomUserDetails fromOAuth(
            AuthAccount account, Map<String, Object> attributes
    ) {
        Member member = account.getMember();

        return CustomUserDetails.builder()
                .memberId(member.getId())
                .email(account.getEmail())
                .displayName(member.getDisplayName())
                .issuerValue(account.getIssuer())
                .subjectValue(account.getSubject())
                .role(member.getRole())
                .provider(account.getAuthProvider())
                .memberStatus(member.getMemberStatus())
                .essentialTermsAgreed(member.isEssentialTermsAgreed())
                .attributes(attributes)
                .build();
    }

    public static CustomUserDetails fromJwt(Long memberId, MemberRole role) {
        return CustomUserDetails.builder()
                .memberId(memberId)
                .email(null)
                .displayName(null)
                .issuerValue(null)
                .subjectValue(null)
                .role(role)
                .provider(null)
                .memberStatus(null)
                .essentialTermsAgreed(true)
                .build();
    }

    // Public 정적 팩토리 메서드 - OIDC 로그인용
    // Todo: OIDC 관련 Implement 시 사용
    /*
    public static CustomUserDetails fromOidc(
            AuthAccount account,
            Map<String, Object> attributes,
            OidcIdToken idToken,
            OidcUserInfo userInfo
    ) {
        Member member = account.getMember();

        return CustomUserDetails.builder()
                .memberId(member.getId())
                .email(account.getEmail())
                .displayName(member.getDisplayName())
                .issuerValue(account.getIssuer())
                .subjectValue(account.getSubject())
                .role(member.getRole())
                .provider(account.getAuthProvider())
                .attributes(attributes)
                .build();
    }
    */

    public boolean isRegistered() {
        return !memberStatus.equals(MemberStatus.PENDING) && essentialTermsAgreed;
    }

    public boolean isActuallyActive() {
        return memberStatus.equals(MemberStatus.ACTIVE) && essentialTermsAgreed;
    }

    @Override
    public String getUsername() {
        return displayName;  // displayName을 username으로 사용
    }

    @Override
    public String getName() {
        return displayName;  // displayName 반환
    }

    @Override
    public @Nullable String getPassword() {
        return null;  // 현재 OAuth만 사용하므로 password 없음
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    // private constructor with Builder
    @Builder
    private CustomUserDetails(
            long memberId,
            String email,
            String displayName,
            String issuerValue,
            String subjectValue,
            MemberRole role,
            AuthProvider provider,
            MemberStatus memberStatus,
            Boolean essentialTermsAgreed,
            Map<String, Object> attributes
    ) {
        this.memberId = memberId;
        this.email = email;
        this.displayName = displayName;
        this.issuerValue = issuerValue;
        this.subjectValue = subjectValue;
        this.role = role;
        this.provider = provider;
        this.memberStatus = memberStatus;
        this.essentialTermsAgreed = essentialTermsAgreed;
        this.attributes = attributes;
    }
}
