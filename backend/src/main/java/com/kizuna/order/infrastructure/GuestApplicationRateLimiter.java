package com.kizuna.order.infrastructure;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 公開店面のゲスト予約申請の流量制限（固定窓の計数）。匿名 POST に対する唯一の兜底で、これが無いと申請表への灌水と店舗の受付箱の淹没を止める機構が無い。
 *
 * <p>数える鍵は「店舗 × 発信元」。店舗ごとの総量では数えない — それは攻撃者が正当な来訪者の申請口を閉じられることを意味する。連絡先の次元も持たない
 * （番号は要求のたびに変えられるので攻撃を止める力が無く、同じ番号から複数人ぶん申し込む正当な利用を先に潰す）。
 *
 * <p>固定窓なので窓の境目をまたぐと短時間に上限の 2 倍まで通りうる。灌水を止める用途では許容し、滑走窓の複雑さは負わない。
 */
@Component
@RequiredArgsConstructor
public class GuestApplicationRateLimiter {

  private static final String KEY_PREFIX = "order:guest-application:";

  /** 数える窓の長さ。 */
  private static final Duration WINDOW = Duration.ofMinutes(10);

  /** 1 つの窓で受け付ける件数。日時違いの申し直しや家族ぶんの申し込みが窓に収まり、連投は止まる幅に採る。 */
  private static final int MAX_PER_WINDOW = 5;

  /** 計数と窓の期限付けを 1 つの原子操作にまとめる。増分と期限付けを別の往復に分けると、その間に鍵が失効した場合に 期限の無い鍵が生まれ、その発信元は窓が明けても永久に撥ねられ続ける。 */
  private static final RedisScript<Long> CONSUME =
      new DefaultRedisScript<>(
          """
          local count = redis.call('INCR', KEYS[1])
          if count == 1 then
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
          end
          return count
          """,
          Long.class);

  private final RedisTemplate<String, Object> redisTemplate;

  /**
   * 1 件ぶん消費し、窓の上限を超えていれば false を返す。
   *
   * <p>撥ねた要求も消費する。窓の中で撥ねられ続ける限り数え続けるのが固定窓の意味で、拒否を無償にすると上限に達した後の連投が計数を素通りする。
   *
   * <p><b>数えられなければ通さない</b>（fail-closed）。この経路は匿名 POST に対する唯一の兜底なので、計数が壊れたときに素通りさせると 兜底そのものが消える。Redis
   * へ届かない場合に例外が上がるのも同じ向きで、どちらも「通さない」に倒す。
   */
  public boolean tryConsume(Long storeId, String origin) {
    String key = KEY_PREFIX + storeId + ":" + origin;
    Long count = redisTemplate.execute(CONSUME, List.of(key), String.valueOf(WINDOW.toMillis()));
    return count != null && count <= MAX_PER_WINDOW;
  }
}
