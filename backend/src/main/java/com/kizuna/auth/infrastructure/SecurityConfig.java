package com.kizuna.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtDecoder jwtDecoder;
  private final PlatformJwtAuthenticationConverter jwtAuthenticationConverter;
  private final PlatformAuthenticationEntryPoint authenticationEntryPoint;
  private final PlatformBearerTokenResolver bearerTokenResolver;

  private static final RequestMatcher[] CSRF_IGNORED_MATCHERS = {
    PathPatternRequestMatcher.withDefaults().matcher("/platform/login"),
    PathPatternRequestMatcher.withDefaults().matcher("/files/upload"),
    // 匿名 POST の招待新規登録受諾（Bearer なしのため Bearer 免除に該当しない）。既存受諾(/existing)は Bearer 付きで既存免除に該当する。
    PathPatternRequestMatcher.withDefaults().matcher("/platform/cast-invitations/*/acceptance"),
    request -> {
      String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
      return StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ");
    }
  };

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(
            csrf ->
                csrf.ignoringRequestMatchers(CSRF_IGNORED_MATCHERS)
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        // 公開端点が保護対象パスの内側に混在するため securityMatcher で受限域を絞らず、全域を単一チェーンで扱う。
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(
                        jwt ->
                            jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .authenticationEntryPoint(authenticationEntryPoint)
                    // PlatformBearerTokenResolver: 公開端点では壊れた Bearer を無視して匿名継続させる
                    // （decoder より前段の全リクエスト共通処理のため、@PreAuthorize/@PermitAll の区別を待てない）。
                    .bearerTokenResolver(bearerTokenResolver))
        .formLogin(AbstractHttpConfigurer::disable);
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * 単一の UserDetailsService Bean + PasswordEncoder Bean から Boot が自動組み立てた DaoAuthenticationProvider
   * を公開する。
   */
  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }
}
