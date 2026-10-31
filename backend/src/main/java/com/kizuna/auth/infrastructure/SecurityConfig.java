package com.kizuna.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtDecoder jwtDecoder;
  private final JwtAuthoritiesConverter jwtAuthenticationConverter;
  private final JwtAuthenticationEntryPoint authenticationEntryPoint;
  private final ExemptingBearerTokenResolver bearerTokenResolver;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // 認証は Bearer だけで、cookie では認証しない。cookie を読む端点（リフレッシュ・ログアウト）は
    // X-Requested-With を自分で要求し、ログイン系は JSON 本文を要求するのでフォームの偽装送信は 415 で止まる。
    http.csrf(AbstractHttpConfigurer::disable)
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
                    // ExemptingBearerTokenResolver: 公開端点では壊れた Bearer を無視して匿名継続させる
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
