package com.kizuna.auth.application;

import com.kizuna.auth.api.dto.MeResponse;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.JwtIssuer;
import com.kizuna.auth.infrastructure.UserPrincipal;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.application.CredentialOperations;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 統一ログイン。認証判定は AuthenticationManager（DaoAuthenticationProvider + 自作
 * UserDetailsService）に委譲する。メール不存在・パスワード不一致は BadCredentialsException、無効化アカウントはパスワードの正誤に関わらず
 * DisabledException が投げられる（enabled 判定がパスワード照合に先行するため、無効化アカウントでのパスワード正誤オラクルを塞ぐ）。列挙耐性・タイミング均一化も
 * フレームワークの既定挙動が担う。いずれの例外も {@code AuthenticationException} 系のため 401 で応答される。
 *
 * <p>authorities の発行: ロールで授権される種別（STAFF / SERVICE）は保持ロールの権限並集を {@code PERM_} 形式で発行し、CAST / MEMBER
 * は本人種別標識 {@code ROLE_CAST} / {@code ROLE_MEMBER} のみを発行する。権限の変更は次回ログインから反映される（会話中は失効しない既定挙動）。 店舗集合は
 * claim に載せず要求ごとに DB から解決する（StoreScopeResolver）。SERVICE は共通の発行守衛が拒否するため、この発行に到達しない。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtIssuer jwtIssuer;
  private final AuthenticationManager authenticationManager;
  private final CredentialOperations credentialOperations;

  @Transactional(readOnly = true)
  public Token login(String email, String password) {
    // ユーザーの email は小文字で保存されるため（保存済みシードは全て小文字）、照合前に正規化する。
    String normalizedEmail = email.toLowerCase(Locale.ROOT);
    Authentication authentication =
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, password));
    // principal は認証成功時に自作 UserDetails が保持する User（二次クエリを避ける）。
    User user = ((UserPrincipal) authentication.getPrincipal()).getUser();

    return issueTokenFor(user);
  }

  /** 本人性を確認済みの身分に通常トークンを発行する。呼び出し側はパスワード照合や LINE の id_token 検証を済ませていること。 */
  @Transactional(readOnly = true)
  public Token issueTokenFor(User user) {
    return jwtIssuer.issue(user.getEmail(), baseClaims(user, permissionsOf(user)));
  }

  /**
   * 全ての発行経路が共有する適格性検査と claim の組み立て。停止中の身分と SERVICE をここで拒否し、 認証手段による検査漏れ・claim の齟齬を防ぐ。SERVICE
   * の資格情報を禁じるドメイン不変条件に加え、 発行時にも種別を検査して対話ログインを拒否する。
   */
  private Map<String, Object> baseClaims(User user, Set<PermissionCode> permissions) {
    if (user.getUserType() == UserType.SERVICE) {
      throw new ServiceIdentityLoginException("サービスIDは対話ログインできません");
    }
    if (!Boolean.TRUE.equals(user.getEnabled())) {
      throw new DisabledException("アカウントが無効化されています");
    }
    Map<String, Object> claims = new HashMap<>();
    claims.put("authorities", buildAuthorities(user, permissions));
    claims.put("userType", user.getUserType().name());
    // 発行時点の資格情報の版。検証時に現在の版と相等比較され、パスワード変更・再設定・停止で
    // 即時に不一致となる（ADR 0022）。
    claims.put("credentialVersion", user.getCredentialVersion());
    return claims;
  }

  /** me 応答を返す（GET /me）。ユーザー不在は空を返し、HTTP 表現は呼び出し側が決める。 */
  @Transactional(readOnly = true)
  public Optional<MeResponse> me(String email) {
    return userRepository.findByEmail(email).map(this::toMeResponse);
  }

  /** 自己プロフィール（表示名）を更新し、更新後の me レスポンスを返す。 */
  @Transactional
  public MeResponse updateMe(String email, String displayName) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
    user.updateDisplayName(displayName);
    userRepository.save(user);
    return toMeResponse(user);
  }

  /** パスワード変更。版の増分により当該トークンを含む全端末のセッションが失効する（要再ログイン、ADR 0022）。 */
  @Transactional
  public void changePassword(String email, String currentPassword, String newPassword) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
    if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
      throw new ServiceException("現在のパスワードが正しくありません");
    }
    credentialOperations.changePassword(user, passwordEncoder.encode(newPassword));
    userRepository.save(user);
  }

  private MeResponse toMeResponse(User user) {
    Set<PermissionCode> permissions = permissionsOf(user);
    return new MeResponse(
        user.getEmail(),
        user.getDisplayName(),
        user.getUserType().name(),
        permissions.stream().map(Enum::name).sorted().toList(),
        user.getLineUserId() != null);
  }

  /**
   * 保持ロールの権限並集。ロールで授権される種別（{@link UserType#holdsRoles()}）以外は権限を持たない。
   *
   * <p>ロールは権限を id 集合で持つため、id→コード→enum の 2 段で解決する。目録行はコード側 enum の播種済み写像であり、写像できないコードは存在しない前提で {@code
   * valueOf} が fail-loud に落ちる。
   */
  private Set<PermissionCode> permissionsOf(User user) {
    if (!user.getUserType().holdsRoles()) {
      return Set.of();
    }
    Set<Long> permissionIds =
        roleRepository.findAllById(user.getRoleIds()).stream()
            .flatMap(role -> role.getPermissionIds().stream())
            .collect(Collectors.toSet());
    return permissionRepository.findAllById(permissionIds).stream()
        .map(permission -> PermissionCode.valueOf(permission.getCode()))
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionCode.class)));
  }

  private static List<String> buildAuthorities(User user, Set<PermissionCode> permissions) {
    return switch (user.getUserType()) {
      case STAFF, SERVICE -> permissions.stream().map(PermissionCode::authority).sorted().toList();
      case CAST -> List.of("ROLE_CAST");
      case MEMBER -> List.of("ROLE_MEMBER");
    };
  }
}
