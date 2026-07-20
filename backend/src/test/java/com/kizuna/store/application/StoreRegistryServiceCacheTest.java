package com.kizuna.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * getByDomain のキャッシュ挙動（#207）。本番は Redis で cache-null-values=false のため、null（未登録ドメイン）を キャッシュに書こうとすると
 * IllegalArgumentException → 500 になる。同じ null 拒否設定の ConcurrentMapCacheManager でプロキシ経由の挙動を再現する。
 */
@SpringJUnitConfig(StoreRegistryServiceCacheTest.Config.class)
class StoreRegistryServiceCacheTest {

  @Configuration
  @EnableCaching
  static class Config {

    @Bean
    CacheManager cacheManager() {
      ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager();
      // 本番設定（REDIS_CACHE_NULL_VALUES:false）と同じ null 拒否契約
      manager.setAllowNullValues(false);
      return manager;
    }

    @Bean
    StoreRepository storeRepository() {
      return mock(StoreRepository.class);
    }

    @Bean
    StoreRegistryService storeRegistryService(StoreRepository storeRepository) {
      return new StoreRegistryService(storeRepository, mock(StoreProfileRepository.class));
    }
  }

  @Autowired private StoreRegistryService service;
  @Autowired private StoreRepository storeRepository;

  @Test
  @DisplayName("未登録ドメインの検索は例外にならず empty を返すこと（null をキャッシュに書かない）")
  void unknownDomainReturnsEmptyWithoutCachingNull() {
    when(storeRepository.findByDomain(anyString())).thenReturn(Optional.empty());

    assertThat(service.getByDomain("unknown.example.com")).isEmpty();
  }

  @Test
  @DisplayName("登録済みドメインの検索結果はキャッシュされ 2 回目は DB を叩かないこと")
  void knownDomainResultIsCached() {
    Store store = new Store();
    store.setId(1L);
    store.setName("店舗A");
    store.setDomain("a.example.com");
    store.setEmail("a@example.com");
    when(storeRepository.findByDomain("a.example.com")).thenReturn(Optional.of(store));

    assertThat(service.getByDomain("a.example.com")).isPresent();
    assertThat(service.getByDomain("a.example.com")).isPresent();

    verify(storeRepository, times(1)).findByDomain("a.example.com");
  }
}
