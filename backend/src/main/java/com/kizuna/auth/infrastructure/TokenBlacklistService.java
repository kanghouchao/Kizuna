package com.kizuna.auth.infrastructure;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/** JWT ブラックリストの読み書き（Redis、残存有効期間ぶんだけ保持）。書き込みはセッション失効、判定は認証フィルタから使う。 */
@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

  private static final String KEY_PREFIX = "blacklist:tokens:";

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

  /** 生トークンがブラックリスト登録済みかを返す。 */
  public boolean isBlacklisted(String token) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
  }
}
