package com.back.domain.user.auth.useCase;

import com.back.domain.user.auth.dto.request.AuthAccountCreationRequestDto;
import com.back.domain.user.auth.dto.request.Oauth2SignUpRequestDto;
import com.back.domain.user.auth.dto.request.SignUpRequestDto;
import com.back.domain.user.auth.dto.response.NickNameCheckResultResponseDto;
import com.back.domain.user.auth.dto.response.SignUpResponseDto;
import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.service.AuthAccountService;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.service.MemberService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("Oauth2SignUpOrLoginCase 테스트")
class Oauth2SignUpOrLoginCaseTest {

    @Mock private AuthAccountService authAccountService;
    @Mock private MemberService memberService;

    @InjectMocks private Oauth2SignUpOrLoginCase oauth2SignUpOrLoginCase;

    private Member testMember;
    private AuthAccount testGoogleAccount;

    private final String testEmail = "test@example.com";
    private final String testIssuer = "google";
    private final String testSubject = "123456";

    @Nested
    @DisplayName("findOrCreateAuthAccount() 테스트")
    class FindOrCreateAuthAccountTests {

        @Test
        @DisplayName("성공 - Oauth2 계정 새 Member 생성")
        void success_create_new_member_for_oauth2_account() {
            AuthProvider provider = AuthProvider.GOOGLE;
            given(memberService.createMember(
                    provider.name() + "-" + testSubject,
                    MemberRole.GUEST,
                    MemberStatus.ACTIVE,
                    false)).willReturn(testMember);

            given(authAccountService.createAuthAccount(
                    AuthAccountCreationRequestDto.builder()
                            .member(testMember)
                            .provider(provider)
                            .issuer(testIssuer)
                            .subject(testSubject)
                            .email(testEmail)
                            .build()
            )).willReturn(testGoogleAccount);

            AuthAccount newAuthAccount = oauth2SignUpOrLoginCase.findOrCreateOAuth2Account(
                    testEmail,
                    testIssuer,
                    testSubject,
                    provider
            );

            assertThat(newAuthAccount).isNotNull();
            assertThat(newAuthAccount.getEmail()).isEqualTo(testEmail);
            assertThat(newAuthAccount.getAuthProvider()).isEqualTo(provider);
            assertThat(newAuthAccount.getIssuer()).isEqualTo(testIssuer);
            assertThat(newAuthAccount.getSubject()).isEqualTo(testSubject);
            assertThat(newAuthAccount.getMember()).isNotNull();
            assertThat(newAuthAccount.getMember()).isEqualTo(testMember);

            verify(memberService).createMember(
                    provider.name() + "-" + testSubject,
                    MemberRole.GUEST,
                    MemberStatus.ACTIVE,
                    false);
            verify(authAccountService).createAuthAccount(
                    AuthAccountCreationRequestDto.builder()
                            .member(testMember)
                            .provider(provider)
                            .issuer(testIssuer)
                            .subject(testSubject)
                            .email(testEmail)
                            .build()
            );
        }

        @Test
        @DisplayName("성공 - 기존 Oauth2 계정 반환")
        void success_return_existing_oauth2_account() {

            given(authAccountService.findByAuthProviderAndIssuerAndSubject(
                    AuthProvider.GOOGLE, testIssuer, testSubject))
                    .willReturn(Optional.of(testGoogleAccount));

            AuthAccount existingAuthAccount = oauth2SignUpOrLoginCase.findOrCreateOAuth2Account(
                    testEmail,
                    testIssuer,
                    testSubject,
                    AuthProvider.GOOGLE
            );

            assertThat(existingAuthAccount).isNotNull();
            assertThat(existingAuthAccount.getEmail()).isEqualTo(testEmail);
            assertThat(existingAuthAccount.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
            assertThat(existingAuthAccount.getIssuer()).isEqualTo(testIssuer);
            assertThat(existingAuthAccount.getSubject()).isEqualTo(testSubject);
            assertThat(existingAuthAccount.getMember()).isNotNull();
            assertThat(existingAuthAccount.getMember()).isEqualTo(testMember);
        }

        @Test
        @DisplayName("실패 - 422 - Local은 Oauth2 계정으로 생성 불가")
        void fail_422_local_cannot_be_created_as_oauth2_account() {
            AuthProvider provider = AuthProvider.LOCAL;

            ApiException exception = assertThrows(
                    ApiException.class, () -> oauth2SignUpOrLoginCase.findOrCreateOAuth2Account(
                            testEmail, testIssuer, testSubject, provider));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_WAY);
            verifyNoInteractions(authAccountService, memberService);
        }
    }

