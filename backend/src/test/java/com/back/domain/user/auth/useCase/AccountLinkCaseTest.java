package com.back.domain.user.auth.useCase;

import com.back.domain.user.auth.dto.request.AuthAccountCreationRequestDto;
import com.back.domain.user.auth.dto.response.AccountLinkIssueResponseDto;
import com.back.domain.user.auth.entity.AccountLinkRedisHash;
import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.auth.service.AccountLinkService;
import com.back.domain.user.auth.service.AuthAccountService;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import com.back.domain.user.member.service.MemberService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.eq;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("AccountLinkCase 테스트")
class AccountLinkCaseTest {

    @Mock private MemberService memberService;
    @Mock private AccountLinkService accountLinkService;
    @Mock private AuthAccountService authAccountService;

    @InjectMocks private AccountLinkCase accountLinkCase;

    private Member testMember;
    private AuthAccount testGoogleAccount;

    @BeforeEach
    void setUp() {
        // 공통 테스트 데이터 초기화
        testMember = Member.builder()
                .displayName("testUser")
                .memberStatus(MemberStatus.ACTIVE)
                .essentialTermsAgreed(true)
                .role(MemberRole.GENERAL)
                .authAccounts(new ArrayList<>())
                .build();
        ReflectionTestUtils.setField(testMember, "id", 1L);

        testGoogleAccount = AuthAccount.builder()
                .member(testMember)
                .authProvider(AuthProvider.GOOGLE)
                .issuer("google")
                .subject("123456")
                .email("test@example.com")
                .build();
        ReflectionTestUtils.setField(testGoogleAccount, "id", 1L);

        testMember.getAuthAccounts().add(testGoogleAccount);
    }

    @Nested
    @DisplayName("startAccountLinking() 테스트")
    class StartAccountLinkingTests {

        @Test
        @DisplayName("성공 - linkCode 발급 및 redirectPath 반환")
        void success_issue_link_code_and_return_redirect_path() {
            // Given: GOOGLE만 연동된 Member, DISCORD 연동 시작
            String provider = "DISCORD";
            Long memberId = 1L;

            AccountLinkIssueResponseDto mockResponse = new AccountLinkIssueResponseDto(
                    "http://localhost:8080/oauth2/authorization/discord?linkCode=test-link-code"
            );

            given(memberService.findByIdWithAuthAccounts(memberId))
                    .willReturn(Optional.of(testMember));
            doNothing().when(memberService).memberValidation(any(Member.class));
            given(accountLinkService.issueLinkCode(memberId, "discord"))
                    .willReturn(mockResponse);

            // When
            var result = accountLinkCase.startAccountLinking(memberId, provider);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.redirectPath()).contains("discord");
            assertThat(result.redirectPath()).contains("linkCode=");

            verify(accountLinkService).issueLinkCode(memberId, "discord");
        }

        @Test
        @DisplayName("성공 - issueLinkCode 호출")
        void success_call_issue_link_code() {
            // Given: 기존 linkCode 존재
            String provider = "DISCORD";
            Long memberId = 1L;

            AccountLinkIssueResponseDto mockResponse = new AccountLinkIssueResponseDto(
                    "http://localhost:8080/oauth2/authorization/discord?linkCode=test-link-code"
            );

            given(memberService.findByIdWithAuthAccounts(memberId))
                    .willReturn(Optional.of(testMember));
            doNothing().when(memberService).memberValidation(any(Member.class));
            given(accountLinkService.issueLinkCode(memberId, "discord"))
                    .willReturn(mockResponse);

            // When
            accountLinkCase.startAccountLinking(memberId, provider);

            // Then
            verify(accountLinkService, times(1)).issueLinkCode(memberId, "discord");
        }

        @Test
        @DisplayName("성공 - provider 소문자 변환")
        void success_convert_provider_to_lowercase() {
            // Given: Provider 이름 대문자 입력
            String provider = "DISCORD";
            Long memberId = 1L;

            AccountLinkIssueResponseDto mockResponse = new AccountLinkIssueResponseDto(
                    "http://localhost:8080/oauth2/authorization/discord?linkCode=test-link-code"
            );

            given(memberService.findByIdWithAuthAccounts(memberId))
                    .willReturn(Optional.of(testMember));
            doNothing().when(memberService).memberValidation(any(Member.class));
            given(accountLinkService.issueLinkCode(memberId, "discord"))
                    .willReturn(mockResponse);

            // When
            accountLinkCase.startAccountLinking(memberId, provider);

            // Then: 소문자 "discord"로 호출됨
            verify(accountLinkService).issueLinkCode(memberId, "discord");
            verify(accountLinkService, never()).issueLinkCode(anyLong(), eq("DISCORD"));
        }

