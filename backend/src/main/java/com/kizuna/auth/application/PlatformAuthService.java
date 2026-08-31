package com.kizuna.auth.application;

import com.kizuna.auth.api.dto.PlatformMeResponse;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.PlatformJwtIssuer;
import com.kizuna.auth.infrastructure.PlatformUserDetails;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.EmergencyElevation;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserCredentialsChanged;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 統一（プラットフォーム）ログイン。認証判定は AuthenticationManager（DaoAuthenticationProvider + 自作
 * UserDetailsService）に委譲する。メール不存在・パスワード不一致は BadCredentialsException、無効化アカウントはパスワードの正誤に関わらず
 * DisabledException が投げられる（enabled 判定がパスワード照合に先行するため、無効化アカウントでのパスワード正誤オラクルを塞ぐ）。列挙耐性・タイミング均一化も
 * フレームワークの既定挙動が担う。いずれの例外も {@code AuthenticationException} 系のため 401 で応答される。
 *
 * <p>authorities の発行: STAFF は保持ロールの権限並集を {@code PERM_} 形式で発行し、CAST / MEMBER は本人種別標識 {@code
 * ROLE_CAST} / {@code ROLE_MEMBER} のみを発行する。授権変更は次回ログインから反映される（会話中は失効しない既定挙動）。
 */
@Service
@RequiredArgsConstructor
public class PlatformAuthService {

