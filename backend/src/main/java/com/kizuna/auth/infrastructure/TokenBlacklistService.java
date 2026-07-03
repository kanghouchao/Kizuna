package com.kizuna.auth.infrastructure;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * JWT を Redis のブラックリストに登録する（残存有効期間ぶんだけ保持）。 ログアウトとパスワード変更の両方から使う。判定は {@link JwtAuthenticationFilter}
 * が行う。
 */
@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

  /** Redis キーの接頭辞。判定側の {@link JwtAuthenticationFilter} と共有する。 */
  static final String KEY_PREFIX = "blacklist:tokens:";

  private final RedisTemplate<String, Object> redisTemplate;
  private final JwtUtil jwtUtil;

  /**
   * Authorization ヘッダ値（"Bearer xxx"）または生トークンをブラックリストへ登録する。 無効・期限切れトークンは黙って無視する。
   *
   * @param authHeaderOrToken Authorization ヘッダ値または生トークン（null 可）
   */
  public void blacklist(String authHeaderOrToken) {
    if (authHeaderOrToken == null) {
      return;
    }
    String token =
        authHeaderOrToken.startsWith("Bearer ")
            ? authHeaderOrToken.substring(7)
            : authHeaderOrToken;
    try {
      long exp = jwtUtil.getClaims(token).getExpiration().getTime();
      long ttl = Math.max(0, exp - System.currentTimeMillis());
      if (ttl > 0) {
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", ttl, TimeUnit.MILLISECONDS);
      }
    } catch (Exception e) {
      // トークンが無効または期限切れ — ブラックリスト不要
    }
  }
}
