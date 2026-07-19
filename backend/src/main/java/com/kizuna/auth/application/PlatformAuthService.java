package com.kizuna.auth.application;

import com.kizuna.auth.api.dto.PlatformMeResponse;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.JwtUtil;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.domain.Capability;
import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.UserType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 統一（プラットフォーム）ログイン。既存の central/tenant 二軌のコードパスに触れないため AuthenticationManager を経由せず、findByEmail +
 * enabled 判定 + パスワード照合を自前で行う。判定順は DaoAuthenticationProvider に倣い enabled を先行させ、無効化ユーザーはパスワードの正誤に関わらず
 * {@link DisabledException} を投げる（無効化アカウントでのパスワード正誤オラクルを塞ぐ）。 メール不存在時は既知メール（誤パスワード）との応答時間差を無くすため、ダミーの
 * bcrypt 照合を 1 回行ってからパスワード不一致と同一メッセージの {@link BadCredentialsException} を投げる（列挙耐性: メッセージ均一 +
 * タイミング均一。 DaoAuthenticationProvider.mitigateAgainstTimingAttack と同趣旨）。いずれの例外も {@code
 * AuthenticationException} 系のため 401 で応答される。
 *
 * <p>authorities の発行（#382 / #398）: STAFF は保持束の能力並集を {@code PERM_} 形式で発行し、CAST / MEMBER は本人種別標識
 * {@code ROLE_CAST} / {@code ROLE_MEMBER} のみを発行する。授権変更は次回ログインから反映される（会話失効なし — #325 既定）。
 */
@Service
public class PlatformAuthService {

  private static final String INVALID_CREDENTIALS_MESSAGE = "メールアドレスまたはパスワードが正しくありません";

  private final PlatformUserRepository userRepository;
  private final CapabilityBundleRepository capabilityBundleRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final AuthSessionService authSessionService;

  /** メール不存在時のタイミング均一化に用いるダミー bcrypt ハッシュ（秘密値ではない固定文字列を構築時に符号化）。 */
  private final String userNotFoundEncodedPassword;

  public PlatformAuthService(
      PlatformUserRepository userRepository,
      CapabilityBundleRepository capabilityBundleRepository,
      PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil,
      AuthSessionService authSessionService) {
    this.userRepository = userRepository;
    this.capabilityBundleRepository = capabilityBundleRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
    this.authSessionService = authSessionService;
    this.userNotFoundEncodedPassword = passwordEncoder.encode("userNotFoundPassword");
  }

  @Transactional(readOnly = true)
  public Token login(String email, String password) {
    // 平台側 email は小文字で保存されるため（保存済みシードは全て小文字）、照合前に正規化する。
    String normalizedEmail = email.toLowerCase(Locale.ROOT);
    PlatformUser user = userRepository.findByEmail(normalizedEmail).orElse(null);
    if (user == null) {
      // メール不存在でも既知メール（誤パスワード）と同等の時間を要するようダミー照合を実行する。
      passwordEncoder.matches(password, userNotFoundEncodedPassword);
      throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
    }
    if (!user.getEnabled()) {
      throw new DisabledException("アカウントが無効化されています");
    }
    if (!passwordEncoder.matches(password, user.getPassword())) {
      throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
    }

    Set<Capability> capabilities = capabilitiesOf(user);
    Map<String, Object> claims = new HashMap<>();
    claims.put("authorities", buildAuthorities(user, capabilities));
    claims.put("userType", user.getUserType().name());
    // 店舗文脈（X-Tenant-ID）を確立できるか。STORE コンソール能力の保持者のみ true（SHARED は含めない —
    // HQ の跨店参照は店舗文脈を確立せず、僭称ヘッダは従来どおり 403）。TenantIdInterceptor が消費する。
    claims.put("storeBridge", hasStoreConsole(capabilities));
    claims.put("storeScopeType", user.getStoreScopeType().name());
    claims.put("storeIds", new ArrayList<>(user.getStoreIds()));
    return jwtUtil.generateToken(user.getEmail(), JwtUtil.ISSUER_PLATFORM, claims);
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
        userRepository.findByEmail(email).orElseThrow(() -> new ServiceException("ユーザーが見つかりません"));
    user.updateDisplayName(displayName);
    userRepository.save(user);
    return toMeResponse(user);
  }

  /** パスワード変更。成功時は現在のセッションを失効させる（要再ログイン）。 */
  @Transactional
  public void changePassword(
      String email, String currentPassword, String newPassword, String currentToken) {
    PlatformUser user =
        userRepository.findByEmail(email).orElseThrow(() -> new ServiceException("ユーザーが見つかりません"));
    if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
      throw new ServiceException("現在のパスワードが正しくありません");
    }
    user.changePassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);
    authSessionService.invalidate(currentToken);
  }

  private PlatformMeResponse toMeResponse(PlatformUser user) {
    Set<Capability> capabilities = capabilitiesOf(user);
    return new PlatformMeResponse(
        user.getEmail(),
        user.getDisplayName(),
        user.getUserType().name(),
        capabilities.stream().map(Enum::name).sorted().toList(),
        consoleOf(user, capabilities),
        user.getStoreScopeType().name(),
        user.getStoreIds().stream().sorted().toList());
  }

  /** 保持束の能力並集。STAFF 以外は能力を持たない（本人種別 — #320 既定）。 */
  private Set<Capability> capabilitiesOf(PlatformUser user) {
    if (user.getUserType() != UserType.STAFF) {
      return Set.of();
    }
    return capabilityBundleRepository.findAllById(user.getBundleIds()).stream()
        .flatMap(bundle -> bundle.getCapabilities().stream())
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Capability.class)));
  }

  private static List<String> buildAuthorities(PlatformUser user, Set<Capability> capabilities) {
    return switch (user.getUserType()) {
      case STAFF -> capabilities.stream().map(Capability::authority).sorted().toList();
      case CAST -> List.of("ROLE_CAST");
      case MEMBER -> List.of("ROLE_MEMBER");
    };
  }

  private static boolean hasStoreConsole(Set<Capability> capabilities) {
    // 標識能力はコンソール入場・店舗文脈確立の資格にしない（PR #411 codex 指摘）。STORE_MENU_VIEW 単独では
    // storeBridge も店舗コンソール着地も許さず、実運用の STORE 能力（STORE_MENU_VIEW 以外）を要求する。
    return capabilities.stream()
        .anyMatch(
            capability ->
                capability.getConsole() == Capability.Console.STORE
                    && capability != Capability.STORE_MENU_VIEW);
  }

  /** ログイン後の着地先。CENTRAL 能力保持者は central 優先（兼務者のコンソール切替導線は別票）。 */
  private static String consoleOf(PlatformUser user, Set<Capability> capabilities) {
    if (user.getUserType() != UserType.STAFF) {
      return "none";
    }
    boolean central =
        capabilities.stream()
            .anyMatch(capability -> capability.getConsole() == Capability.Console.CENTRAL);
    if (central) {
      return "central";
    }
    return hasStoreConsole(capabilities) ? "store" : "none";
  }
}
