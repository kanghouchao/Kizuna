package com.kizuna.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.auth.api.dto.MeResponse;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.JwtIssuer;
import com.kizuna.auth.infrastructure.UserPrincipal;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.application.CredentialOperations;
import com.kizuna.user.domain.Permission;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final long HQ_ROLE_ID = 10L;
  private static final long STORE_ROLE_ID = 20L;

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private PermissionRepository permissionRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtIssuer jwtIssuer;
  @Spy private CredentialOperations credentialOperations = new CredentialOperations(event -> {});
  @Mock private AuthenticationManager authenticationManager;
  @Mock private Authentication authentication;
  @Mock private LoginBenefitTrigger loginBenefitTrigger;

  @Captor private ArgumentCaptor<Map<String, Object>> claimsCaptor;

  @InjectMocks private AuthService authService;

  /** authenticationManager.authenticate が指定ユーザーを principal に持つ成功済み Authentication を返すよう配線する。 */
  private void stubSuccessfulAuthentication(String email, String password, User user) {
    when(authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(email, password)))
        .thenReturn(authentication);
    when(authentication.getPrincipal()).thenReturn(new UserPrincipal(user));
  }

  /** 権限解決の 2 段（ロール id → 権限 id 集合 → 権限コード）を配線する。 */
  private void stubRolePermissions(long roleId, String roleName, Set<PermissionCode> codes) {
    Set<Long> permissionIds =
        codes.stream().map(AuthServiceTest::permissionId).collect(Collectors.toSet());
    when(roleRepository.findAllById(Set.of(roleId)))
        .thenReturn(List.of(Role.builder().name(roleName).permissionIds(permissionIds).build()));
    when(permissionRepository.findAllById(permissionIds))
        .thenReturn(
            codes.stream().map(code -> Permission.builder().code(code.name()).build()).toList());
  }

  private static long permissionId(PermissionCode code) {
    return code.ordinal() + 1L;
  }

  private User hqAdmin() {
    return User.builder()
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
            PermissionCode.SYSTEM_CONFIG_MANAGE,
            PermissionCode.ASSET_MANAGE,
            PermissionCode.STORE_VIEW,
            PermissionCode.ORDER_SET_VIEW));
  }

  private User storeStaff(String email, String displayName, StoreScopeType scopeType) {
    return User.builder()
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
    Token mockToken = new Token("issued_token", 12345L);
    when(jwtIssuer.issue(eq("admin@kizuna.test"), any())).thenReturn(mockToken);

    Token res = authService.login("admin@kizuna.test", "pass");

    assertThat(res.token()).isEqualTo("issued_token");
    verify(jwtIssuer).issue(eq("admin@kizuna.test"), claimsCaptor.capture());
    Map<String, Object> claims = claimsCaptor.getValue();
    @SuppressWarnings("unchecked")
    List<String> authorities = (List<String>) claims.get("authorities");
    assertThat(authorities)
        .isEqualTo(
            List.of(
                "PERM_ASSET_MANAGE",
                "PERM_ORDER_SET_VIEW",
                "PERM_ROLE_MANAGE",
                "PERM_STORE_MANAGE",
                "PERM_STORE_VIEW",
                "PERM_SYSTEM_CONFIG_MANAGE"));
    assertThat(claims.get("userType")).isEqualTo("STAFF");
    assertThat(claims).doesNotContainKey("role");
    // 店舗集合は claim に載せない — 要求ごとに DB から解決する。
    assertThat(claims).doesNotContainKeys("storeBridge", "storeScopeType", "storeIds");
    // 発行時点の資格情報の版が claim に載る（ADR 0022 — 検証側が相等比較する前提値）。
    assertThat(claims.get("credentialVersion")).isEqualTo(0L);
  }

  @Test
  void login_staffWithStorePermissions_issuesOnlyPermissionAuthorities() {
    User staff = storeStaff("staff@kizuna.test", "店舗スタッフ", StoreScopeType.SPECIFIC_STORES);
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
  }

  @Test
  void login_member_firesTheLoginBenefitTriggerAfterIssuingTheToken() {
    User member =
        User.builder()
            .email("member@kizuna.test")
            .password("stored-hash")
            .displayName("会員")
            .enabled(true)
            .userType(UserType.MEMBER)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of())
            .build();
    stubSuccessfulAuthentication("member@kizuna.test", "pass", member);
    when(jwtIssuer.issue(eq("member@kizuna.test"), any())).thenReturn(new Token("t", 1L));

    Token token = authService.login("member@kizuna.test", "pass");

    assertThat(token.token()).isEqualTo("t");
    // 発火事象は認証成功 1 回。停止中の身分は発行側で先に拒まれるので、発行の後に 1 度だけ渡す。
    InOrder inOrder = inOrder(jwtIssuer, loginBenefitTrigger);
    inOrder.verify(jwtIssuer).issue(eq("member@kizuna.test"), any());
    inOrder.verify(loginBenefitTrigger).onAuthenticated(member);
  }

  @Test
  void issueTokenFor_doesNotCountAsALogin() {
    stubHqRole();
    when(jwtIssuer.issue(eq("admin@kizuna.test"), any())).thenReturn(new Token("t", 1L));

    authService.issueTokenFor(hqAdmin());

    verifyNoInteractions(loginBenefitTrigger);
  }

  @Test
  void login_cast_issuesRoleCastOnly() {
    User cast =
        User.builder()
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
  }

  @Test
  void login_mixedCaseEmail_resolvesToLowercaseUser() {
    stubSuccessfulAuthentication("admin@kizuna.test", "pass", hqAdmin());
    stubHqRole();
    when(jwtIssuer.issue(eq("admin@kizuna.test"), any()))
        .thenReturn(new Token("issued_token", 12345L));

    Token res = authService.login("ADMIN@Kizuna.TEST", "pass");

    assertThat(res.token()).isEqualTo("issued_token");
    // 照合は小文字正規化後の email を AuthenticationManager へ渡す（User は小文字で保存する）。
    ArgumentCaptor<UsernamePasswordAuthenticationToken> tokenCaptor =
        ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
    verify(authenticationManager).authenticate(tokenCaptor.capture());
    assertThat(tokenCaptor.getValue().getPrincipal()).isEqualTo("admin@kizuna.test");
  }

  @Test
  void me_staff_returnsPermissions() {
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(hqAdmin()));
    stubHqRole();

    Optional<MeResponse> res = authService.me("admin@kizuna.test");

    assertThat(res).isPresent();
    assertThat(res.get().userType()).isEqualTo("STAFF");
    assertThat(res.get().permissions())
        .contains("STORE_MANAGE", "ROLE_MANAGE", "SYSTEM_CONFIG_MANAGE");
  }

  @Test
  void me_cast_returnsNoPermissions() {
    User cast =
        User.builder()
            .email("cast@kizuna.test")
            .password("stored-hash")
            .displayName("キャスト")
            .enabled(true)
            .userType(UserType.CAST)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    when(userRepository.findByEmail("cast@kizuna.test")).thenReturn(Optional.of(cast));

    Optional<MeResponse> res = authService.me("cast@kizuna.test");

    assertThat(res).isPresent();
    assertThat(res.get().userType()).isEqualTo("CAST");
    assertThat(res.get().permissions()).isEmpty();
  }

  @Test
  void updateMe_emailNotFound_throwsStaleSession() {
    when(userRepository.findByEmail("missing@kizuna.test")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.updateMe("missing@kizuna.test", "新表示名"))
        .isInstanceOf(StaleSessionException.class);
  }

  @Test
  void updateMe_success_updatesDisplayNameAndReturnsResponse() {
    User user = hqAdmin();
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(user));
    stubHqRole();

    MeResponse res = authService.updateMe("admin@kizuna.test", "新しい表示名");

    assertThat(user.getDisplayName()).isEqualTo("新しい表示名");
    assertThat(res.displayName()).isEqualTo("新しい表示名");
    assertThat(res.email()).isEqualTo("admin@kizuna.test");
    verify(userRepository).save(user);
  }

  @Test
  void changePassword_wrongCurrentPassword_throwsServiceExceptionAndKeepsSession() {
    User user = hqAdmin();
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

    assertThatThrownBy(
            () -> authService.changePassword("admin@kizuna.test", "wrong", "new-password-123"))
        .isInstanceOf(ServiceException.class);

    verify(userRepository, never()).save(any());
    verifyNoInteractions(credentialOperations);
  }

  @Test
  void changePassword_success_encodesSavesAndPublishesBumpedCredentialVersion() {
    User user = hqAdmin();
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("current-pass", "stored-hash")).thenReturn(true);
    when(passwordEncoder.encode("new-password-123")).thenReturn("new-encoded-hash");

    authService.changePassword("admin@kizuna.test", "current-pass", "new-password-123");

    assertThat(user.getPassword()).isEqualTo("new-encoded-hash");
    verify(userRepository).save(user);
    verify(credentialOperations).changePassword(user, "new-encoded-hash");
  }

  /**
   * ドメイン不変条件が禁じる「資格情報を持つ SERVICE」を mock で組む。真の経路では email が null で引けず種別の守衛に
   * 到達しないため、二重化した第二の防線を単独で突くには mock しかない（テストを通すために不変条件を緩めない）。
   */
  private static User serviceIdentity() {
    User user = mock(User.class);
    when(user.getUserType()).thenReturn(UserType.SERVICE);
    return user;
  }

  @Test
  void issueTokenFor_disabledUser_isRejectedWithoutIssuingToken() {
    User user = hqAdmin();
    user.stop();

    assertThatThrownBy(() -> authService.issueTokenFor(user)).isInstanceOf(DisabledException.class);

    verify(jwtIssuer, never()).issue(any(), any());
  }

  @Test
  void login_disabledPrincipal_isRejectedWithoutIssuingToken() {
    User user = hqAdmin();
    user.stop();
    stubSuccessfulAuthentication("admin@kizuna.test", "pass", user);

    assertThatThrownBy(() -> authService.login("admin@kizuna.test", "pass"))
        .isInstanceOf(DisabledException.class);
    verify(jwtIssuer, never()).issue(any(), any());
  }

  @Test
  void login_serviceIdentity_isRejectedWithoutIssuingToken() {
    stubSuccessfulAuthentication("svc@kizuna.test", "pass", serviceIdentity());

    assertThatThrownBy(() -> authService.login("svc@kizuna.test", "pass"))
        .isInstanceOf(ServiceIdentityLoginException.class)
        // 観測面を資格情報の不一致に揃える継承。外れると応答が種別の存在オラクルになる。
        .isInstanceOf(BadCredentialsException.class);

    verify(jwtIssuer, never()).issue(anyString(), any());
  }
}