    @Nested
    @DisplayName("doSignUp() 테스트")
    class DoSignUpTests {

        private final String testDisplayName = "CompleteMember";

        @Test
        @DisplayName("성공 - Guest 였던 멤버가 가입 완료")
        void success_guest_member_is_signed_up() {
            Long memberId = testMember.getId();

            Oauth2SignUpRequestDto originRequest =
                    new Oauth2SignUpRequestDto(testDisplayName, true);
            NickNameCheckResultResponseDto nickNameDupDto =
                    new NickNameCheckResultResponseDto(true);

            Member completedMember = Member.builder()
                    .displayName(testDisplayName)
                    .memberStatus(MemberStatus.ACTIVE)
                    .essentialTermsAgreed(true)
                    .role(MemberRole.GENERAL)
                    .authAccounts(new ArrayList<>())
                    .build();
            ReflectionTestUtils.setField(completedMember, "id", memberId);

            given(memberService.checkDisplayNameDuplication(testDisplayName)).willReturn(nickNameDupDto);
            given(memberService.findMemberById(memberId)).willReturn(testMember);
            given(memberService.saveMember(testMember)).willReturn(completedMember);

            SignUpResponseDto responseDto = oauth2SignUpOrLoginCase
                    .doSignUp(SignUpRequestDto.ofOauth2(originRequest, memberId));

            assertThat(responseDto).isNotNull();
            assertThat(responseDto.message()).isEqualTo("Welcome, " + testDisplayName + "!");

            verify(memberService).checkDisplayNameDuplication(testDisplayName);
            verify(memberService).findMemberById(memberId);
            verify(memberService).saveMember(testMember);
        }

        @Test
        @DisplayName("실패 - 약관 동의를 하지 않음")
        void fail_terms_not_accepted() {
            Long memberId = testMember.getId();

            Oauth2SignUpRequestDto originRequest =
                    new Oauth2SignUpRequestDto(testDisplayName, false);

            ApiException exception = assertThrows(
                    ApiException.class,
                    () -> oauth2SignUpOrLoginCase.doSignUp(
                            SignUpRequestDto.ofOauth2(originRequest, memberId))
            );

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TERMS_NOT_ACCEPTED);
            verifyNoInteractions(memberService);
        }

        @Test
        @DisplayName("실패 - 가입 당시 중복 닉네임이 있음")
        void fail_nickname_already_used() {
            Long memberId = testMember.getId();

            Oauth2SignUpRequestDto originRequest =
                    new Oauth2SignUpRequestDto(testDisplayName, true);

            given(memberService.checkDisplayNameDuplication(testDisplayName))
                    .willReturn(new NickNameCheckResultResponseDto(false));

            ApiException exception = assertThrows(
                    ApiException.class,
                    () -> oauth2SignUpOrLoginCase.doSignUp(
                            SignUpRequestDto.ofOauth2(originRequest, memberId)));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
            verify(memberService).checkDisplayNameDuplication(testDisplayName);
        }

        @Test
        @DisplayName("실패 - GUEST가 아닌 멤버가 가입 시도")
        void fail_non_guest_member_signup() {
            testMember.setRole(MemberRole.GENERAL);

            Oauth2SignUpRequestDto originRequest =
                    new Oauth2SignUpRequestDto(testDisplayName, true);

            given(memberService.checkDisplayNameDuplication(testDisplayName))
                    .willReturn(new NickNameCheckResultResponseDto(true));

            given(memberService.findMemberById(1L))
                    .willReturn(testMember);

            ApiException exception = assertThrows(
                    ApiException.class,
                    () -> oauth2SignUpOrLoginCase.doSignUp(
                            SignUpRequestDto.ofOauth2(originRequest, testMember.getId())));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
            assertThat(exception.getMessage()).isEqualTo("You have already completed the sign-up process.");
        }
    }

    @BeforeEach
    void setUp() {
        // 공통 테스트 데이터 초기화
        testMember = Member.builder()
                .displayName("GOOGLE-123456")
                .memberStatus(MemberStatus.ACTIVE)
                .essentialTermsAgreed(false)
                .role(MemberRole.GUEST)
                .authAccounts(new ArrayList<>())
                .build();
        ReflectionTestUtils.setField(testMember, "id", 1L);

        testGoogleAccount = AuthAccount.builder()
                .member(testMember)
                .authProvider(AuthProvider.GOOGLE)
                .issuer(testIssuer)
                .subject(testSubject)
                .email(testEmail)
                .build();
        ReflectionTestUtils.setField(testGoogleAccount, "id", 1L);

        testMember.getAuthAccounts().add(testGoogleAccount);
    }
}