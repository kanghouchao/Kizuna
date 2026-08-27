package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * JWT ブラックリストの読み書き（Redis）。TTL は token 単位（{@link #blacklist(String)}）は実際の exp まで、ユーザー単位（{@link
 * #blacklistUser(String)}）とパスワード再設定（{@link #markPasswordReset(String)}）は JWT
 * 有効期間ぶん。書き込みはセッション失効、判定は認証フィルタから使う。
 */
@Component
public class TokenBlacklistService {

  private static final String KEY_PREFIX = "blacklist:tokens:";

  /** ユーザー単位ブラックリストの key 接頭辞。停止済みユーザーの全セッションを email 単位で一括失効させる。 */
  private static final String USER_KEY_PREFIX = "blacklist:users:";

  /** パスワード再設定の key 接頭辞。停止用の鍵と分けてあり、再開（{@link #clearUser}）では決して消さない。 */
  private static final String PASSWORD_RESET_KEY_PREFIX = "blacklist:password-reset:";

  /** 既存値以上のときだけ書く（単調書込み）。巻き戻さないことは Redis 側の原子性で保証する。 */
  private static final RedisScript<Long> MONOTONIC_MARK =
      RedisScript.of(
          "local cur = redis.call('GET', KEYS[1]) "
              + "if cur and tonumber(cur) >= tonumber(ARGV[1]) then return 0 end "
              + "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2]) "
              + "return 1",
          Long.class);

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

  /**
   * パスワード再設定の時刻を記録し、それ以前に発行された JWT を失効させる（再設定時に使う）。
   *
   * <p>停止用の鍵とは独立の鍵を使う。共用すると、無関係な再開操作 1 回（{@link #clearUser}）が再設定前のセッションまで蘇らせてしまう。
   *
   * <p>値は書込み時点（＝ commit 後）のエポック秒。TTL は {@link #blacklistUser} と同じ理由で JWT 有効期間ぶん取る —
   * その時間が経てば再設定前のトークンはどれも自身の期限切れで無効になっている。
   *
   * <p>書込みは Lua で単調化する。再設定が重なると commit 順と callback 実行順は一致せず、素の SET
   * では遅れた古い時刻が新しい境界を巻き戻し、両境界の間に発行されたトークンが復活し得る。
   */
  public void markPasswordReset(String email) {
    redisTemplate.execute(
        MONOTONIC_MARK,
        List.of(PASSWORD_RESET_KEY_PREFIX + email),
        String.valueOf(Instant.now().getEpochSecond()),
        String.valueOf(appProperties.getJwtExpiration()));
  }

  /** 記録済みのパスワード再設定時刻（エポック秒）を返す。記録が無ければ空。 */
  public Optional<Long> getPasswordResetAtSeconds(String email) {
    Object value = redisTemplate.opsForValue().get(PASSWORD_RESET_KEY_PREFIX + email);
    return value == null ? Optional.empty() : Optional.of(Long.parseLong(value.toString()));
  }
}
