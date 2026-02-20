package com.back.global.security;

import com.back.domain.user.member.type.MemberRole;
import com.back.global.property.UrlProperty;
import com.back.global.security.jwt.JwtAuthenticationFilter;
import com.back.global.security.jwt.JwtCookieHelper;
import com.back.global.security.jwt.service.JwtTokenProvider;
import com.back.global.security.oauth2.handler.Oauth2LoginFailureHandler;
import com.back.global.security.oauth2.handler.Oauth2LoginSuccessHandler;
import com.back.global.security.oauth2.repository.RedisOAuth2AuthorizationRequestRepository;
import com.back.global.security.oauth2.resolver.CustomOAuth2AuthorizationRequestResolver;
import com.back.global.security.oauth2.service.StrategyOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // Oauth2
    private final StrategyOAuth2UserService strategyOAuth2UserService;
    private final CustomOAuth2AuthorizationRequestResolver customOAuth2AuthorizationRequestResolver;
    private final RedisOAuth2AuthorizationRequestRepository redisOAuth2AuthorizationRequestRepository;
    private final Oauth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final Oauth2LoginFailureHandler oauth2LoginFailureHandler;

    // Jwt
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtCookieHelper jwtCookieHelper;

    // Domain
    private final UrlProperty urlProperty;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, jwtCookieHelper);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers ->
                        headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(HttpRequestAuthorizationConfig::configure)
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(ae -> ae
                                .authorizationRequestResolver(customOAuth2AuthorizationRequestResolver)
                                .authorizationRequestRepository(redisOAuth2AuthorizationRequestRepository))
                        .userInfoEndpoint(ui -> ui
                                .userService(strategyOAuth2UserService))
                        .successHandler(oauth2LoginSuccessHandler)
                        .failureHandler(oauth2LoginFailureHandler))
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((_, res, _) ->
                                res.setStatus(HttpStatus.UNAUTHORIZED.value()))
                        .accessDeniedHandler((_, res, _) ->
                                res.setStatus(HttpStatus.FORBIDDEN.value())))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(urlProperty.getFrontUrl()));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowCredentials(true);

        configuration.setAllowedHeaders(List.of("*"));

        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        String hierarchy = Arrays.stream(MemberRole.values())
                .sorted(Comparator.comparingInt(MemberRole::getPriority))
                .map(role -> "ROLE_" + role.name())
                .collect(Collectors.joining(" < "));

        return RoleHierarchyImpl.fromHierarchy(hierarchy);
    }

}
