package com.back.domain.user.auth.service;

import com.back.domain.user.auth.dto.request.AuthAccountCreationRequestDto;
import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.auth.repository.AuthAccountRepository;
import com.back.domain.user.member.type.MemberStatus;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * AuthAccount CRUD 및 비밀번호 검증 서비스.
 *
 * <p>AuthAccount의 생성·조회·삭제와 로컬 인증용 비밀번호 검증 로직을 담당합니다.
 * 트랜잭션은 기본 readOnly이며, 변경이 필요한 메서드는 별도로 {@code @Transactional}을 선언합니다.</p>
 */
@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthAccountService {

    private final AuthAccountRepository authAccountRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 특정 Member에서 지정 provider의 AuthAccount를 삭제합니다.
     *
     * @param memberId Member ID
     * @param provider 삭제할 OAuth provider 문자열 (예: "google")
     */
    @Transactional
    public void removeAuthAccountFromMember(Long memberId, String provider){
        authAccountRepository.deleteAuthAccountByMemberIdAndAuthProvider(
                memberId, AuthProvider.of(provider));
    }

    /**
     * 상태가 ACTIVE인 Member의 모든 AuthAccount를 반환합니다.
     *
     * @param memberId Member ID
     * @return ACTIVE 상태의 Member에 연결된 AuthAccount 목록
     */
    public List<AuthAccount> findAllAuthAccountsByMemberId(Long memberId) {
        return authAccountRepository.findAllByMemberIdAndMemberStatus(memberId, MemberStatus.ACTIVE);
    }

    /**
     * AuthAccount를 생성하고 저장합니다.
     *
     * <p>password 필드가 null이거나 blank이면 그대로 null로 저장합니다 (OAuth 계정).
     * 값이 있으면 BCrypt로 인코딩합니다 (로컬 계정).</p>
     *
     * @param dto 생성에 필요한 데이터
     * @return 저장된 AuthAccount 엔티티
     */
    @Transactional
    public AuthAccount createAuthAccount(AuthAccountCreationRequestDto dto) {
        String password = dto.password();

        if (password != null && !password.isBlank()) {
            password = passwordEncoder.encode(password);
        }

        AuthAccount account = AuthAccount.builder()
                .member(dto.member())
                .authProvider(dto.provider())
                .email(dto.email())
                .password(password)
                .issuer(dto.issuer())
                .subject(dto.subject())
                .build();

        return authAccountRepository.save(account);
    }

    /**
     * (provider, issuer, subject) 조합으로 AuthAccount를 조회합니다.
     *
     * @param provider OAuth provider
     * @param issuer   토큰 발급 기관
     * @param subject  provider의 사용자 고유 ID
     * @return 일치하는 AuthAccount, 없으면 {@code Optional.empty()}
     */
    public Optional<AuthAccount> findByAuthProviderAndIssuerAndSubject(
            AuthProvider provider,
            String issuer,
            String subject
    ) {
        return authAccountRepository
                .findByAuthProviderAndIssuerAndSubject(provider, issuer, subject);
    }

    /**
     * 입력 비밀번호와 저장된 BCrypt 해시를 비교합니다.
     *
     * @param inputPassword   사용자가 입력한 평문 비밀번호
     * @param encodedPassword DB에 저장된 BCrypt 해시
     * @throws ApiException 비밀번호가 일치하지 않으면 {@code ErrorCode.RESOURCE_NOT_FOUND}
     */
    public void validatePassword(String inputPassword, String encodedPassword) {
        if (!passwordEncoder.matches(inputPassword, encodedPassword))
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found.");
    }

}
