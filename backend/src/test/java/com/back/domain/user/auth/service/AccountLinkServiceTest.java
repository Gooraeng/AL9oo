package com.back.domain.user.auth.service;

import com.back.domain.user.auth.dto.response.AccountLinkIssueResponseDto;
import com.back.domain.user.auth.entity.AccountLinkRedisHash;
import com.back.domain.user.auth.repository.AccountLinkCrudRepository;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.property.UrlProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("AccountLinkService 테스트")
class AccountLinkServiceTest {

    @Mock private AccountLinkCrudRepository accountLinkCrudRepository;
    @Mock private UrlProperty urlProperty;

    @InjectMocks private AccountLinkService accountLinkService;

    private AccountLinkRedisHash testLinkHash;

    @BeforeEach
    void setUp() {
        testLinkHash = AccountLinkRedisHash.builder()
                .memberId(1L)
                .requestedProvider("discord")
                .build();
        ReflectionTestUtils.setField(testLinkHash, "linkCode", "test-link-code-uuid");
    }

    @Nested
    @DisplayName("removeAllLinkCodeByMemberId() 테스트")
    class RemoveAllLinkCodeByMemberIdTests {

        @Test
        @DisplayName("성공 - 삭제 위임")
        void success_delegate_delete() {
            // Given
            Long memberId = 1L;

            // When
            accountLinkService.removeAllLinkCodeByMemberId(memberId);

            // Then
            verify(accountLinkCrudRepository).deleteAllByMemberId(memberId);
        }
    }

    @Nested
    @DisplayName("issueLinkCode() 테스트")
    class IssueLinkCodeTests {

        @Test
        @DisplayName("성공 - redirectPath 반환")
        void success_return_redirect_path() {
            // Given
            Long memberId = 1L;
            String requestedProvider = "discord";

            given(urlProperty.getBackUrl()).willReturn("http://localhost:8080");
            given(accountLinkCrudRepository.save(any(AccountLinkRedisHash.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When
            AccountLinkIssueResponseDto result = accountLinkService.issueLinkCode(memberId, requestedProvider);

            // Then
            assertThat(result.redirectPath()).startsWith("http://localhost:8080/oauth2/authorization/discord?linkCode=");
        }

        @Test
        @DisplayName("성공 - 올바른 데이터로 save 호출")
        void success_save_with_correct_data() {
            // Given
            Long memberId = 1L;
            String requestedProvider = "discord";

            given(urlProperty.getBackUrl()).willReturn("http://localhost:8080");
            given(accountLinkCrudRepository.save(any(AccountLinkRedisHash.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When
            accountLinkService.issueLinkCode(memberId, requestedProvider);

            // Then
            ArgumentCaptor<AccountLinkRedisHash> captor = ArgumentCaptor.forClass(AccountLinkRedisHash.class);
            verify(accountLinkCrudRepository).save(captor.capture());

            AccountLinkRedisHash saved = captor.getValue();
            assertThat(saved.getMemberId()).isEqualTo(memberId);
            assertThat(saved.getRequestedProvider()).isEqualTo(requestedProvider);
            assertThat(saved.getLinkCode()).isNotNull();
        }
    }

    @Nested
    @DisplayName("verifyLinkCode() 테스트")
    class VerifyLinkCodeTests {

        @Test
        @DisplayName("성공 - 유효한 linkCode로 hash 반환")
        void success_return_hash_with_valid_link_code() {
            // Given
            String linkCode = "test-link-code-uuid";
            given(accountLinkCrudRepository.findById(linkCode))
                    .willReturn(Optional.of(testLinkHash));

            // When
            AccountLinkRedisHash result = accountLinkService.verifyLinkCode(linkCode);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getLinkCode()).isEqualTo(linkCode);
            assertThat(result.getMemberId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("실패 - 401 - 만료된 linkCode로 예외 발생")
        void fail_401_expired_link_code_throws_exception() {
            // Given
            String expiredLinkCode = "expired-link-code";
            given(accountLinkCrudRepository.findById(expiredLinkCode))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> accountLinkService.verifyLinkCode(expiredLinkCode))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_EXPIRED)
                    .hasMessage("Link session expired.");
        }
    }
}
