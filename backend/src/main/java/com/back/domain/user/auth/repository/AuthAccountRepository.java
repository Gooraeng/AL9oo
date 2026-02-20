package com.back.domain.user.auth.repository;

import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.member.type.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {
    Optional<AuthAccount> findByEmail(String email);

    @Query("""
        SELECT a FROM AuthAccount a JOIN FETCH a.member m
        WHERE a.email != null and a.email = :email
            AND a.authProvider = :provider
    """)
    Optional<AuthAccount> findByEmailAndAuthProviderWithMember(String email, AuthProvider provider);

    @Query("""
        SELECT a FROM AuthAccount a JOIN FETCH a.member m
        WHERE a.authProvider = :provider
            AND a.issuer = :issuer
            AND a.subject = :subject
    """)
    Optional<AuthAccount> findByAuthProviderAndIssuerAndSubject(
        @Param("provider") AuthProvider provider,
        @Param("issuer") String issuer,
        @Param("subject") String subject
    );

    /**
     * 약관 동의인 상태의 Member를 기준으로 사용 중인 auth account를 받습니다.
     * isDelete flag도 사용 가능하나 business logic 상 거기까지 검증할 필요 X
     *
     * @param memberId Member ID
     * @return List of AuthAccount that can be empty
     */
    @Query("""
        SELECT a from AuthAccount a JOIN FETCH a.member m
        WHERE m.id = :memberId
            AND m.memberStatus = :status
    """)
    List<AuthAccount> findAllByMemberIdAndMemberStatus(
            Long memberId,
            @Param("status") MemberStatus status
    );

    @Modifying
    @Query("""
        DELETE FROM AuthAccount a
            WHERE a.member.id = :memberId
            AND a.authProvider = :authProvider
    """)
    void deleteAuthAccountByMemberIdAndAuthProvider(Long memberId, AuthProvider authProvider);
}