  private final PlatformUserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final PasswordEncoder passwordEncoder;
  private final PlatformJwtIssuer jwtIssuer;
  private final AuthenticationManager authenticationManager;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public Token login(String email, String password) {
    // 平台側 email は小文字で保存されるため（保存済みシードは全て小文字）、照合前に正規化する。
    String normalizedEmail = email.toLowerCase(Locale.ROOT);
    Authentication authentication =
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, password));
    // principal は認証成功時に自作 UserDetails が保持する PlatformUser（二次クエリを避ける）。
    PlatformUser user = ((PlatformUserDetails) authentication.getPrincipal()).getPlatformUser();

    return issueTokenFor(user);
  }

  /**
   * 認証済みの身分に対してトークンを発行する。パスワードログインと LINE ログインの双方から呼ばれ、 認証手段が変わっても claim の内容が一致することを構造的に保証する（認証手段ごとに
   * claim を組み立てると、片方だけ 権限が欠ける・過剰になる齟齬が静かに生まれる）。
   *
   * <p>呼び出し側は本人性の確認（パスワード照合・LINE の id_token 検証）を済ませていること。
   */
  @Transactional(readOnly = true)
  public Token issueTokenFor(PlatformUser user) {
    return jwtIssuer.issue(user.getEmail(), baseClaims(user, permissionsOf(user)));
  }

  /**
   * 緊急昇格中の身分に対してトークンを発行する。基底の claim は通常発行と同一の組み立てを通し、昇格が変える点だけを 上書きする。期限は記録の {@code expiresAt}
   * をそのまま渡す — 別々に 60 分を数えると監査で復元した区間と実効区間が 食い違う。資格情報の版は現在値のまま据え置き、撤回で版が進めば昇格トークンも同時に失効する（ADR 0022）。
   */
  @Transactional(readOnly = true)
  public Token issueElevatedTokenFor(PlatformUser user, EmergencyElevation elevation) {
    Set<PermissionCode> permissions = permissionsOf(user);
    Map<String, Object> claims = baseClaims(user, permissions);
    claims.put("authorities", elevatedAuthorities(user, permissions));
    claims.put("storeBridge", true);
    claims.put("storeScopeType", StoreScopeType.SPECIFIC_STORES.name());
    claims.put("storeIds", List.of(elevation.getTargetStoreId()));
    // 昇格中の操作を発動記録へ結び付けるための錨。今は誰も検証しないが、この claim が無いと
    // 昇格中に何をしたかを後から記録へ辿れない。
    claims.put("elevationId", elevation.getId());
    return jwtIssuer.issue(user.getEmail(), claims, elevation.getExpiresAt().toInstant());
  }

  /**
   * 全ての発行経路が共有する claim の組み立て。ここが唯一の組み立て点であることが、認証手段や発行経路が 増えても claim
   * の内容が食い違わないことの保証である（片方だけ権限が欠ける・過剰になる齟齬は静かに生まれる）。
   */
  private Map<String, Object> baseClaims(PlatformUser user, Set<PermissionCode> permissions) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("authorities", buildAuthorities(user, permissions));
    claims.put("userType", user.getUserType().name());
    // 店舗文脈（X-Store-ID）を確立できるか。STORE コンソール権限の保持者のみ true（SHARED は含めない —
    // HQ の跨店参照は店舗文脈を確立せず、僭称ヘッダは従来どおり 403）。StoreIdInterceptor が消費する。
    claims.put("storeBridge", hasStoreConsole(permissions));
    claims.put("storeScopeType", user.getStoreScopeType().name());
    claims.put("storeIds", new ArrayList<>(user.getStoreIds()));
    // 発行時点の資格情報の版。検証時に現在の版と相等比較され、パスワード変更・再設定・停止で
    // 即時に不一致となる（ADR 0022）。
    claims.put("credentialVersion", user.getCredentialVersion());
    return claims;
  }

  /**
   * 昇格中の authorities。本人の通常の授権に、店舗コンソールの全権限を重ねる。
   *
   * <p>{@link PermissionCode#grantsStoreConsole()} では組まない。あれは入場資格の述語で標識権限 {@code STORE_MENU_VIEW}
   * を除くため、それで組むと店舗コンソールのメニューが一行も出ない昇格になる。
   */
  private static List<String> elevatedAuthorities(
      PlatformUser user, Set<PermissionCode> permissions) {
    return Stream.concat(
            buildAuthorities(user, permissions).stream(),
            Arrays.stream(PermissionCode.values())
                .filter(code -> code.getConsole() == PermissionCode.Console.STORE)
                .map(PermissionCode::authority))
        .distinct()
        .sorted()
        .toList();
  }

  /** me 応答を返す（GET /platform/me）。ユーザー不在は空を返し、HTTP 表現は呼び出し側が決める。 */
  @Transactional(readOnly = true)
  public Optional<PlatformMeResponse> me(String email) {
    return userRepository.findByEmail(email).map(this::toMeResponse);
  }

  /** 自己プロフィール（表示名）を更新し、更新後の me レスポンスを返す。 */
  @Transactional
  public PlatformMeResponse updateMe(String email, String displayName) {
    PlatformUser user =
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
    PlatformUser user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
    if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
      throw new ServiceException("現在のパスワードが正しくありません");
    }
    user.changePassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);
    eventPublisher.publishEvent(
        new PlatformUserCredentialsChanged(user.getEmail(), user.getCredentialVersion()));
  }

  private PlatformMeResponse toMeResponse(PlatformUser user) {
    Set<PermissionCode> permissions = permissionsOf(user);
    return new PlatformMeResponse(
        user.getEmail(),
        user.getDisplayName(),
        user.getUserType().name(),
        permissions.stream().map(Enum::name).sorted().toList(),
        consoleOf(user, permissions),
        hasStoreConsole(permissions),
        user.getStoreScopeType().name(),
        user.getStoreIds().stream().sorted().toList(),
        user.getLineUserId() != null);
  }

  /**
   * 保持ロールの権限並集。STAFF 以外は権限を持たない（本人種別の既定）。
   *
   * <p>ロールは権限を id 集合で持つため、id→コード→enum の 2 段で解決する。目録行はコード側 enum の播種済み写像であり、写像できないコードは存在しない前提で {@code
   * valueOf} が fail-loud に落ちる。
   */
  private Set<PermissionCode> permissionsOf(PlatformUser user) {
    if (user.getUserType() != UserType.STAFF) {
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

  private static List<String> buildAuthorities(PlatformUser user, Set<PermissionCode> permissions) {
    return switch (user.getUserType()) {
      case STAFF -> permissions.stream().map(PermissionCode::authority).sorted().toList();
      case CAST -> List.of("ROLE_CAST");
      case MEMBER -> List.of("ROLE_MEMBER");
    };
  }

  /** 入場資格の述語は {@link PermissionCode#grantsStoreConsole()} が単源（付与時の検証と共有する）。 */
  private static boolean hasStoreConsole(Set<PermissionCode> permissions) {
    return permissions.stream().anyMatch(PermissionCode::grantsStoreConsole);
  }

  /** ログイン後の着地先。PLATFORM 権限保持者は platform 優先（兼務者のコンソール切替導線は別票）。 */
  private static String consoleOf(PlatformUser user, Set<PermissionCode> permissions) {
    if (user.getUserType() != UserType.STAFF) {
      return "none";
    }
    boolean platform =
        permissions.stream()
            .anyMatch(permission -> permission.getConsole() == PermissionCode.Console.PLATFORM);
    if (platform) {
      return "platform";
    }
    return hasStoreConsole(permissions) ? "store" : "none";
  }
}
