package com.kizuna.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.store.domain.StoreStatus;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreActivationServiceTest {

  private static final long STORE_ID = 7L;

  @Mock private StoreRepository storeRepository;
  @Mock private CacheManager cacheManager;
  @Mock private Cache cache;
  @InjectMocks private StoreActivationService storeActivationService;

  private Store preparingStore() {
    Store store = new Store("準備中店舗", "preparing.kizuna.test", null);
    store.setId(STORE_ID);
    return store;
  }

  private Store activeStore() {
    Store store = preparingStore();
    store.activate();
    return store;
  }

  @Test
  @DisplayName("準備中の店舗を稼働中へ移し、ドメイン照会のキャッシュを落とすこと")
  void activate_preparingStore_transitionsAndEvictsCache() {
    Store store = preparingStore();
    when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
    when(cacheManager.getCache("storeByDomain")).thenReturn(cache);

    storeActivationService.activateOnConsoleAccess(STORE_ID);

    assertThat(store.getStatus()).isEqualTo(StoreStatus.ACTIVE);
    verify(storeRepository).save(store);
    verify(cache).evict("preparing.kizuna.test");
  }

  @Test
  @DisplayName("既に稼働中の店舗には書き込まないこと")
  void activate_activeStore_doesNotWrite() {
    when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(activeStore()));

    storeActivationService.activateOnConsoleAccess(STORE_ID);

    verify(storeRepository, never()).save(any());
  }

  // 稼働中は DB で確認できた事実なので覚えてよい。2 回目以降は照会そのものが消える。
  @Test
  @DisplayName("稼働中を一度確認した店舗は二度と照会しないこと")
  void activate_activeStore_isNotQueriedTwice() {
    when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(activeStore()));

    storeActivationService.activateOnConsoleAccess(STORE_ID);
    storeActivationService.activateOnConsoleAccess(STORE_ID);

    verify(storeRepository, times(1)).findById(STORE_ID);
  }

  // 遷移が確定するのはコミット時。書けたつもりで覚えると、コミットに失敗した店舗を
  // このプロセスが二度と見に行かなくなる。
  @Test
  @DisplayName("遷移させた側は覚えず、次の要求で状態を読み直すこと")
  void activate_afterTransition_readsStateAgain() {
    when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(preparingStore()));
    when(cacheManager.getCache("storeByDomain")).thenReturn(cache);

    storeActivationService.activateOnConsoleAccess(STORE_ID);
    storeActivationService.activateOnConsoleAccess(STORE_ID);

    verify(storeRepository, times(2)).findById(STORE_ID);
  }

  @Test
  @DisplayName("存在しない店舗には何も書き込まないこと")
  void activate_missingStore_doesNothing() {
    when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

    storeActivationService.activateOnConsoleAccess(STORE_ID);

    verify(storeRepository, never()).save(any());
  }
}
