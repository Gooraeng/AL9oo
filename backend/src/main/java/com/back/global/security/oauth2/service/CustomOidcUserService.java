package com.back.global.security.oauth2.service;

//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class CustomOidcUserService extends OidcUserService {
//
//    private final AuthService authService;
//
//    @Override
//    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
//        try {
//            // 기본 OIDC 사용자 정보 로드
//            OidcUser oidcUser = super.loadUser(userRequest);
//
//            String registrationId = userRequest.getClientRegistration().getRegistrationId();
//
//            // OIDC 속성 추출 (issuer, subject 포함)
//            String email = oidcUser.getEmail();
//            String name = oidcUser.getFullName();
//            String issuer = oidcUser.getIssuer().toString();
//            String subject = oidcUser.getSubject();
//
//            // 필수 필드 검증
//            validateOidcUser(email, issuer, subject);
//
//            AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());
//
//            // Member + AuthAccount 찾기 또는 생성
//            AuthAccount account = authService.findOrCreateOAuthAccount(
//                email,
//                name,
//                issuer,
//                subject,
//                provider
//            );
//
//            return CustomUserDetails.fromOidc(
//                account,
//                oidcUser.getAttributes(),
//                oidcUser.getIdToken(),
//                oidcUser.getUserInfo()
//            );
//
//        } catch (AccountLinkRequiredException ex) {
//            // Spring Security를 위해 OAuth2AuthenticationException으로 래핑
//            throw new OAuth2AuthenticationException(
//                    new OAuth2Error("ACCOUNT_LINK_REQUIRED", ex.getMessage(), null),
//                    ex
//            );
//        } catch (OAuth2AuthenticationException ex) {
//            throw ex;
//        } catch (Exception ex) {
//            log.error("Error during OIDC user loading", ex);
//            throw new OAuth2AuthenticationException(
//                    new OAuth2Error("SERVER_ERROR", "Failed to load OIDC user", null),
//                    ex
//            );
//        }
//    }
//
//    private void validateOidcUser(String email, String issuer, String subject) {
//        if (email == null || email.isBlank()) {
//            throw new OAuth2AuthenticationException(
//                    new OAuth2Error("EMAIL_NOT_FOUND", "Email not found from OIDC provider", null)
//            );
//        }
//        if (issuer == null || subject == null) {
//            throw new OAuth2AuthenticationException(
//                    new OAuth2Error("INVALID_OIDC_DATA", "Issuer or subject missing from OIDC response", null)
//            );
//        }
//    }
//}
