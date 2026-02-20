package com.back.global.security.local.service;

import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.auth.repository.AuthAccountRepository;
import com.back.global.security.member.CustomUserDetails;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalUserDetailsService implements UserDetailsService {

    private final AuthAccountRepository accountRepository;

    @Override
    public @Nonnull UserDetails loadUserByUsername(@Nonnull String email) throws UsernameNotFoundException {
        AuthAccount account = accountRepository.findByEmailAndAuthProviderWithMember(email, AuthProvider.LOCAL)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

         return new CustomUserDetails(account);
    }


}
