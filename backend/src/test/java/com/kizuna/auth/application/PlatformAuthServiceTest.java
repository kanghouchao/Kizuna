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
import com.kizuna.auth.infrastructure.PlatformJwtIssuer;
import com.kizuna.auth.infrastructure.PlatformUserDetails;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.Permission;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PlatformAuthServiceTest {

  private static final long HQ_ROLE_ID = 10L;
  private static final long STORE_ROLE_ID = 20L;

  @Mock private PlatformUserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private PermissionRepository permissionRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private PlatformJwtIssuer jwtIssuer;
  @Mock private AuthSessionService authSessionService;
  @Mock private AuthenticationManager authenticationManager;
  @Mock private Authentication authentication;

  @Captor private ArgumentCaptor<Map<String, Object>> claimsCaptor;

  @InjectMocks private PlatformAuthService authService;

  /** authenticationManager.authenticate が指定ユーザーを principal に持つ成功済み Authentication を返すよう配線する。 */
  private void stubSuccessfulAuthentication(String email, String password, PlatformUser user) {
    when(authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(email, password)))
        .thenReturn(authentication);
    when(authentication.getPrincipal()).thenReturn(new PlatformUserDetails(user));
  }

  /** 権限解決の 2 段（ロール id → 権限 id 集合 → 権限コード）を配線する。 */
  private void stubRolePermissions(long roleId, String roleName, Set<PermissionCode> codes) {
    Set<Long> permissionIds =
        codes.stream().map(PlatformAuthServiceTest::permissionId).collect(Collectors.toSet());
    when(roleRepository.findAllById(Set.of(roleId)))
        .thenReturn(List.of(Role.builder().name(roleName).permissionIds(permissionIds).build()));
    when(permissionRepository.findAllById(permissionIds))
        .thenReturn(
            codes.stream().map(code -> Permission.builder().code(code.name()).build()).toList());
  }

  private static long permissionId(PermissionCode code) {
    return code.ordinal() + 1L;
  }

  private PlatformUser hqAdmin() {
    return PlatformUser.builder()
        .email("admin@kizuna.test")
        .password("stored-hash")
        .displayName("HQ管理者")
        .enabled(true)
        .userType(UserType.STAFF)
        .roleIds(Set.of(HQ_ROLE_ID))
        .storeScopeType(StoreScopeType.ALL_STORES)
        .storeIds(Set.of())
        .build();
  }

  private void stubHqRole() {
    stubRolePermissions(
        HQ_ROLE_ID,
        "HQ管理者",
        Set.of(
            PermissionCode.STORE_MANAGE,
            PermissionCode.ROLE_MANAGE,
            PermissionCode.STAFF_MANAGE,
            PermissionCode.SYSTEM_CONFIG_MANAGE,
            PermissionCode.PLATFORM_MENU_VIEW,
            PermissionCode.PLATFORM_ASSET_MANAGE,
            PermissionCode.STORE_VIEW,
            PermissionCode.ORDER_SET_MANAGE));
  }

  private PlatformUser storeStaff(String email, String displayName, StoreScopeType scopeType) {
    return PlatformUser.builder()
        .email(email)
        .password("stored-hash")
        .displayName(displayName)
        .enabled(true)
        .userType(UserType.STAFF)
        .roleIds(Set.of(STORE_ROLE_ID))
        .storeScopeType(scopeType)
        .storeIds(scopeType == StoreScopeType.ALL_STORES ? Set.of() : Set.of(1L))
        .build();
  }

  @Test
  void login_staff_issuesSortedPermAuthoritiesWithoutRoleClaim() {
    stubSuccessfulAuthentication("admin@kizuna.test", "pass", hqAdmin());
    stubHqRole();
    Token mockToken = new Token("platform_token", 12345L);
    when(jwtIssuer.issue(eq("admin@kizuna.test"), any())).thenReturn(mockToken);

    Token res = authService.login("admin@kizuna.test", "pass");

    assertThat(res.token()).isEqualTo("platform_token");
    verify(jwtIssuer).issue(eq("admin@kizuna.test"), claimsCaptor.capture());
    Map<String, Object> claims = claimsCaptor.getValue();
    @SuppressWarnings("unchecked")
    List<String> authorities = (List<String>) claims.get("authorities");
    assertThat(authorities)
        .isEqualTo(
            List.of(
                "PERM_ORDER_SET_MANAGE",
                "PERM_PLATFORM_ASSET_MANAGE",
                "PERM_PLATFORM_MENU_VIEW",
                "PERM_ROLE_MANAGE",
                "PERM_STAFF_MANAGE",
                "PERM_STORE_MANAGE",
                "PERM_STORE_VIEW",
                "PERM_SYSTEM_CONFIG_MANAGE"));
    assertThat(claims.get("userType")).isEqualTo("STAFF");
    // HQ 束は STAFF_MANAGE（Console.STORE）を含むため店舗文脈を確立できる（ADR 0020）。
    assertThat(claims.get("storeBridge")).isEqualTo(true);
    assertThat(claims).doesNotContainKey("role");
    assertThat(claims.get("storeScopeType")).isEqualTo("ALL_STORES");
    assertThat(claims.get("storeIds")).isEqualTo(List.of());
  }

  @Test
  void login_staffWithStorePermissions_setsStoreBridgeTrue() {
    PlatformUser staff = storeStaff("staff@kizuna.test", "店舗スタッフ", StoreScopeType.SPECIFIC_STORES);
    stubSuccessfulAuthentication("staff@kizuna.test", "pass", staff);
    stubRolePermissions(
        STORE_ROLE_ID, "店舗スタッフ", Set.of(PermissionCode.ORDER_MANAGE, PermissionCode.STORE_VIEW));
    when(jwtIssuer.issue(eq("staff@kizuna.test"), any())).thenReturn(new Token("t", 1L));

    authService.login("staff@kizuna.test", "pass");

    verify(jwtIssuer).issue(eq("staff@kizuna.test"), claimsCaptor.capture());
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
        storeStaff("menu@kizuna.test", "店舗メニュー標識のみ", StoreScopeType.SPECIFIC_STORES);
    stubSuccessfulAuthentication("menu@kizuna.test", "pass", staff);
    stubRolePermissions(STORE_ROLE_ID, "店舗メニュー標識のみ", Set.of(PermissionCode.STORE_MENU_VIEW));
    when(jwtIssuer.issue(eq("menu@kizuna.test"), any())).thenReturn(new Token("t", 1L));

    authService.login("menu@kizuna.test", "pass");

    verify(jwtIssuer).issue(eq("menu@kizuna.test"), claimsCaptor.capture());
    Map<String, Object> claims = claimsCaptor.getValue();
    // 標識権限（STORE_MENU_VIEW）単独では店舗文脈を確立できない。
    assertThat(claims.get("storeBridge")).isEqualTo(false);
  }

  @Test
  void login_staffWithOperationalStorePermissionAndMenuMarker_setsStoreBridgeTrue() {
    PlatformUser staff = storeStaff("manager@kizuna.test", "店長", StoreScopeType.SPECIFIC_STORES);
    stubSuccessfulAuthentication("manager@kizuna.test", "pass", staff);
    stubRolePermissions(
        STORE_ROLE_ID, "店長", Set.of(PermissionCode.ORDER_MANAGE, PermissionCode.STORE_MENU_VIEW));
    when(jwtIssuer.issue(eq("manager@kizuna.test"), any())).thenReturn(new Token("t", 1L));

    authService.login("manager@kizuna.test", "pass");

    verify(jwtIssuer).issue(eq("manager@kizuna.test"), claimsCaptor.capture());
    Map<String, Object> claims = claimsCaptor.getValue();
    // 実運用の STORE 権限（ORDER_MANAGE）を保持するため、標識との併存でも店舗文脈を確立できる。
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
    stubSuccessfulAuthentication("cast@kizuna.test", "pass", cast);
    when(jwtIssuer.issue(eq("cast@kizuna.test"), any())).thenReturn(new Token("t", 1L));

    authService.login("cast@kizuna.test", "pass");

    verify(jwtIssuer).issue(eq("cast@kizuna.test"), claimsCaptor.capture());
    Map<String, Object> claims = claimsCaptor.getValue();
    @SuppressWarnings("unchecked")
    List<String> authorities = (List<String>) claims.get("authorities");
    assertThat(authorities).isEqualTo(List.of("ROLE_CAST"));
    assertThat(claims.get("userType")).isEqualTo("CAST");
    assertThat(claims.get("storeBridge")).isEqualTo(false);
  }

  @Test
  void login_mixedCaseEmail_resolvesToLowercaseUser() {
    stubSuccessfulAuthentication("admin@kizuna.test", "pass", hqAdmin());
    stubHqRole();
    when(jwtIssuer.issue(eq("admin@kizuna.test"), any()))
        .thenReturn(new Token("platform_token", 12345L));

    Token res = authService.login("ADMIN@Kizuna.TEST", "pass");

    assertThat(res.token()).isEqualTo("platform_token");
    // 照合は小文字正規化後の email を AuthenticationManager へ渡す（保存済みシードは全て小文字）。
    ArgumentCaptor<UsernamePasswordAuthenticationToken> tokenCaptor =
        ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
    verify(authenticationManager).authenticate(tokenCaptor.capture());
    assertThat(tokenCaptor.getValue().getPrincipal()).isEqualTo("admin@kizuna.test");
  }

  @Test
  void me_staff_returnsPermissionsAndDerivedConsole() {
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(hqAdmin()));
    stubHqRole();

    Optional<PlatformMeResponse> res = authService.me("admin@kizuna.test");

    assertThat(res).isPresent();
    assertThat(res.get().userType()).isEqualTo("STAFF");
    assertThat(res.get().console()).isEqualTo("platform");
    assertThat(res.get().permissions()).contains("STORE_MANAGE", "ROLE_MANAGE", "STAFF_MANAGE");
    // 着地は PLATFORM 権限が優先で platform のまま、店舗文脈は STAFF_MANAGE 由来で確立できる。
    assertThat(res.get().storeBridge()).isTrue();
  }

  @Test
  void me_staffWithStoreConsolePermission_returnsStoreBridgeTrue() {
    PlatformUser staff = storeStaff("manager@kizuna.test", "店長", StoreScopeType.SPECIFIC_STORES);
    when(userRepository.findByEmail("manager@kizuna.test")).thenReturn(Optional.of(staff));
    stubRolePermissions(
        STORE_ROLE_ID, "店長", Set.of(PermissionCode.ORDER_MANAGE, PermissionCode.STORE_VIEW));

    Optional<PlatformMeResponse> res = authService.me("manager@kizuna.test");

    assertThat(res).isPresent();
    // 実運用の STORE コンソール権限（ORDER_MANAGE）保持者は JWT storeBridge claim と同源で true を返す。
    assertThat(res.get().storeBridge()).isTrue();
  }

  @Test
  void me_hybridStaffWithPlatformAndStorePermissions_returnsPlatformConsoleAndStoreBridgeTrue() {
    // 混成ロール（PLATFORM 権限と実運用 STORE 権限の併持）: 着地は platform 優先のまま store_bridge=true。
    PlatformUser staff = storeStaff("hybrid@kizuna.test", "兼務者", StoreScopeType.SPECIFIC_STORES);
    when(userRepository.findByEmail("hybrid@kizuna.test")).thenReturn(Optional.of(staff));
    stubRolePermissions(
        STORE_ROLE_ID, "兼務ロール", Set.of(PermissionCode.STORE_MANAGE, PermissionCode.ORDER_MANAGE));

    Optional<PlatformMeResponse> res = authService.me("hybrid@kizuna.test");

    assertThat(res).isPresent();
    assertThat(res.get().console()).isEqualTo("platform");
    assertThat(res.get().storeBridge()).isTrue();
  }

  @Test
  void me_staffWithOnlySharedPermissions_returnsStoreBridgeFalse() {
    PlatformUser staff = storeStaff("shared@kizuna.test", "跨店参照のみ", StoreScopeType.ALL_STORES);
    when(userRepository.findByEmail("shared@kizuna.test")).thenReturn(Optional.of(staff));
    stubRolePermissions(
        STORE_ROLE_ID,
        "跨店参照のみ",
        Set.of(PermissionCode.STORE_VIEW, PermissionCode.ORDER_SET_MANAGE));

    Optional<PlatformMeResponse> res = authService.me("shared@kizuna.test");

    assertThat(res).isPresent();
    // SHARED 権限（跨店参照）は STORE コンソール権限ではないため店舗文脈を確立できない → false。
    assertThat(res.get().storeBridge()).isFalse();
  }

  @Test
  void me_cast_returnsNoneConsoleWithoutPermissions() {
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
    assertThat(res.get().permissions()).isEmpty();
  }

  @Test
  void me_staffWithOnlyStoreMenuMarker_returnsNoneConsole() {
    PlatformUser staff =
        storeStaff("menu@kizuna.test", "店舗メニュー標識のみ", StoreScopeType.SPECIFIC_STORES);
    when(userRepository.findByEmail("menu@kizuna.test")).thenReturn(Optional.of(staff));
    stubRolePermissions(STORE_ROLE_ID, "店舗メニュー標識のみ", Set.of(PermissionCode.STORE_MENU_VIEW));

    Optional<PlatformMeResponse> res = authService.me("menu@kizuna.test");

    assertThat(res).isPresent();
    // 標識のみのロールは店舗コンソールに着地しない（fail-closed）。
    assertThat(res.get().console()).isEqualTo("none");
    assertThat(res.get().permissions()).containsExactly("STORE_MENU_VIEW");
    // 標識権限（STORE_MENU_VIEW）単独では店舗文脈を確立できないため false。
    assertThat(res.get().storeBridge()).isFalse();
  }

  @Test
  void updateMe_emailNotFound_throwsStaleSession() {
    when(userRepository.findByEmail("missing@kizuna.test")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.updateMe("missing@kizuna.test", "新表示名"))
        .isInstanceOf(StaleSessionException.class);
  }

  @Test
  void updateMe_success_updatesDisplayNameAndReturnsResponse() {
    PlatformUser user = hqAdmin();
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(user));
    stubHqRole();

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
}
