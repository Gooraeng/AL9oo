package com.back.domain.user.auth.repository;

import com.back.domain.user.auth.entity.AccountLinkRedisHash;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountLinkCrudRepository extends CrudRepository<AccountLinkRedisHash, String> {
    Optional<AccountLinkRedisHash> findByMemberId(Long memberId);

    void deleteAllByMemberId(Long memberId);
}
