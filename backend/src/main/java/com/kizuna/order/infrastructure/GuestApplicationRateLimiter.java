package com.kizuna.order.infrastructure;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 公開店面のゲスト予約申請の流量制限（固定窓の計数）。匿名 POST に対する唯一の兜底で、これが無いと申請表への灌水と 店舗の受付箱の淹没を止める機構が他に無い。
 *
 * <p>数える鍵は「店舗 × 発信元」。店舗を鍵に含めるのは、ある店舗への集中が無関係な店舗の受付まで塞がないため。 逆に店舗ごとの総量では数えない —
 * それは攻撃者が正当な来訪者の申請口を閉じられることを意味する。
 *
 * <p>連絡先（電話番号）の次元は持たない。番号は要求のたびに変えられるので攻撃を止める力が無い一方、 同じ番号から複数人ぶんを申し込む正当な利用を先に潰す。
 */
@Component
@RequiredArgsConstructor
public class GuestApplicationRateLimiter {

  private static final String KEY_PREFIX = "order:guest-application:";

  /** 数える窓の長さ。 */
  private static final Duration WINDOW = Duration.ofMinutes(10);

  /** 1 つの窓で受け付ける件数。日時違いの申し直しや家族ぶんの申し込みが窓に収まり、連投は止まる幅に採る。 */
  private static final int MAX_PER_WINDOW = 5;

  private final RedisTemplate<String, Object> redisTemplate;

  /**
   * 1 件ぶん消費し、窓の上限を超えていれば false を返す。
   *
   * <p>撥ねた要求も消費する。窓の中で撥ねられ続ける限り数え続けるのが固定窓の意味で、 拒否を無償にすると上限に達した後の連投が計数を素通りする。
   */
  public boolean tryConsume(Long storeId, String origin) {
    String key = KEY_PREFIX + storeId + ":" + origin;
    // 窓は鍵の生成と同時に期限付きで開く（SET NX + 有効期限の 1 手）。数えてから期限を張る 2 手だと、
    // 間で取りこぼした鍵が期限なしで残り、その発信元は二度と申請できなくなる。
    redisTemplate.opsForValue().setIfAbsent(key, "0", WINDOW);
    // 増分は期限を触らないため、窓は最初の 1 件からの固定長のまま滑らない。
    Long count = redisTemplate.opsForValue().increment(key);
    return count == null || count <= MAX_PER_WINDOW;
  }
}
