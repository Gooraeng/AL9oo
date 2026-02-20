package com.back.domain.user.member.repository;

import com.back.domain.user.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

//    @Query("""
//        SELECT DISTINCT m FROM Member m JOIN
//                m.authAccounts a WHERE a.email = :email
//    """)
//    Optional<Member> findByAnyAuthAccountEmail(String email);

    @Query("""
        SELECT CASE WHEN COUNT(m) > 0
        THEN true ELSE false END
        FROM Member m WHERE m.displayName = :displayName
    """)
    boolean existsMemberByDisplayName(String displayName);

    @Query("""
        SELECT m FROM Member m
        LEFT JOIN FETCH m.authAccounts
        WHERE m.id = :memberId
    """)
    Optional<Member> findByIdWithAuthAccounts(Long memberId);
}