        @Test
        @DisplayName("실패 - 403 - 존재하지 않는 memberId로 예외 발생")
        void fail_403_member_not_found_throws_exception() {
            // Given: 존재하지 않는 memberId
            Long nonExistentMemberId = 999L;
            String provider = "DISCORD";

            given(memberService.findByIdWithAuthAccounts(nonExistentMemberId))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> accountLinkCase.startAccountLinking(nonExistentMemberId, provider))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

            verify(accountLinkService, never()).issueLinkCode(anyLong(), anyString());
        }

        @Test
        @DisplayName("실패 - 403 - 비활성 Member로 예외 발생")
        void fail_403_inactive_member_throws_exception() {
            // Given: termsAccepted=false
            Long memberId = 1L;
            String provider = "DISCORD";

            testMember.setEssentialTermsAgreed(false);

            given(memberService.findByIdWithAuthAccounts(memberId))
                    .willReturn(Optional.of(testMember));
            doThrow(new ApiException(ErrorCode.ACCESS_DENIED))
                    .when(memberService).memberValidation(any(Member.class));

            // When & Then
            assertThatThrownBy(() -> accountLinkCase.startAccountLinking(memberId, provider))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

            verify(accountLinkService, never()).issueLinkCode(anyLong(), anyString());
        }

        @Test
        @DisplayName("실패 - 403 - 이미 연동된 provider로 예외 발생")
        void fail_403_already_linked_provider_throws_exception() {
            // Given: GOOGLE 이미 연동된 상태에서 GOOGLE 재연동 시도
            Long memberId = 1L;
            String provider = "GOOGLE";

            given(memberService.findByIdWithAuthAccounts(memberId))
                    .willReturn(Optional.of(testMember));
            doNothing().when(memberService).memberValidation(any(Member.class));

            // When & Then
            assertThatThrownBy(() -> accountLinkCase.startAccountLinking(memberId, provider))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

            verify(accountLinkService, never()).issueLinkCode(anyLong(), anyString());
        }

        @Test
        @DisplayName("실패 - 400 - 유효하지 않은 provider로 예외 발생")
        void fail_400_invalid_provider_throws_exception() {
            // Given: "INVALID_PROVIDER" 전달
            Long memberId = 1L;
            String invalidProvider = "INVALID_PROVIDER";

            given(memberService.findByIdWithAuthAccounts(memberId))
                    .willReturn(Optional.of(testMember));
            doNothing().when(memberService).memberValidation(any(Member.class));

            // When & Then
            assertThatThrownBy(() -> accountLinkCase.startAccountLinking(memberId, invalidProvider))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);

            verify(accountLinkService, never()).issueLinkCode(anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("linkOauth2Account() 테스트")
    class LinkAccountTests {

        @Test
        @DisplayName("성공 - 새 OAuth 계정 연동")
        void success_link_new_oauth_account() {
            // Given: 새 OAuth 계정 정보
            Long memberId = 1L;
            AuthProvider provider = AuthProvider.DISCORD;
            String issuer = "discord";
            String subject = "discord-user-123";
            String email = "discord@example.com";

            AuthAccount newAccount = AuthAccount.builder()
                    .member(testMember)
                    .authProvider(provider)
                    .issuer(issuer)
                    .subject(subject)
                    .email(email)
                    .build();

            given(authAccountService.findByAuthProviderAndIssuerAndSubject(provider, issuer, subject))
                    .willReturn(Optional.empty());
            given(memberService.findMemberByValidation(memberId))
                    .willReturn(testMember);
            given(authAccountService.createAuthAccount(any(AuthAccountCreationRequestDto.class)))
                    .willReturn(newAccount);

            // When
            AuthAccount result = accountLinkCase.linkOauth2Account(memberId, provider, issuer, subject, email);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAuthProvider()).isEqualTo(provider);
            assertThat(result.getEmail()).isEqualTo(email);

            verify(authAccountService).createAuthAccount(any(AuthAccountCreationRequestDto.class));
        }

        @Test
        @DisplayName("성공 - 동일 email 다른 provider 연동 허용")
        void success_allow_same_email_different_provider() {
            // Given: GOOGLE(test@example.com) 이미 연동, DISCORD(test@example.com) 연동
            Long memberId = 1L;
            AuthProvider provider = AuthProvider.DISCORD;
            String issuer = "discord";
            String subject = "discord-user-123";
            String email = "test@example.com"; // GOOGLE과 동일 email

            AuthAccount newAccount = AuthAccount.builder()
                    .member(testMember)
                    .authProvider(provider)
                    .issuer(issuer)
                    .subject(subject)
                    .email(email)
                    .build();

            given(authAccountService.findByAuthProviderAndIssuerAndSubject(provider, issuer, subject))
                    .willReturn(Optional.empty());
            given(memberService.findMemberByValidation(memberId))
                    .willReturn(testMember);
            given(authAccountService.createAuthAccount(any(AuthAccountCreationRequestDto.class)))
                    .willReturn(newAccount);

            // When
            AuthAccount result = accountLinkCase.linkOauth2Account(memberId, provider, issuer, subject, email);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(email);
            verify(authAccountService).createAuthAccount(any(AuthAccountCreationRequestDto.class));
        }

        @Test
        @DisplayName("성공 - orphaned account에 member 할당")
        void success_link_orphaned_account_to_member() {
            // Given: member가 null인 AuthAccount (orphaned)
            Long memberId = 1L;
            AuthProvider provider = AuthProvider.DISCORD;
            String issuer = "discord";
            String subject = "discord-user-123";
            String email = "discord@example.com";

            AuthAccount orphanedAccount = AuthAccount.builder()
                    .member(null)
                    .authProvider(provider)
                    .issuer(issuer)
                    .subject(subject)
                    .email(email)
                    .build();
            ReflectionTestUtils.setField(orphanedAccount, "id", 10L);

            given(authAccountService.findByAuthProviderAndIssuerAndSubject(provider, issuer, subject))
                    .willReturn(Optional.of(orphanedAccount));
            given(memberService.findMemberByValidation(memberId))
                    .willReturn(testMember);

            // When
            AuthAccount result = accountLinkCase.linkOauth2Account(memberId, provider, issuer, subject, email);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMember()).isEqualTo(testMember);
            verify(memberService).findMemberByValidation(memberId);
            verify(authAccountService, never()).createAuthAccount(any());
        }

        @Test
        @DisplayName("실패 - 403 - 다른 Member에 연동된 계정으로 예외 발생")
        void fail_403_oauth_already_linked_to_another_member() {
            // Given: 동일 (provider, issuer, subject)가 다른 Member에게 연동됨
            Long memberId = 1L;
            AuthProvider provider = AuthProvider.DISCORD;
            String issuer = "discord";
            String subject = "discord-user-123";
            String email = "discord@example.com";

            Member anotherMember = Member.builder()
                    .displayName("anotherUser")
                    .memberStatus(MemberStatus.ACTIVE)
                    .essentialTermsAgreed(true)
                    .role(MemberRole.GENERAL)
                    .build();
            ReflectionTestUtils.setField(anotherMember, "id", 2L);

            AuthAccount existingAccount = AuthAccount.builder()
                    .member(anotherMember)
                    .authProvider(provider)
                    .issuer(issuer)
                    .subject(subject)
                    .email(email)
                    .build();

            given(authAccountService.findByAuthProviderAndIssuerAndSubject(provider, issuer, subject))
                    .willReturn(Optional.of(existingAccount));

            // When & Then
            assertThatThrownBy(() -> accountLinkCase.linkOauth2Account(memberId, provider, issuer, subject, email))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

            verify(authAccountService, never()).createAuthAccount(any());
        }

        @Test
        @DisplayName("실패 - 403 - 존재하지 않는 memberId로 예외 발생")
        void fail_403_member_not_found_in_link() {
            // Given: 존재하지 않는 memberId
            Long nonExistentMemberId = 999L;
            AuthProvider provider = AuthProvider.DISCORD;
            String issuer = "discord";
            String subject = "discord-user-123";
            String email = "discord@example.com";

            given(authAccountService.findByAuthProviderAndIssuerAndSubject(provider, issuer, subject))
                    .willReturn(Optional.empty());
            given(memberService.findMemberByValidation(nonExistentMemberId))
                    .willThrow(new ApiException(ErrorCode.ACCESS_DENIED));

            // When & Then
            assertThatThrownBy(() -> accountLinkCase.linkOauth2Account(nonExistentMemberId, provider, issuer, subject, email))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

            verify(authAccountService, never()).createAuthAccount(any());
        }
    }

    @Nested
    @DisplayName("getCurrentAccountLinkStatus() 테스트")
    class GetCurrentAccountLinkStatusTests {

        @Test
        @DisplayName("성공 - 모든 OAuth provider 상태 반환")
        void success_return_all_oauth_providers_status() {
            // Given: GOOGLE만 연동
            Long memberId = 1L;
            AuthProvider currentProvider = AuthProvider.GOOGLE;

            given(authAccountService.findAllAuthAccountsByMemberId(memberId))
                    .willReturn(List.of(testGoogleAccount));

            // When
            var result = accountLinkCase.getCurrentAccountLinkStatus(memberId, currentProvider);

            // Then
            assertThat(result).hasSize(2); // GOOGLE, DISCORD
            assertThat(result).extracting("provider")
                    .containsExactlyInAnyOrder("GOOGLE", "DISCORD");
        }

        @Test
        @DisplayName("성공 - 현재 provider 표시")
        void success_mark_current_provider() {
            // Given: currentProvider=GOOGLE
            Long memberId = 1L;
            AuthProvider currentProvider = AuthProvider.GOOGLE;

            given(authAccountService.findAllAuthAccountsByMemberId(memberId))
                    .willReturn(List.of(testGoogleAccount));

            // When
            var result = accountLinkCase.getCurrentAccountLinkStatus(memberId, currentProvider);

            // Then
            var googleStatus = result.stream()
                    .filter(dto -> dto.provider().equals("GOOGLE"))
                    .findFirst()
                    .orElseThrow();
            var discordStatus = result.stream()
                    .filter(dto -> dto.provider().equals("DISCORD"))
                    .findFirst()
                    .orElseThrow();

            assertThat(googleStatus.currentProvider()).isTrue();
            assertThat(discordStatus.currentProvider()).isFalse();
        }

        @Test
        @DisplayName("성공 - linkedAt 반환")
        void success_return_linked_at() {
            // Given: GOOGLE 연동, DISCORD 미연동
            Long memberId = 1L;
            AuthProvider currentProvider = AuthProvider.GOOGLE;

            given(authAccountService.findAllAuthAccountsByMemberId(memberId))
                    .willReturn(List.of(testGoogleAccount));

            // When
            var result = accountLinkCase.getCurrentAccountLinkStatus(memberId, currentProvider);

            // Then
            var googleStatus = result.stream()
                    .filter(dto -> dto.provider().equals("GOOGLE"))
                    .findFirst()
                    .orElseThrow();
            var discordStatus = result.stream()
                    .filter(dto -> dto.provider().equals("DISCORD"))
                    .findFirst()
                    .orElseThrow();

            assertThat(googleStatus.linkedAt()).isNotNull();
            assertThat(discordStatus.linkedAt()).isNull();
        }

        @Test
        @DisplayName("실패 - 401 - authAccounts 비어있으면 예외 발생")
        void fail_401_no_auth_accounts_throws_exception() {
            // Given: authAccounts 빈 리스트
            Long memberId = 1L;
            AuthProvider currentProvider = AuthProvider.GOOGLE;

            given(authAccountService.findAllAuthAccountsByMemberId(memberId))
                    .willReturn(List.of());

            // When & Then
            assertThatThrownBy(() -> accountLinkCase.getCurrentAccountLinkStatus(memberId, currentProvider))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_EXPIRED);
        }
    }

    @Nested
    @DisplayName("verifyLinkCode() 테스트")
    class VerifyLinkCodeTests {

        @Test
        @DisplayName("성공 - 유효한 linkCode로 Redis Hash 반환")
        void success_return_redis_hash_with_valid_link_code() {
            // Given: 유효한 linkCode
            String linkCode = "valid-link-code";
            AccountLinkRedisHash mockHash = AccountLinkRedisHash.builder()
                    .memberId(1L)
                    .requestedProvider("DISCORD")
                    .build();
            ReflectionTestUtils.setField(mockHash, "linkCode", linkCode);

            given(accountLinkService.verifyLinkCode(linkCode))
                    .willReturn(mockHash);

            // When
            AccountLinkRedisHash result = accountLinkCase.verifyLinkCode(linkCode);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getLinkCode()).isEqualTo(linkCode);
            assertThat(result.getMemberId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("실패 - 401 - 만료/존재하지 않는 linkCode로 예외 발생")
        void fail_401_expired_or_invalid_link_code_throws_exception() {
            // Given: 만료되거나 존재하지 않는 linkCode
            String expiredLinkCode = "expired-link-code";

            given(accountLinkService.verifyLinkCode(expiredLinkCode))
                    .willThrow(new ApiException(ErrorCode.SESSION_EXPIRED));

            // When & Then
            assertThatThrownBy(() -> accountLinkCase.verifyLinkCode(expiredLinkCode))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_EXPIRED);
        }
    }
}
