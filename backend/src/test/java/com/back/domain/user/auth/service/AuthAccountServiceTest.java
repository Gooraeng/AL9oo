package com.back.domain.user.auth.service;

import com.back.domain.user.auth.dto.request.AuthAccountCreationRequestDto;
import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.auth.repository.AuthAccountRepository;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("AuthAccountService 테스트")
class AuthAccountServiceTest {

    @Mock private AuthAccountRepository authAccountRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AuthAccountService authAccountService;

    private Member testMember;
    private AuthAccount testAccount;

    @BeforeEach
    void setUp() {
        testMember = Member.builder()
                .displayName("testUser")
                .memberStatus(MemberStatus.ACTIVE)
                .essentialTermsAgreed(true)
                .role(MemberRole.GENERAL)
                .authAccounts(new ArrayList<>())
                .build();
        ReflectionTestUtils.setField(testMember, "id", 1L);

        testAccount = AuthAccount.builder()
                .member(testMember)
                .authProvider(AuthProvider.GOOGLE)
                .issuer("google")
                .subject("123456")
                .email("test@example.com")
                .build();
        ReflectionTestUtils.setField(testAccount, "id", 1L);
    }

    @Nested
    @DisplayName("removeAuthAccountFromMember() 테스트")
    class RemoveAuthAccountFromMemberTests {

        @Test
        @DisplayName("성공 - 유효한 provider로 삭제 위임")
        void success_remove_with_valid_provider() {
            // Given
            Long memberId = 1L;
            String provider = "GOOGLE";

            // When
            authAccountService.removeAuthAccountFromMember(memberId, provider);

            // Then
            verify(authAccountRepository).deleteAuthAccountByMemberIdAndAuthProvider(1L, AuthProvider.GOOGLE);
        }

        @Test
        @DisplayName("실패 - 400 - 잘못된 provider로 예외 발생")
        void fail_400_invalid_provider_throws_exception() {
            // Given
            Long memberId = 1L;
            String invalidProvider = "INVALID";

            // When & Then
            assertThatThrownBy(() -> authAccountService.removeAuthAccountFromMember(memberId, invalidProvider))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);

            verify(authAccountRepository, never()).deleteAuthAccountByMemberIdAndAuthProvider(any(), any());
        }
    }

    @Nested
    @DisplayName("findAllAuthAccountsByMemberId() 테스트")
    class FindAllAuthAccountsByMemberIdTests {

        @Test
        @DisplayName("성공 - 목록 반환")
        void success_return_list() {
            // Given
            Long memberId = 1L;
            given(authAccountRepository.findAllByMemberIdAndMemberStatus(memberId, MemberStatus.ACTIVE))
                    .willReturn(List.of(testAccount));

            // When
            List<AuthAccount> result = authAccountService.findAllAuthAccountsByMemberId(memberId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        }

        @Test
        @DisplayName("성공 - 빈 목록 반환")
        void success_return_empty_list() {
            // Given
            Long memberId = 1L;
            given(authAccountRepository.findAllByMemberIdAndMemberStatus(memberId, MemberStatus.ACTIVE))
                    .willReturn(List.of());

            // When
            List<AuthAccount> result = authAccountService.findAllAuthAccountsByMemberId(memberId);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("createAuthAccount() 테스트")
    class CreateAuthAccountTests {

        @Test
        @DisplayName("성공 - 비밀번호 인코딩 후 저장")
        void success_encode_password_and_save() {
            // Given
            AuthAccountCreationRequestDto dto = AuthAccountCreationRequestDto.builder()
                    .member(testMember)
                    .password("rawPassword")
                    .provider(AuthProvider.LOCAL)
                    .issuer("local")
                    .subject("local-123")
                    .email("test@example.com")
                    .build();

            given(passwordEncoder.encode("rawPassword")).willReturn("encodedPassword");
            given(authAccountRepository.save(any(AuthAccount.class))).willAnswer(invocation -> invocation.getArgument(0));

            // When
            AuthAccount result = authAccountService.createAuthAccount(dto);

            // Then
            assertThat(result.getPassword()).isEqualTo("encodedPassword");
            verify(passwordEncoder).encode("rawPassword");
            verify(authAccountRepository).save(any(AuthAccount.class));
        }

        @Test
        @DisplayName("성공 - null 비밀번호는 인코딩 하지 않음")
        void success_null_password_not_encoded() {
            // Given
            AuthAccountCreationRequestDto dto = AuthAccountCreationRequestDto.builder()
                    .member(testMember)
                    .password(null)
                    .provider(AuthProvider.GOOGLE)
                    .issuer("google")
                    .subject("google-123")
                    .email("test@example.com")
                    .build();

            given(authAccountRepository.save(any(AuthAccount.class))).willAnswer(invocation -> invocation.getArgument(0));

            // When
            AuthAccount result = authAccountService.createAuthAccount(dto);

            // Then
            assertThat(result.getPassword()).isNull();
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("성공 - 공백 비밀번호는 인코딩 하지 않음")
        void success_blank_password_not_encoded() {
            // Given
            AuthAccountCreationRequestDto dto = AuthAccountCreationRequestDto.builder()
                    .member(testMember)
                    .password("   ")
                    .provider(AuthProvider.GOOGLE)
                    .issuer("google")
                    .subject("google-456")
                    .email("test@example.com")
                    .build();

            given(authAccountRepository.save(any(AuthAccount.class))).willAnswer(invocation -> invocation.getArgument(0));

            // When
            AuthAccount result = authAccountService.createAuthAccount(dto);

            // Then
            assertThat(result.getPassword()).isEqualTo("   ");
            verify(passwordEncoder, never()).encode(any());
        }
    }

    @Nested
    @DisplayName("findByAuthProviderAndIssuerAndSubject() 테스트")
    class FindByAuthProviderAndIssuerAndSubjectTests {

        @Test
        @DisplayName("성공 - 존재하는 계정 반환")
        void success_return_existing_account() {
            // Given
            given(authAccountRepository.findByAuthProviderAndIssuerAndSubject(
                    AuthProvider.GOOGLE, "google", "123456"))
                    .willReturn(Optional.of(testAccount));

            // When
            Optional<AuthAccount> result = authAccountService.findByAuthProviderAndIssuerAndSubject(
                    AuthProvider.GOOGLE, "google", "123456");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getSubject()).isEqualTo("123456");
        }

        @Test
        @DisplayName("성공 - 존재하지 않는 계정은 empty 반환")
        void success_return_empty_when_not_found() {
            // Given
            given(authAccountRepository.findByAuthProviderAndIssuerAndSubject(
                    AuthProvider.DISCORD, "discord", "nonexistent"))
                    .willReturn(Optional.empty());

            // When
            Optional<AuthAccount> result = authAccountService.findByAuthProviderAndIssuerAndSubject(
                    AuthProvider.DISCORD, "discord", "nonexistent");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("validatePassword() 테스트")
    class ValidatePasswordTests {

        @Test
        @DisplayName("성공 - 비밀번호 일치")
        void success_password_matches() {
            // Given
            given(passwordEncoder.matches("rawPassword", "encodedPassword")).willReturn(true);

            // When & Then
            assertThatCode(() -> authAccountService.validatePassword("rawPassword", "encodedPassword"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패 - 404 - 비밀번호 불일치")
        void fail_404_password_mismatch_throws_exception() {
            // Given
            given(passwordEncoder.matches("wrongPassword", "encodedPassword")).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> authAccountService.validatePassword("wrongPassword", "encodedPassword"))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND)
                    .hasMessage("Account not found.");
        }
    }
}
