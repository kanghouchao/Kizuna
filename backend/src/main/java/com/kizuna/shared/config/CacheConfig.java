package com.kizuna.shared.config;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** キャッシュ設定クラス。実体は Redis（{@code spring.cache.type: redis}）。 */
@Configuration
@EnableCaching
public class CacheConfig {

  /**
   * キャッシュへの書き込みと失効を事務のコミット後まで遅らせる。
   *
   * <p>既定では {@code @CacheEvict} はメソッド復帰時＝コミット前に走る。失効からコミットまでの間に読んだ並行要求は
   * 更新前の行を読むため、失効させたばかりのキーへ陳腐な値を載せ直し、それが TTL まで配られてしまう。コミット後に失効させれば
   * この窓が閉じる。ロールバック時は行が変わっていないので、失効が走らないのが正しい。
   */
  @Bean
  RedisCacheManagerBuilderCustomizer transactionAwareCacheManagerCustomizer() {
    return builder -> builder.transactionAware();
  }

  /**
   * キャッシュ書き込み（put / evict / clear）を即時＝同期にする。
   *
   * <p>Spring Data Redis 4 の既定は Lettuce 環境で非同期 fire-and-forget：{@code @CacheEvict} の失効が Redis
   * へ届くのを待たずに応答が返るため、「更新の成功応答を受けてから照会する」クライアントでも失効前の 値を読める競合窓が開く（失効の read-your-writes
   * が壊れる）。書き込みを同期へ戻してこの窓を閉じる。
   *
   * <p>{@code immediateWrites(true)} 以外の構成（非ロック・KEYS バッチ・統計なし）は既定 writer と同一。 配線は {@code
   * CacheTransactionAwarenessIT} が固定する。
   */
  @Bean
  RedisCacheManagerBuilderCustomizer immediateCacheWritesCustomizer(
      RedisConnectionFactory connectionFactory) {
    return builder ->
        builder.cacheWriter(
            RedisCacheWriter.create(
                connectionFactory, configurer -> configurer.immediateWrites(true)));
  }
}
