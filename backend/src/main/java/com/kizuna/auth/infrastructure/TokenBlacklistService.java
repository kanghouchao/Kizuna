package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/** JWT ブラックリストの読み書き（Redis、残存有効期間ぶんだけ保持）。書き込みはセッション失効、判定は認証フィルタから使う。 */
@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

  private static final String KEY_PREFIX = "blacklist:tokens:";

  /** ユーザー単位ブラックリストの key 接頭辞。停止済みユーザーの全セッションを email 単位で一括失効させる（#403）。 */
  private static final String USER_KEY_PREFIX = "blacklist:users:";

  private final RedisTemplate<String, Object> redisTemplate;
  private final JwtUtil jwtUtil;
  private final AppProperties appProperties;

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
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", Duration.ofMillis(ttl));
      }
    } catch (Exception e) {
      // トークンが無効または期限切れ — ブラックリスト不要
    }
  }

  /** 生トークンがブラックリスト登録済みかを返す。 */
  public boolean isBlacklisted(String token) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
  }

  /**
   * 指定ユーザーの発行済み JWT を email 単位で即時失効させる（停止時に使う）。
   *
   * <p>{@link #blacklist(String)} が TTL が既に 0 以下（＝トークンが元々期限切れ）なら書き込みを省略するのに対し、こちらは省略しない。
   * blacklist(String) の省略は「対象トークンがどのみち無効」という正常な業務ケースの最適化だが、こちらで同様に省略すると
   * 「停止したのにブラックリストが書かれない」というフェイルオープン（安全制御の静默失効）になってしまうため、性質が異なる。
   *
   * <p>TTL は JWT 有効期間（app.jwt.expiration）と同じ長さを取る。その時間が経過すれば、停止前に発行された どのトークンも JWT
   * 自体の期限切れで自然に無効化されているため、ブラックリスト側もそれ以上保持する必要がない。 前提: 稼働中に APP_JWT_EXPIRATION_MS
   * を短く変更すると、変更前に発行された寿命の長いトークンがこのブラックリストの 期限切れ後に復活し得る（新しい設定値は新規発行トークンにしか効かない）。
   */
  public void blacklistUser(String email) {
    redisTemplate
        .opsForValue()
        .set(USER_KEY_PREFIX + email, "1", Duration.ofMillis(appProperties.getJwtExpiration()));
  }

  /** ユーザー単位ブラックリストを解除する（再開時に使う）。 */
  public void clearUser(String email) {
    redisTemplate.delete(USER_KEY_PREFIX + email);
  }

  /** 指定 email がユーザー単位ブラックリストに登録済みかを返す。 */
  public boolean isUserBlacklisted(String email) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(USER_KEY_PREFIX + email));
  }
}
