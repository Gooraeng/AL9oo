package com.back.global.security.jwt.refreshToken.crud;

import com.back.global.security.constant.SecurityConstants;
import com.back.global.security.jwt.refreshToken.domain.RefreshTokenDomain;
import com.back.global.security.jwt.refreshToken.domain.RefreshTokenRepository;
import com.back.global.security.jwt.refreshToken.mapper.RefreshTokenCrudMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenCrudRepository crudRepository;
    private final RefreshTokenCrudMapper mapper;

    @Override
    public Optional<RefreshTokenDomain> findById(String id) {
        return crudRepository
                .findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsById(String id) {
        return crudRepository.existsById(id);
    }

    @Override
    public void deleteById(String id) {
        crudRepository.deleteById(id);
    }

    @Override
    public void deleteRefreshTokenContaining(List<RefreshTokenDomain> tokens) {
        for (RefreshTokenDomain token : tokens) {
            crudRepository.deleteById(token.getId());
        }
    }

    @Override
    public List<RefreshTokenDomain> findAllByMemberId(Long memberId) {
        return crudRepository
                .findAllByMemberId(memberId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public RefreshTokenDomain save(RefreshTokenDomain refreshTokenDomain) {
        RefreshTokenRedisHash entity = mapper.toEntity(refreshTokenDomain);
        entity.setTtl(SecurityConstants.REFRESH_TOKEN_TTL_SECONDS);

        RefreshTokenRedisHash saved = crudRepository.save(entity);

        return mapper.toDomain(saved);
    }

    @Override
    public void deleteAllByMemberId(Long memberId) {
        crudRepository.deleteAllByMemberId(memberId);
    }
}
