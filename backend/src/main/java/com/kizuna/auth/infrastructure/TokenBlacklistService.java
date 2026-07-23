package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/** JWT ブラックリストの読み書き（Redis、TTL は JWT 有効期間ぶん）。書き込みはセッション失効、判定は認証フィルタから使う。 */
@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

  private static final String KEY_PREFIX = "blacklist:tokens:";

  /** ユーザー単位ブラックリストの key 接頭辞。停止済みユーザーの全セッションを email 単位で一括失効させる。 */
  private static final String USER_KEY_PREFIX = "blacklist:users:";

  private final RedisTemplate<String, Object> redisTemplate;
  private final AppProperties appProperties;

  /**
   * Authorization ヘッダ値（"Bearer xxx"）または生トークンをブラックリストへ登録する。
   *
   * <p>TTL は JWT 有効期間（app.jwt.expiration）。token を解析せず固定 TTL を使うため、既に失効済みの token も書かれ得るが、自分の token
   * しか blacklist できず増幅はない。token が自然失効するまで保持すれば十分で、{@link #blacklistUser(String)} と同一基準。
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
    redisTemplate
        .opsForValue()
        .set(KEY_PREFIX + token, "1", Duration.ofMillis(appProperties.getJwtExpiration()));
  }

  /** 生トークンがブラックリスト登録済みかを返す。 */
  public boolean isBlacklisted(String token) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
  }

  /**
   * 指定ユーザーの発行済み JWT を email 単位で即時失効させる（停止時に使う）。
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
