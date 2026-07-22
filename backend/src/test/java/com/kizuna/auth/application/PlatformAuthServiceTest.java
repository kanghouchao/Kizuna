package com.kizuna.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.auth.api.dto.PlatformMeResponse;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.JwtUtil;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.domain.Capability;
import com.kizuna.user.domain.CapabilityBundle;
import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
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

  private static final long HQ_BUNDLE_ID = 10L;
  private static final long STORE_BUNDLE_ID = 20L;

  @Mock private PlatformUserRepository userRepository;
  @Mock private CapabilityBundleRepository capabilityBundleRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtUtil jwtUtil;
  @Mock private AuthSessionService authSessionService;

  @Captor private ArgumentCaptor<Map<String, Object>> claimsCaptor;

  @InjectMocks private PlatformAuthService authService;

  private PlatformUser hqAdmin() {
    return PlatformUser.builder()
        .email("admin@kizuna.test")
        .password("stored-hash")
        .displayName("HQ管理者")
        .enabled(true)
        .userType(UserType.STAFF)
        .bundleIds(Set.of(HQ_BUNDLE_ID))
        .storeScopeType(StoreScopeType.ALL_STORES)
        .storeIds(Set.of())
        .build();
  }

  private CapabilityBundle hqBundle() {
    return CapabilityBundle.builder()
        .name("HQ管理者")
        .capabilities(
            Set.of(
                Capability.STORE_MANAGE,
                Capability.STAFF_MANAGE,
                Capability.SYSTEM_CONFIG_MANAGE,
                Capability.PLATFORM_MENU_VIEW,
                Capability.PLATFORM_ASSET_MANAGE,
                Capability.STORE_VIEW,
                Capability.ORDER_SET_MANAGE))
        .build();
  }

  @Test
  void login_staff_issuesSortedPermAuthoritiesWithoutRoleClaim() {
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(hqAdmin()));
    when(passwordEncoder.matches("pass", "stored-hash")).thenReturn(true);
    when(capabilityBundleRepository.findAllById(Set.of(HQ_BUNDLE_ID)))
        .thenReturn(List.of(hqBundle()));
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
    assertThat(authorities)
        .isEqualTo(
            List.of(
                "PERM_ORDER_SET_MANAGE",
                "PERM_PLATFORM_ASSET_MANAGE",
                "PERM_PLATFORM_MENU_VIEW",
                "PERM_STAFF_MANAGE",
                "PERM_STORE_MANAGE",
                "PERM_STORE_VIEW",
                "PERM_SYSTEM_CONFIG_MANAGE"));
    assertThat(claims.get("userType")).isEqualTo("STAFF");
    // HQ は STORE コンソール能力を持たないため店舗文脈を確立できない（僭称ヘッダは従来どおり 403）。
    assertThat(claims.get("storeBridge")).isEqualTo(false);
    assertThat(claims).doesNotContainKey("role");
    assertThat(claims.get("storeScopeType")).isEqualTo("ALL_STORES");
    assertThat(claims.get("storeIds")).isEqualTo(List.of());
  }

  @Test
  void login_staffWithStoreCapabilities_setsStoreBridgeTrue() {
    PlatformUser staff =
        PlatformUser.builder()
            .email("staff@kizuna.test")
            .password("stored-hash")
            .displayName("店舗スタッフ")
            .enabled(true)
            .userType(UserType.STAFF)
            .bundleIds(Set.of(STORE_BUNDLE_ID))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    when(userRepository.findByEmail("staff@kizuna.test")).thenReturn(Optional.of(staff));
    when(passwordEncoder.matches("pass", "stored-hash")).thenReturn(true);
    when(capabilityBundleRepository.findAllById(Set.of(STORE_BUNDLE_ID)))
        .thenReturn(
            List.of(
                CapabilityBundle.builder()
                    .name("店舗スタッフ")
                    .capabilities(Set.of(Capability.ORDER_MANAGE, Capability.STORE_VIEW))
                    .build()));
    when(jwtUtil.generateToken(eq("staff@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), any()))
        .thenReturn(new Token("t", 1L));

    authService.login("staff@kizuna.test", "pass");

    verify(jwtUtil)
        .generateToken(
            eq("staff@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), claimsCaptor.capture());
    Map<String, Object> claims = claimsCaptor.getValue();
    @SuppressWarnings("unchecked")
    List<String> authorities = (List<String>) claims.get("authorities");
    assertThat(authorities).isEqualTo(List.of("PERM_ORDER_MANAGE", "PERM_STORE_VIEW"));
    assertThat(claims.get("storeBridge")).isEqualTo(true);
    assertThat(claims.get("storeScopeType")).isEqualTo("SPECIFIC_STORES");
    assertThat(claims.get("storeIds")).isEqualTo(List.of(1L));
  }

  @Test
  void login_staffWithOnlyStoreMenuMarker_doesNotSetStoreBridge() {
    PlatformUser staff =
        PlatformUser.builder()
            .email("menu@kizuna.test")
            .password("stored-hash")
            .displayName("店舗メニュー標識のみ")
            .enabled(true)
            .userType(UserType.STAFF)
            .bundleIds(Set.of(STORE_BUNDLE_ID))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    when(userRepository.findByEmail("menu@kizuna.test")).thenReturn(Optional.of(staff));
    when(passwordEncoder.matches("pass", "stored-hash")).thenReturn(true);
    when(capabilityBundleRepository.findAllById(Set.of(STORE_BUNDLE_ID)))
        .thenReturn(
            List.of(
                CapabilityBundle.builder()
                    .name("店舗メニュー標識のみ")
                    .capabilities(Set.of(Capability.STORE_MENU_VIEW))
                    .build()));
    when(jwtUtil.generateToken(eq("menu@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), any()))
        .thenReturn(new Token("t", 1L));

    authService.login("menu@kizuna.test", "pass");

    verify(jwtUtil)
        .generateToken(eq("menu@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), claimsCaptor.capture());
    Map<String, Object> claims = claimsCaptor.getValue();
    // 標識能力（STORE_MENU_VIEW）単独では店舗文脈を確立できない（PR #411 codex 指摘）。
    assertThat(claims.get("storeBridge")).isEqualTo(false);
  }

  @Test
  void login_staffWithOperationalStoreCapabilityAndMenuMarker_setsStoreBridgeTrue() {
    PlatformUser staff =
        PlatformUser.builder()
            .email("manager@kizuna.test")
            .password("stored-hash")
            .displayName("店長")
            .enabled(true)
            .userType(UserType.STAFF)
            .bundleIds(Set.of(STORE_BUNDLE_ID))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    when(userRepository.findByEmail("manager@kizuna.test")).thenReturn(Optional.of(staff));
    when(passwordEncoder.matches("pass", "stored-hash")).thenReturn(true);
    when(capabilityBundleRepository.findAllById(Set.of(STORE_BUNDLE_ID)))
        .thenReturn(
            List.of(
                CapabilityBundle.builder()
                    .name("店長")
                    .capabilities(Set.of(Capability.ORDER_MANAGE, Capability.STORE_MENU_VIEW))
                    .build()));
    when(jwtUtil.generateToken(eq("manager@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), any()))
        .thenReturn(new Token("t", 1L));

    authService.login("manager@kizuna.test", "pass");

    verify(jwtUtil)
        .generateToken(
            eq("manager@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), claimsCaptor.capture());
    Map<String, Object> claims = claimsCaptor.getValue();
    // 実運用の STORE 能力（ORDER_MANAGE）を保持するため、標識との併存でも店舗文脈を確立できる。
    assertThat(claims.get("storeBridge")).isEqualTo(true);
  }

  @Test
  void login_cast_issuesRoleCastOnlyWithoutStoreBridge() {
    PlatformUser cast =
        PlatformUser.builder()
            .email("cast@kizuna.test")
            .password("stored-hash")
            .displayName("キャスト")
            .enabled(true)
            .userType(UserType.CAST)
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
    @SuppressWarnings("unchecked")
    List<String> authorities = (List<String>) claims.get("authorities");
    assertThat(authorities).isEqualTo(List.of("ROLE_CAST"));
    assertThat(claims.get("userType")).isEqualTo("CAST");
    assertThat(claims.get("storeBridge")).isEqualTo(false);
  }

  @Test
  void login_mixedCaseEmail_resolvesToLowercaseUser() {
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(hqAdmin()));
    when(passwordEncoder.matches("pass", "stored-hash")).thenReturn(true);
    when(capabilityBundleRepository.findAllById(Set.of(HQ_BUNDLE_ID)))
        .thenReturn(List.of(hqBundle()));
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

  @Test
  void me_staff_returnsCapabilitiesAndDerivedConsole() {
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(hqAdmin()));
    when(capabilityBundleRepository.findAllById(Set.of(HQ_BUNDLE_ID)))
        .thenReturn(List.of(hqBundle()));

    Optional<PlatformMeResponse> res = authService.me("admin@kizuna.test");

    assertThat(res).isPresent();
    assertThat(res.get().userType()).isEqualTo("STAFF");
    assertThat(res.get().console()).isEqualTo("platform");
    assertThat(res.get().capabilities()).contains("STORE_MANAGE", "STAFF_MANAGE");
    // HQ 束は PLATFORM 能力 + SHARED（STORE_VIEW / ORDER_SET_MANAGE）のみで、STORE コンソール能力を持たないため false。
    assertThat(res.get().storeBridge()).isFalse();
  }

  @Test
  void me_staffWithStoreConsoleCapability_returnsStoreBridgeTrue() {
    PlatformUser staff =
        PlatformUser.builder()
            .email("manager@kizuna.test")
            .password("stored-hash")
            .displayName("店長")
            .enabled(true)
            .userType(UserType.STAFF)
            .bundleIds(Set.of(STORE_BUNDLE_ID))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    when(userRepository.findByEmail("manager@kizuna.test")).thenReturn(Optional.of(staff));
    when(capabilityBundleRepository.findAllById(Set.of(STORE_BUNDLE_ID)))
        .thenReturn(
            List.of(
                CapabilityBundle.builder()
                    .name("店長")
                    .capabilities(Set.of(Capability.ORDER_MANAGE, Capability.STORE_VIEW))
                    .build()));

    Optional<PlatformMeResponse> res = authService.me("manager@kizuna.test");

    assertThat(res).isPresent();
    // 実運用の STORE コンソール能力（ORDER_MANAGE）保持者は JWT storeBridge claim と同源で true を返す。
    assertThat(res.get().storeBridge()).isTrue();
  }

  @Test
  void me_hybridStaffWithPlatformAndStoreCapabilities_returnsPlatformConsoleAndStoreBridgeTrue() {
    // 混成束（PLATFORM 能力と実運用 STORE 能力の併持）: 着地は platform 優先のまま store_bridge=true（#428 AC1）。
    PlatformUser staff =
        PlatformUser.builder()
            .email("hybrid@kizuna.test")
            .password("stored-hash")
            .displayName("兼務者")
            .enabled(true)
            .userType(UserType.STAFF)
            .bundleIds(Set.of(STORE_BUNDLE_ID))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    when(userRepository.findByEmail("hybrid@kizuna.test")).thenReturn(Optional.of(staff));
    when(capabilityBundleRepository.findAllById(Set.of(STORE_BUNDLE_ID)))
        .thenReturn(
            List.of(
                CapabilityBundle.builder()
                    .name("兼務束")
                    .capabilities(Set.of(Capability.STORE_MANAGE, Capability.ORDER_MANAGE))
                    .build()));

    Optional<PlatformMeResponse> res = authService.me("hybrid@kizuna.test");

    assertThat(res).isPresent();
    assertThat(res.get().console()).isEqualTo("platform");
    assertThat(res.get().storeBridge()).isTrue();
  }

  @Test
  void me_staffWithOnlySharedCapabilities_returnsStoreBridgeFalse() {
    PlatformUser staff =
        PlatformUser.builder()
            .email("shared@kizuna.test")
            .password("stored-hash")
            .displayName("跨店参照のみ")
            .enabled(true)
            .userType(UserType.STAFF)
            .bundleIds(Set.of(STORE_BUNDLE_ID))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();
    when(userRepository.findByEmail("shared@kizuna.test")).thenReturn(Optional.of(staff));
    when(capabilityBundleRepository.findAllById(Set.of(STORE_BUNDLE_ID)))
        .thenReturn(
            List.of(
                CapabilityBundle.builder()
                    .name("跨店参照のみ")
                    .capabilities(Set.of(Capability.STORE_VIEW, Capability.ORDER_SET_MANAGE))
                    .build()));

    Optional<PlatformMeResponse> res = authService.me("shared@kizuna.test");

    assertThat(res).isPresent();
    // SHARED 能力（跨店参照）は STORE コンソール能力ではないため店舗文脈を確立できない → false。
    assertThat(res.get().storeBridge()).isFalse();
  }

  @Test
  void me_cast_returnsNoneConsoleWithoutCapabilities() {
    PlatformUser cast =
        PlatformUser.builder()
            .email("cast@kizuna.test")
            .password("stored-hash")
            .displayName("キャスト")
            .enabled(true)
            .userType(UserType.CAST)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    when(userRepository.findByEmail("cast@kizuna.test")).thenReturn(Optional.of(cast));

    Optional<PlatformMeResponse> res = authService.me("cast@kizuna.test");

    assertThat(res).isPresent();
    assertThat(res.get().userType()).isEqualTo("CAST");
    assertThat(res.get().console()).isEqualTo("none");
    assertThat(res.get().capabilities()).isEmpty();
  }

  @Test
  void me_staffWithOnlyStoreMenuMarker_returnsNoneConsole() {
    PlatformUser staff =
        PlatformUser.builder()
            .email("menu@kizuna.test")
            .password("stored-hash")
            .displayName("店舗メニュー標識のみ")
            .enabled(true)
            .userType(UserType.STAFF)
            .bundleIds(Set.of(STORE_BUNDLE_ID))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    when(userRepository.findByEmail("menu@kizuna.test")).thenReturn(Optional.of(staff));
    when(capabilityBundleRepository.findAllById(Set.of(STORE_BUNDLE_ID)))
        .thenReturn(
            List.of(
                CapabilityBundle.builder()
                    .name("店舗メニュー標識のみ")
                    .capabilities(Set.of(Capability.STORE_MENU_VIEW))
                    .build()));

    Optional<PlatformMeResponse> res = authService.me("menu@kizuna.test");

    assertThat(res).isPresent();
    // 標識のみの束は店舗コンソールに着地しない（fail-closed — PR #411 codex 指摘）。
    assertThat(res.get().console()).isEqualTo("none");
    assertThat(res.get().capabilities()).containsExactly("STORE_MENU_VIEW");
    // 標識能力（STORE_MENU_VIEW）単独では店舗文脈を確立できないため false。
    assertThat(res.get().storeBridge()).isFalse();
  }

  @Test
  void updateMe_emailNotFound_throwsServiceException() {
    when(userRepository.findByEmail("missing@kizuna.test")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.updateMe("missing@kizuna.test", "新表示名"))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void updateMe_success_updatesDisplayNameAndReturnsResponse() {
    PlatformUser user = hqAdmin();
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(user));
    when(capabilityBundleRepository.findAllById(Set.of(HQ_BUNDLE_ID)))
        .thenReturn(List.of(hqBundle()));

    PlatformMeResponse res = authService.updateMe("admin@kizuna.test", "新しい表示名");

    assertThat(user.getDisplayName()).isEqualTo("新しい表示名");
    assertThat(res.displayName()).isEqualTo("新しい表示名");
    assertThat(res.email()).isEqualTo("admin@kizuna.test");
    verify(userRepository).save(user);
  }

  @Test
  void changePassword_wrongCurrentPassword_throwsServiceExceptionAndKeepsSession() {
    PlatformUser user = hqAdmin();
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

    assertThatThrownBy(
            () ->
                authService.changePassword(
                    "admin@kizuna.test", "wrong", "new-password-123", "Bearer tok"))
        .isInstanceOf(ServiceException.class);

    verify(userRepository, never()).save(any());
    verify(authSessionService, never()).invalidate(any());
  }

  @Test
  void changePassword_success_encodesSavesAndInvalidatesSession() {
    PlatformUser user = hqAdmin();
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("current-pass", "stored-hash")).thenReturn(true);
    when(passwordEncoder.encode("new-password-123")).thenReturn("new-encoded-hash");

    authService.changePassword(
        "admin@kizuna.test", "current-pass", "new-password-123", "Bearer tok");

    assertThat(user.getPassword()).isEqualTo("new-encoded-hash");
    verify(userRepository).save(user);
    verify(authSessionService).invalidate("Bearer tok");
  }

  private PlatformUser disabledUser() {
    return PlatformUser.builder()
        .email("admin@kizuna.test")
        .password("stored-hash")
        .displayName("HQ管理者")
        .enabled(false)
        .userType(UserType.STAFF)
        .bundleIds(Set.of(HQ_BUNDLE_ID))
        .storeScopeType(StoreScopeType.ALL_STORES)
        .storeIds(Set.of())
        .build();
  }
}
