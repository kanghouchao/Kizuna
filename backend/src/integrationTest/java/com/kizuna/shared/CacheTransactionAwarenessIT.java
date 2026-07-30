package com.kizuna.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;

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
}
