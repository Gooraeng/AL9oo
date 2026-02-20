package com.back.domain.user.auth;

import com.back.domain.user.auth.dto.response.AccountLinkStatusResponseDto;
import com.back.domain.user.auth.entity.AccountLinkRedisHash;
import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.repository.AccountLinkCrudRepository;
import com.back.domain.user.auth.repository.AuthAccountRepository;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.auth.useCase.AccountLinkCase;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.repository.MemberRepository;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.member.CustomUserDetails;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("AccountLink 통합 테스트")
@SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
@Sql(
        statements = {
                "SET REFERENTIAL_INTEGRITY FALSE",
                "TRUNCATE TABLE members",
                "TRUNCATE TABLE auth_accounts",
                "SET REFERENTIAL_INTEGRITY TRUE"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class AccountLinkIntegrationTest {

    @Autowired private AccountLinkCase accountLinkCase;
    @Autowired private AuthAccountRepository authAccountRepository;
    @Autowired private AccountLinkCrudRepository accountLinkCrudRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MockMvc mockMvc;

    private CustomUserDetails userDetails;
    private final String baseEndpoint = "/api/v1/auth/account-link";

    private final Long testId = 1L;

    @BeforeEach
    void setUp() {
        // Redis 데이터 정리 (테스트 간 격리)
        accountLinkCrudRepository.deleteAll();

        Member mockMember = Member.builder()
                .displayName("testMember1")
                .memberStatus(MemberStatus.ACTIVE)
                .essentialTermsAgreed(true)
                .role(MemberRole.GENERAL)
                .build();

        AuthAccount mockAuthaccount = AuthAccount.builder()
                .authProvider(AuthProvider.GOOGLE)
                .issuer("google")
                .email("test_email@gmail.com")
                .member(mockMember)
                .subject("1234567890")
                .build();

        memberRepository.save(mockMember);
        authAccountRepository.save(mockAuthaccount);

        memberRepository.flush();
        authAccountRepository.flush();

        this.userDetails = new CustomUserDetails(mockAuthaccount);

        // SecurityContext 설정
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("계정 연동 시작")
    class AccountLinkStart {

        private String getProviderAuthUri(String provider) {
            return baseEndpoint + "/start" + "?provider=" + provider;
        }

        @Test
        @DisplayName("성공 - 코드 발급 및 저장")
        void code_generation_and_save_success() throws Exception {
            ResultActions resultActions = mockMvc.perform(
                    post(getProviderAuthUri("discord"))
                            .with(user(userDetails))
                            .contentType(MediaType.APPLICATION_JSON));

            AccountLinkRedisHash acc = accountLinkCrudRepository.findByMemberId(testId)
                    .orElseThrow(Exception::new);

            resultActions
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.redirectPath").exists())
                    .andExpect(jsonPath("$.redirectPath").isString())
                    .andExpect(jsonPath("$.redirectPath").value(
                            "http://localhost:8080" +
                                    "/oauth2/authorization/discord?linkCode=" +
                                    acc.getLinkCode())
                    );
        }

        @Test
        @DisplayName("실패 - member가 active 하지 않은 상태")
        void failed_member_not_active() throws Exception {
            Member member = memberRepository.findById(testId).get();

            member.setEssentialTermsAgreed(false);
            memberRepository.saveAndFlush(member);

            ResultActions resultActions = mockMvc.perform(
                    post(getProviderAuthUri("discord"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .with(user(userDetails))
            );

            resultActions
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.redirectPath").doesNotExist())
                    .andExpect(jsonPath("$.detail").value("Not found: This member is not active."));
        }

        @Test
        @DisplayName("실패 - 연동된 auth account가 존재하지 않음")
        void failed_no_auth_account() throws Exception {
            authAccountRepository.deleteById(testId);
            authAccountRepository.flush();
            memberRepository.flush();

            ResultActions resultActions = mockMvc.perform(
                    post(getProviderAuthUri("discord"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .with(user(userDetails))
            );

            resultActions
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.redirectPath").doesNotExist())
                    .andExpect(jsonPath("$.detail").value("Access Denied: You must link at least one provider."));
        }

        @Test
        @DisplayName("실패 - 이미 연동된 provider의 연동 시도")
        void failed_duplicated_auth_provider() throws Exception {
            ResultActions resultActions = mockMvc.perform(
                    post(getProviderAuthUri("google"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .with(user(userDetails))
            );

            resultActions
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.redirectPath").doesNotExist())
                    .andExpect(jsonPath("$.detail").value("Access Denied: You are already linked to this account."));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 provider 요청 시 400")
        void fail_invalid_provider_returns_400() throws Exception {
            mockMvc.perform(
                    post(getProviderAuthUri("invalid_provider"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .with(user(userDetails))
            )
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("성공 - 연속 호출 시 새 코드 발급")
        void success_multiple_start_calls_issue_new_codes() throws Exception {
            // 첫 번째 호출
            String firstResponse = mockMvc.perform(
                    post(getProviderAuthUri("discord"))
                            .with(user(userDetails))
                            .contentType(MediaType.APPLICATION_JSON)
            )
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // 두 번째 호출
            String secondResponse = mockMvc.perform(
                    post(getProviderAuthUri("discord"))
                            .with(user(userDetails))
                            .contentType(MediaType.APPLICATION_JSON)
            )
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // 두 응답의 redirectPath가 다름 (각각 다른 linkCode 포함)
            assertThat(secondResponse).isNotEqualTo(firstResponse);
        }
    }

    @Nested
    @DisplayName("계정 - 연동 현황")
    class AccountLinkStatus {

        @Test
        @DisplayName("성공 - 계정 연동 현황 가져오기")
        void get_account_link_status_success() throws Exception {
            ResultActions resultActions = mockMvc.perform(
                    get(baseEndpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .with(user(userDetails))
                    )
                    .andDo(print());

            AuthProvider currentProvider = AuthProvider.GOOGLE;

            List<AccountLinkStatusResponseDto> responseDto = accountLinkCase
                    .getCurrentAccountLinkStatus(testId, currentProvider);

            for (int i = 0; i < responseDto.size(); i++) {
                AccountLinkStatusResponseDto rd = responseDto.get(i);

                resultActions
                        .andExpect(jsonPath("$[%d].provider".formatted(i)).exists())
                        .andExpect(jsonPath("$[%d].provider".formatted(i)).isString());

                if (currentProvider.equals(AuthProvider.of(rd.provider()))) {
                    resultActions
                            .andExpect(jsonPath("$[%d].linkedAt".formatted(i)).exists())
                            .andExpect(jsonPath("$[%d].currentProvider".formatted(i)).value(true));
                } else {
                    resultActions
                            .andExpect(jsonPath("$[%d].linkedAt".formatted(i)).doesNotExist())
                            .andExpect(jsonPath("$[%d].currentProvider".formatted(i)).value(false));
                }
            }
        }

        @Test
        @DisplayName("성공 - Google+Discord 연동 후 전체 상태 확인")
        void status_shows_multiple_linked_providers() throws Exception {
            // Discord 계정도 추가
            Member member = memberRepository.findById(testId).get();
            AuthAccount discordAccount = AuthAccount.builder()
                    .authProvider(AuthProvider.DISCORD)
                    .issuer("discord")
                    .email("test@discord.com")
                    .member(member)
                    .subject("discord-123")
                    .build();
            authAccountRepository.saveAndFlush(discordAccount);

            mockMvc.perform(
                    get(baseEndpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .with(user(userDetails))
            )
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].provider").exists())
                    .andExpect(jsonPath("$[0].linkedAt").exists())
                    .andExpect(jsonPath("$[1].provider").exists())
                    .andExpect(jsonPath("$[1].linkedAt").exists());
        }

        @Test
        @DisplayName("실패 - 미인증 요청 시 401")
        void fail_unauthenticated_returns_unauthorized() throws Exception {
            mockMvc.perform(
                    get(baseEndpoint)
                            .with(anonymous())
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
            )
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("계정 연동 해제")
    class AccountLinkUnlink {

        private String getUnlinkUri(String provider) {
            return baseEndpoint + "/unlink?provider=" + provider;
        }

        @Test
        @DisplayName("성공 - 2개 중 1개 provider 해제")
        void success_unlink_one_of_two_providers() throws Exception {
            // Discord 계정 추가 (Google + Discord)
            Member member = memberRepository.findById(testId).get();
            AuthAccount discordAccount = AuthAccount.builder()
                    .authProvider(AuthProvider.DISCORD)
                    .issuer("discord")
                    .email("test@discord.com")
                    .member(member)
                    .subject("discord-123")
                    .build();
            authAccountRepository.saveAndFlush(discordAccount);

            mockMvc.perform(
                    delete(getUnlinkUri("discord"))
                            .with(user(userDetails))
                            .contentType(MediaType.APPLICATION_JSON)
            )
                    .andDo(print())
                    .andExpect(status().isOk());

            // Discord 계정이 삭제되었는지 확인
            List<AuthAccount> remaining = authAccountRepository.findAllByMemberIdAndMemberStatus(
                    testId, MemberStatus.ACTIVE);
            assertThat(remaining).hasSize(1);
            assertThat(remaining.getFirst().getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        }

        @Test
        @DisplayName("실패 - 마지막 provider 해제 시도 시 403")
        void fail_unlink_last_provider_returns_403() throws Exception {
            mockMvc.perform(
                    delete(getUnlinkUri("google"))
                            .with(user(userDetails))
                            .contentType(MediaType.APPLICATION_JSON)
            )
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패 - 미인증 요청 시 401")
        void fail_unauthenticated_returns_unauthorized() throws Exception {
            mockMvc.perform(
                    delete(getUnlinkUri("google"))
                            .with(anonymous())
                            .contentType(MediaType.APPLICATION_JSON)
            )
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("LinkCode 검증 (AccountLinkCase 직접 호출)")
    class LinkCodeVerification {

        @Test
        @DisplayName("성공 - 유효한 linkCode 검증")
        void success_verify_valid_link_code() {
            // linkCode 발급
            accountLinkCase.startAccountLinking(testId, "discord");
            AccountLinkRedisHash stored = accountLinkCrudRepository.findByMemberId(testId)
                    .orElseThrow();

            // 검증
            AccountLinkRedisHash verified = accountLinkCase.verifyLinkCode(stored.getLinkCode());
            assertThat(verified).isNotNull();
            assertThat(verified.getMemberId()).isEqualTo(testId);
            assertThat(verified.getRequestedProvider()).isEqualTo("discord");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 linkCode")
        void fail_verify_nonexistent_link_code() {
            assertThatThrownBy(() -> accountLinkCase.verifyLinkCode("nonexistent-code"))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("성공 - linkCode 소비 후 verifyLinkCode 실패 확인")
        void success_consume_link_code_invalidates_verification() {
            // linkCode 발급
            accountLinkCase.startAccountLinking(testId, "discord");
            AccountLinkRedisHash stored = accountLinkCrudRepository.findByMemberId(testId)
                    .orElseThrow();
            String linkCode = stored.getLinkCode();

            // 발급된 linkCode가 유효한지 먼저 확인
            AccountLinkRedisHash verified = accountLinkCase.verifyLinkCode(linkCode);
            assertThat(verified).isNotNull();

            // 소비 - 직접 repository로 삭제
            accountLinkCrudRepository.deleteById(linkCode);

            // 소비 후 verifyLinkCode 시 예외 발생 확인
            assertThatThrownBy(() -> accountLinkCase.verifyLinkCode(linkCode))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Nested
    @DisplayName("OAuth2 계정 연동 (AccountLinkCase 직접 호출)")
    class LinkOauth2Account {

        @Test
        @DisplayName("성공 - 새 OAuth2 계정 연동")
        void success_link_new_oauth2_account() {
            AuthAccount result = accountLinkCase.linkOauth2Account(
                    testId,
                    AuthProvider.DISCORD,
                    "discord",
                    "discord-new-subject",
                    "discord@example.com"
            );

            assertThat(result).isNotNull();
            assertThat(result.getAuthProvider()).isEqualTo(AuthProvider.DISCORD);
            assertThat(result.getMember().getId()).isEqualTo(testId);
        }

        @Test
        @DisplayName("성공 - 이미 연동된 계정 재연동 시 기존 계정 반환")
        void success_already_linked_account_returns_existing() {
            // 같은 member에 이미 연동된 Discord 계정 생성
            Member member = memberRepository.findById(testId).get();
            AuthAccount existingDiscord = AuthAccount.builder()
                    .authProvider(AuthProvider.DISCORD)
                    .issuer("discord")
                    .subject("discord-existing-subject")
                    .email("existing@discord.com")
                    .member(member)
                    .build();
            authAccountRepository.saveAndFlush(existingDiscord);

            // 동일 계정으로 재연동 시도
            AuthAccount result = accountLinkCase.linkOauth2Account(
                    testId,
                    AuthProvider.DISCORD,
                    "discord",
                    "discord-existing-subject",
                    "existing@discord.com"
            );

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(existingDiscord.getId());
            assertThat(result.getMember().getId()).isEqualTo(testId);
        }

        @Test
        @DisplayName("실패 - 타인의 계정 연동 시도")
        void fail_account_owned_by_another_member() {
            // 다른 member 생성
            Member otherMember = Member.builder()
                    .displayName("otherMember")
                    .memberStatus(MemberStatus.ACTIVE)
                    .essentialTermsAgreed(true)
                    .role(MemberRole.GENERAL)
                    .build();
            memberRepository.saveAndFlush(otherMember);

            // 다른 member의 Discord 계정 생성
            AuthAccount otherAccount = AuthAccount.builder()
                    .authProvider(AuthProvider.DISCORD)
                    .issuer("discord")
                    .subject("other-discord-subject")
                    .email("other@discord.com")
                    .member(otherMember)
                    .build();
            authAccountRepository.saveAndFlush(otherAccount);

            // testId로 타인의 계정 연동 시도
            assertThatThrownBy(() -> accountLinkCase.linkOauth2Account(
                    testId,
                    AuthProvider.DISCORD,
                    "discord",
                    "other-discord-subject",
                    "other@discord.com"
            ))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }
    }
}