package com.back.domain.user.auth;

import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.repository.AuthAccountRepository;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.auth.useCase.Oauth2SignUpOrLoginCase;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.repository.MemberRepository;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import com.back.global.security.member.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("OAuth2 가입/로그인 통합 테스트")
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
class Oauth2SignUpOrLoginIntegrationTest {

    @Autowired private Oauth2SignUpOrLoginCase oauth2SignUpOrLoginCase;
    @Autowired private AuthAccountRepository authAccountRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MockMvc mockMvc;

    private Member guestMember;
    private AuthAccount guestAuthAccount;
    private Member generalMember;
    private CustomUserDetails guestUserDetails;
    private CustomUserDetails generalUserDetails;

    private final String signupEndpoint = "/api/v1/auth/oauth2/signup";
    private final String checkNameEndpoint = "/api/v1/auth/check-display-name";

    @BeforeEach
    void setUp() {
        // GUEST Member (Google, subject="guest-subject-001")
        guestMember = Member.builder()
                .displayName("GOOGLE-guest-subject-001")
                .memberStatus(MemberStatus.ACTIVE)
                .essentialTermsAgreed(false)
                .role(MemberRole.GUEST)
                .build();
        memberRepository.save(guestMember);

        guestAuthAccount = AuthAccount.builder()
                .authProvider(AuthProvider.GOOGLE)
                .issuer("google")
                .email("guest@example.com")
                .member(guestMember)
                .subject("guest-subject-001")
                .build();
        authAccountRepository.save(guestAuthAccount);

        // GENERAL Member (Discord)
        generalMember = Member.builder()
                .displayName("existingGeneralUser")
                .memberStatus(MemberStatus.ACTIVE)
                .essentialTermsAgreed(true)
                .role(MemberRole.GENERAL)
                .build();
        memberRepository.save(generalMember);

        AuthAccount generalAuthAccount = AuthAccount.builder()
                .authProvider(AuthProvider.DISCORD)
                .issuer("discord")
                .email("general@example.com")
                .member(generalMember)
                .subject("general-subject-001")
                .build();
        authAccountRepository.save(generalAuthAccount);

        memberRepository.flush();
        authAccountRepository.flush();

        guestUserDetails = new CustomUserDetails(guestAuthAccount);
        generalUserDetails = new CustomUserDetails(generalAuthAccount);
    }

    @Nested
    @DisplayName("findOrCreateOAuth2Account (UseCase 직접 호출)")
    class FindOrCreateOAuth2Account {

        @Test
        @DisplayName("신규 유저 - Member(GUEST) + AuthAccount DB에 생성")
        void success_create_new_guest_member_and_auth_account() {
            long beforeMemberCount = memberRepository.count();
            long beforeAccountCount = authAccountRepository.count();

            AuthAccount result = oauth2SignUpOrLoginCase.findOrCreateOAuth2Account(
                    "newuser@example.com",
                    "google",
                    "new-subject-999",
                    AuthProvider.GOOGLE
            );

            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getMember()).isNotNull();
            assertThat(result.getMember().getRole()).isEqualTo(MemberRole.GUEST);
            assertThat(memberRepository.count()).isEqualTo(beforeMemberCount + 1);
            assertThat(authAccountRepository.count()).isEqualTo(beforeAccountCount + 1);
        }

        @Test
        @DisplayName("신규 GUEST displayName 형식: GOOGLE-{subject}")
        void new_guest_display_name_format_google() {
            String subject = "format-test-subject";

            AuthAccount result = oauth2SignUpOrLoginCase.findOrCreateOAuth2Account(
                    "format@example.com",
                    "google",
                    subject,
                    AuthProvider.GOOGLE
            );

            assertThat(result.getMember().getDisplayName())
                    .isEqualTo("GOOGLE-" + subject);
        }

        @Test
        @DisplayName("기존 유저 - 동일 (provider, issuer, subject) → 기존 AuthAccount 반환, count 유지")
        void success_return_existing_auth_account() {
            long beforeMemberCount = memberRepository.count();
            long beforeAccountCount = authAccountRepository.count();

            AuthAccount result = oauth2SignUpOrLoginCase.findOrCreateOAuth2Account(
                    "guest@example.com",
                    "google",
                    "guest-subject-001",
                    AuthProvider.GOOGLE
            );

            assertThat(result.getId()).isEqualTo(guestAuthAccount.getId());
            assertThat(memberRepository.count()).isEqualTo(beforeMemberCount);
            assertThat(authAccountRepository.count()).isEqualTo(beforeAccountCount);
        }

