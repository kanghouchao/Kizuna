package com.kizuna.shared.config;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
