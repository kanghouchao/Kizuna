package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;
import java.time.Duration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * JWT ブラックリストの読み書き（Redis）。TTL は token 単位（{@link #blacklist(String)}）は実際の exp まで、ユーザー単位（{@link
 * #blacklistUser(String)}）は JWT 有効期間ぶん。書き込みはセッション失効、判定は認証フィルタから使う。
 */
@Component
public class TokenBlacklistService {

  private static final String KEY_PREFIX = "blacklist:tokens:";

  /** ユーザー単位ブラックリストの key 接頭辞。停止済みユーザーの全セッションを email 単位で一括失効させる。 */
  private static final String USER_KEY_PREFIX = "blacklist:users:";

  private final RedisTemplate<String, Object> redisTemplate;
  private final AppProperties appProperties;
  private final JwtDecoder expDecoder;

  public TokenBlacklistService(
      RedisTemplate<String, Object> redisTemplate, AppProperties appProperties) {
    this.redisTemplate = redisTemplate;
    this.appProperties = appProperties;
    // 主 JwtDecoder bean（JwtDecoderConfig）は本クラス（TokenBlacklistValidator 経由）に依存するため、
    // ここで注入すると循環参照になる。token の exp を読むためだけの decoder を自前で組み立てる
    // （issuer・ブラックリスト検証は不要 — 失効判定は blacklist() 自身が担う）。
    this.expDecoder =
        NimbusJwtDecoder.withSecretKey(HmacSecretKeyFactory.create(appProperties))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
  }

  /**
   * Authorization ヘッダ値（"Bearer xxx"）または生トークンをブラックリストへ登録する。
   *
   * <p>TTL は token 自身の exp まで（残存有効期間）。token を解析して実際の exp を読むため、運用中に app.jwt.expiration
   * を短縮しても、既発行の長寿命 token が固定 TTL より早くブラックリストから 消えて復活する fail-open は起きない。無効・期限切れの token は書き込みを省略する。
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
      long ttl =
          expDecoder.decode(token).getExpiresAt().toEpochMilli() - System.currentTimeMillis();
      if (ttl > 0) {
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", Duration.ofMillis(ttl));
      }
    } catch (JwtException e) {
      // 無効・期限切れトークンはブラックリスト不要
    }
  }

  /** 生トークンがブラックリスト登録済みかを返す。 */
  public boolean isBlacklisted(String token) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
  }

  /**
   * 指定ユーザーの発行済み JWT を email 単位で即時失効させる（停止時に使う）。
   *
   * <p>{@link #blacklist(String)} が無効・期限切れ token への書き込みを省略するのに対し、こちらは省略しない。 blacklist(String)
   * の省略は「対象 token がどのみち無効」という正常な業務ケースの最適化だが、こちらで同様に
   * 省略すると「停止したのにブラックリストが書かれない」というフェイルオープン（安全制御の静默失効）になって しまうため、性質が異なる。
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
