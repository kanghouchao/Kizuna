package com.kizuna.auth.application;

import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.JwtUtil;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 統一（プラットフォーム）ログイン。既存の central/tenant 二軌のコードパスに触れないため AuthenticationManager を経由せず、findByEmail +
 * パスワード照合 + enabled 判定を自前で行う。 メール不存在・パスワード不一致は同一メッセージの {@link
 * BadCredentialsException}（ユーザー列挙防止）、無効化ユーザーは {@link DisabledException}。いずれも {@code
 * AuthenticationException} 系のため 401 で応答される。
 */
@Service
@RequiredArgsConstructor
public class PlatformAuthService {

  private static final String INVALID_CREDENTIALS_MESSAGE = "メールアドレスまたはパスワードが正しくありません";

  private final PlatformUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;

  @Transactional(readOnly = true)
  public Token login(String email, String password) {
    PlatformUser user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));
    if (!passwordEncoder.matches(password, user.getPassword())) {
      throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
    }
    if (!user.getEnabled()) {
      throw new DisabledException("アカウントが無効化されています");
    }

    Map<String, Object> claims = new HashMap<>();
    claims.put("authorities", List.of("ROLE_" + user.getRole().name()));
    claims.put("role", user.getRole().name());
    claims.put("storeScopeType", user.getStoreScopeType().name());
    claims.put("storeIds", new ArrayList<>(user.getStoreIds()));
    return jwtUtil.generateToken(user.getEmail(), JwtUtil.ISSUER_PLATFORM, claims);
  }
}
