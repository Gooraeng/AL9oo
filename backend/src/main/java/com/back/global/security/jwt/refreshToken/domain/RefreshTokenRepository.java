package com.back.global.security.jwt.refreshToken.domain;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository {

    // based id
    Optional<RefreshTokenDomain> findById(String id);

    boolean existsById(String id);

    void deleteById(String id);

    void deleteRefreshTokenContaining(List<RefreshTokenDomain> tokens);

    // member id
    List<RefreshTokenDomain> findAllByMemberId(Long memberId);

    void deleteAllByMemberId(Long memberId);

    // save
    RefreshTokenDomain save(RefreshTokenDomain refreshTokenDomain);
}
