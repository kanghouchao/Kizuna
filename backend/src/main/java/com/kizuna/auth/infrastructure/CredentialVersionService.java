package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 資格情報の版の照合（ADR 0022）。DB が正本、Redis は read-through キャッシュ（TTL = JWT 有効期間）。 書き込みは増分の反映（{@link
 * #reflect}）も miss の埋め戻しも単調 — キャッシュが DB を超えることは無い。 DEL 方式は採らない: 増分前に DB から読んだ旧版が DEL
 * の後に埋め戻される競合で、旧版が TTL まで居座る。 Redis 断連の例外はそのまま伝播し 500 になる（fail-closed）。
 */
@Component
@RequiredArgsConstructor
public class CredentialVersionService {

  /** トークン claim 名。発行（PlatformAuthService）と検証（CredentialVersionValidator）の単一の合意点。 */
  public static final String CLAIM = "credentialVersion";

  private static final String KEY_PREFIX = "credential-version:";

  /** 既存値以上のときだけ書く（単調書込み）。書き込みの到着順に依らず最大の版へ収束することを Redis 側の原子性で保証する。 */
  private static final RedisScript<Long> MONOTONIC_SET =
      RedisScript.of(
          "local cur = redis.call('GET', KEYS[1]) "
              + "if cur and tonumber(cur) >= tonumber(ARGV[1]) then return 0 end "
              + "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2]) "
              + "return 1",
          Long.class);

  private final RedisTemplate<String, Object> redisTemplate;
  private final AppProperties appProperties;
  private final PlatformUserRepository userRepository;

  /**
   * claim が運んできた版が現在の版と一致するかを返す。主体不在は不一致（fail-closed）。
   *
   * <p>キャッシュ不一致のうち claim &lt; キャッシュだけを即拒否できる（単調書込みによりキャッシュ ≤ DB が常に成り立ち、 claim の旧さが確定するため）。claim
   * &gt; キャッシュは「増分は確定したが commit 後の反映が失われた」直後の正当な 新トークンでありうるので、miss と同様に正本へ問い合わせて埋め戻して、DB
   * の現在値と相等比較する。
   */
  public boolean isCurrent(String email, long claimedVersion) {
    Object cached = redisTemplate.opsForValue().get(KEY_PREFIX + email);
    if (cached != null) {
      long cachedVersion = Long.parseLong(cached.toString());
      if (claimedVersion == cachedVersion) {
        return true;
      }
      if (claimedVersion < cachedVersion) {
        return false;
      }
    }
    return userRepository
        .findCredentialVersionByEmail(email)
        .map(
            current -> {
              reflect(email, current);
              return claimedVersion == current;
            })
        .orElse(false);
  }

  /** 確定済みの版をキャッシュへ単調に反映する（増分の commit 後と miss の埋め戻しが共用する唯一の書き込み口）。 */
  public void reflect(String email, long version) {
    redisTemplate.execute(
        MONOTONIC_SET,
        List.of(KEY_PREFIX + email),
        String.valueOf(version),
        String.valueOf(appProperties.getJwtExpiration()));
  }
}
