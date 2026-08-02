package com.kizuna.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * キャッシュが事務対応で組み上がっていることを固定する IT。
 *
 * <p>この装飾が外れると {@code @CacheEvict} はコミット前に走り、失効からコミットまでの間に読んだ並行要求が 陳腐な値を載せ直せてしまう。装飾自体の意味（失効を
 * afterCommit へ回す）は Spring の {@link TransactionAwareCacheDecorator} が担うため、ここで固定するのは配線が実際に効いていることだけ。
 */
@SpringBootTest
class CacheTransactionAwarenessIT {

  @Autowired private CacheManager cacheManager;

  @Test
  @DisplayName("キャッシュは事務対応で装飾され、失効がコミット後に回ること")
  void cachesAreTransactionAware() {
    assertThat(cacheManager.getCache("storeByDomain"))
        .as("公開ドメイン照会のキャッシュ")
        .isInstanceOf(TransactionAwareCacheDecorator.class);
    assertThat(cacheManager.getCache("systemConfigValues"))
        .as("システム設定のキャッシュ（装飾は CacheManager 単位なので全キャッシュに効く）")
        .isInstanceOf(TransactionAwareCacheDecorator.class);
  }

  // Spring Data Redis 4 の RedisCacheWriter は Lettuce 環境で書き込み（put/evict/clear）を
  // 非同期 fire-and-forget で行うのが既定。失効の完了を待たずに応答が返るため、
  // 「更新 204 → 公開照会」の順で叩いても照会が失効前の値を読める競合窓が開く。
  // immediateWrites の配線が外れると全 @CacheEvict がこの競合に戻るので、内部フラグで固定する。
  // （競合自体は負荷依存で決定的に再現できないため、断言面は挙動でなく配線に置く）
  @Test
  @DisplayName("キャッシュ書き込みは即時（同期）であること — 失効完了前に応答が返らない")
  void cacheWritesAreImmediate() {
    assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
    Object writer = ReflectionTestUtils.getField(cacheManager, "cacheWriter");
    assertThat(writer).as("既定の DefaultRedisCacheWriter 構成であること").isNotNull();
    assertThat(ReflectionTestUtils.getField(writer, "asynchronousWrites"))
        .as("非同期書き込みが無効化されていること（immediateWrites）")
        .isEqualTo(false);
  }
}
