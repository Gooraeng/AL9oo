package com.back.global.security.jwt.refreshToken.crud;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface RefreshTokenCrudRepository extends CrudRepository<RefreshTokenRedisHash, String> {
    void deleteAllByMemberId(Long memberId);

    List<RefreshTokenRedisHash> findAllByMemberId(Long memberId);
}
