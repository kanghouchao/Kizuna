package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 資格情報の版は DB を正本とし、Redis は確定版を単調に保存する（TTL = JWT 有効期間）。コミット後の反映が失敗しても旧 JWT を許可しないよう、受理する版は毎回 DB
 * で照合する。キャッシュより旧い版は DB を読まず拒否できる。DB・Redis 障害はそのまま伝播する。
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

  /** claim が運ぶ版を確定済みの版と照合する。主体不在は拒否し、キャッシュの遅れだけを単調書込みで埋め戻す。 */
  public boolean isCurrent(String email, long claimedVersion) {
    Object cached = redisTemplate.opsForValue().get(KEY_PREFIX + email);
    Long cachedVersion = cached == null ? null : Long.parseLong(cached.toString());
    if (cachedVersion != null && claimedVersion < cachedVersion) {
      return false;
    }
    return userRepository
        .findCredentialVersionByEmail(email)
        .map(
            current -> {
              if (cachedVersion == null || cachedVersion < current) {
                reflect(email, current);
              }
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
