package com.kizuna.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.JwtUtil;
import com.kizuna.user.domain.PlatformRole;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PlatformAuthServiceTest {

  @Mock private PlatformUserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtUtil jwtUtil;

  @Captor private ArgumentCaptor<Map<String, Object>> claimsCaptor;

  @InjectMocks private PlatformAuthService authService;

  private PlatformUser hqAdmin() {
    return PlatformUser.builder()
        .email("admin@kizuna.test")
        .password("stored-hash")
        .displayName("HQ管理者")
        .enabled(true)
        .role(PlatformRole.HQ_ADMIN)
        .storeScopeType(StoreScopeType.ALL_STORES)
        .storeIds(Set.of())
        .build();
  }

  @Test
  void login_success_issuesPlatformTokenWithRoleAndScopeClaims() {
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(hqAdmin()));
    when(passwordEncoder.matches("pass", "stored-hash")).thenReturn(true);
    Token mockToken = new Token("platform_token", 12345L);
    when(jwtUtil.generateToken(eq("admin@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), any()))
        .thenReturn(mockToken);

    Token res = authService.login("admin@kizuna.test", "pass");

    assertThat(res.token()).isEqualTo("platform_token");
    verify(jwtUtil)
        .generateToken(
            eq("admin@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), claimsCaptor.capture());
    Map<String, Object> claims = claimsCaptor.getValue();
    @SuppressWarnings("unchecked")
    List<String> authorities = (List<String>) claims.get("authorities");
    // ROLE_ が先頭、後続に旧権限マッピング（過橋期）。
    assertThat(authorities.get(0)).isEqualTo("ROLE_HQ_ADMIN");
    assertThat(authorities)
        .containsExactlyInAnyOrder("ROLE_HQ_ADMIN", "TENANT_MANAGE", "SYSTEM_CONFIG");
    assertThat(claims.get("role")).isEqualTo("HQ_ADMIN");
    assertThat(claims.get("storeScopeType")).isEqualTo("ALL_STORES");
    assertThat(claims.get("storeIds")).isEqualTo(List.of());
  }

  @Test
  void login_success_specificStores_carriesStoreIdsClaim() {
    PlatformUser manager =
        PlatformUser.builder()
            .email("mgr@kizuna.test")
            .password("stored-hash")
            .displayName("店長")
            .enabled(true)
            .role(PlatformRole.STORE_MANAGER)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    when(userRepository.findByEmail("mgr@kizuna.test")).thenReturn(Optional.of(manager));
    when(passwordEncoder.matches("pass", "stored-hash")).thenReturn(true);
    when(jwtUtil.generateToken(eq("mgr@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), any()))
        .thenReturn(new Token("t", 1L));

    authService.login("mgr@kizuna.test", "pass");

    verify(jwtUtil)
        .generateToken(eq("mgr@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), claimsCaptor.capture());
    Map<String, Object> claims = claimsCaptor.getValue();
    @SuppressWarnings("unchecked")
    List<String> authorities = (List<String>) claims.get("authorities");
    assertThat(authorities.get(0)).isEqualTo("ROLE_STORE_MANAGER");
    assertThat(authorities)
        .containsExactlyInAnyOrder(
            "ROLE_STORE_MANAGER",
            "ORDER_MANAGE",
            "CAST_MANAGE",
            "CUSTOMER_MANAGE",
            "TENANT_CONFIG");
    assertThat(claims.get("role")).isEqualTo("STORE_MANAGER");
    assertThat(claims.get("storeScopeType")).isEqualTo("SPECIFIC_STORES");
    assertThat(claims.get("storeIds")).isEqualTo(List.of(1L));
  }

  @Test
  void login_cast_issuesRoleAuthorityWithoutLegacyPermissions() {
    PlatformUser cast =
        PlatformUser.builder()
            .email("cast@kizuna.test")
            .password("stored-hash")
            .displayName("キャスト")
            .enabled(true)
            .role(PlatformRole.CAST)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    when(userRepository.findByEmail("cast@kizuna.test")).thenReturn(Optional.of(cast));
    when(passwordEncoder.matches("pass", "stored-hash")).thenReturn(true);
    when(jwtUtil.generateToken(eq("cast@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), any()))
        .thenReturn(new Token("t", 1L));

    authService.login("cast@kizuna.test", "pass");

    verify(jwtUtil)
        .generateToken(eq("cast@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), claimsCaptor.capture());
    Map<String, Object> claims = claimsCaptor.getValue();
    assertThat(claims.get("authorities")).isEqualTo(List.of("ROLE_CAST"));
  }

  @Test
  void login_mixedCaseEmail_resolvesToLowercaseUser() {
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(hqAdmin()));
    when(passwordEncoder.matches("pass", "stored-hash")).thenReturn(true);
    when(jwtUtil.generateToken(eq("admin@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), any()))
        .thenReturn(new Token("platform_token", 12345L));

    Token res = authService.login("ADMIN@Kizuna.TEST", "pass");

    assertThat(res.token()).isEqualTo("platform_token");
    // 照合は小文字正規化後の email で行う（保存済みシードは全て小文字）。
    verify(userRepository).findByEmail("admin@kizuna.test");
  }

  @Test
  void login_emailNotFound_throwsBadCredentials() {
    when(userRepository.findByEmail("missing@kizuna.test")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login("missing@kizuna.test", "pass"))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("メールアドレスまたはパスワードが正しくありません");

    // 列挙耐性: メール不存在でもダミー bcrypt 照合を 1 回行い、既知メール（誤パスワード）との応答時間差を作らない。
    verify(passwordEncoder).matches(eq("pass"), any());
  }

  @Test
  void login_wrongPassword_throwsBadCredentials() {
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(hqAdmin()));
    when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

    assertThatThrownBy(() -> authService.login("admin@kizuna.test", "wrong"))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("メールアドレスまたはパスワードが正しくありません");
  }

  @Test
  void login_disabledUser_correctPassword_throwsDisabled() {
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(disabledUser()));

    // enabled 判定がパスワード照合より先行するため、正しいパスワードでも DisabledException になる。
    assertThatThrownBy(() -> authService.login("admin@kizuna.test", "pass"))
        .isInstanceOf(DisabledException.class);
  }

  @Test
  void login_disabledUser_wrongPassword_throwsDisabled() {
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(disabledUser()));

    // 誤パスワードでも enabled 判定が先行するため DisabledException（無効化アカウントでのパスワード正誤オラクルを塞ぐ）。
    assertThatThrownBy(() -> authService.login("admin@kizuna.test", "wrong"))
        .isInstanceOf(DisabledException.class);
  }

  private PlatformUser disabledUser() {
    return PlatformUser.builder()
        .email("admin@kizuna.test")
        .password("stored-hash")
        .displayName("HQ管理者")
        .enabled(false)
        .role(PlatformRole.HQ_ADMIN)
        .storeScopeType(StoreScopeType.ALL_STORES)
        .storeIds(Set.of())
        .build();
  }
}