        @Test
        @DisplayName("Discord provider 신규 생성 - DISCORD-{subject} 형식")
        void new_guest_display_name_format_discord() {
            String subject = "discord-new-subject";

            AuthAccount result = oauth2SignUpOrLoginCase.findOrCreateOAuth2Account(
                    "discord-new@example.com",
                    "discord",
                    subject,
                    AuthProvider.DISCORD
            );

            assertThat(result.getMember().getDisplayName())
                    .isEqualTo("DISCORD-" + subject);
            assertThat(result.getAuthProvider()).isEqualTo(AuthProvider.DISCORD);
        }
    }

    @Nested
    @DisplayName("OAuthSignupEndpoint (POST /api/v1/auth/oauth2/signup)")
    class OAuthSignupEndpoint {

        @Test
        @DisplayName("성공 - GUEST + 고유 닉네임 + terms=true → 200, DB role=GENERAL")
        void success_guest_unique_nickname_terms_true() throws Exception {
            String uniqueName = "uniqueNickname99";

            mockMvc.perform(post(signupEndpoint)
                            .with(user(guestUserDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"displayName": "%s", "essentialTermsAgreed": true}
                                    """.formatted(uniqueName)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Welcome, " + uniqueName + "!"));

            Member updated = memberRepository.findById(guestMember.getId()).orElseThrow();
            assertThat(updated.getRole()).isEqualTo(MemberRole.GENERAL);
            assertThat(updated.getDisplayName()).isEqualTo(uniqueName);
        }

        @Test
        @DisplayName("실패 - GUEST + terms=false → 400 (TERMS_NOT_ACCEPTED)")
        void fail_terms_not_accepted_returns_400() throws Exception {
            mockMvc.perform(post(signupEndpoint)
                            .with(user(guestUserDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"displayName": "someNickname", "essentialTermsAgreed": false}
                                    """))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("TERMS_NOT_ACCEPTED"));
        }

        @Test
        @DisplayName("실패 - GUEST + 중복 닉네임 → 409 (CONFLICT)")
        void fail_duplicate_nickname_returns_409() throws Exception {
            // existingGeneralUser는 setUp()에서 이미 DB에 있음
            mockMvc.perform(post(signupEndpoint)
                            .with(user(guestUserDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"displayName": "existingGeneralUser", "essentialTermsAgreed": true}
                                    """))
                    .andDo(print())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("CONFLICT"));
        }

        @Test
        @DisplayName("실패 - GENERAL 유저 재가입 → 403 (@PreAuthorize)")
        void fail_general_user_signup_returns_403() throws Exception {
            mockMvc.perform(post(signupEndpoint)
                            .with(user(generalUserDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"displayName": "anotherName", "essentialTermsAgreed": true}
                                    """))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패 - Anonymous → 401")
        void fail_anonymous_returns_401() throws Exception {
            mockMvc.perform(post(signupEndpoint)
                            .with(anonymous())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"displayName": "anonName", "essentialTermsAgreed": true}
                                    """))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("NicknameCheckEndpoint (GET /api/v1/auth/check-display-name/{name})")
    class NicknameCheckEndpoint {

        @Test
        @DisplayName("DB에 없는 닉네임 → available=true")
        void not_in_db_returns_available_true() throws Exception {
            mockMvc.perform(get(checkNameEndpoint + "/totallyUniqueName123")
                            .with(user(guestUserDetails)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(true));
        }

        @Test
        @DisplayName("DB에 있는 닉네임 → available=false")
        void in_db_returns_available_false() throws Exception {
            // existingGeneralUser는 setUp()에서 DB에 저장됨
            mockMvc.perform(get(checkNameEndpoint + "/existingGeneralUser")
                            .with(user(guestUserDetails)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(false));
        }

        @Test
        @DisplayName("미인증(anonymous) → 200 (permitAll)")
        void anonymous_can_access_check_endpoint() throws Exception {
            mockMvc.perform(get(checkNameEndpoint + "/someNameToCheck")
                            .with(anonymous()))
                    .andDo(print())
                    .andExpect(status().isOk());
        }
    }
}
