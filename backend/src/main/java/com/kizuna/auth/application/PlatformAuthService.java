package com.kizuna.auth.application;

import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.JwtUtil;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 */
@Service
public class PlatformAuthService {

  private static final String INVALID_CREDENTIALS_MESSAGE = "メールアドレスまたはパスワードが正しくありません";

  private final PlatformUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;

  /** メール不存在時のタイミング均一化に用いるダミー bcrypt ハッシュ（秘密値ではない固定文字列を構築時に符号化）。 */
  private final String userNotFoundEncodedPassword;

  public PlatformAuthService(
      PlatformUserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
    this.userNotFoundEncodedPassword = passwordEncoder.encode("userNotFoundPassword");
  }

  @Transactional(readOnly = true)
  public Token login(String email, String password) {
    PlatformUser user = userRepository.findByEmail(email).orElse(null);
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

    Map<String, Object> claims = new HashMap<>();
    claims.put("authorities", List.of("ROLE_" + user.getRole().name()));
    claims.put("role", user.getRole().name());
    claims.put("storeScopeType", user.getStoreScopeType().name());
    claims.put("storeIds", new ArrayList<>(user.getStoreIds()));
    return jwtUtil.generateToken(user.getEmail(), JwtUtil.ISSUER_PLATFORM, claims);
  }
}
