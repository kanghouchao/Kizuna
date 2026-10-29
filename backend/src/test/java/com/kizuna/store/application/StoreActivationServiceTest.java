package com.kizuna.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.store.domain.StoreStatus;
import com.kizuna.user.application.BusinessAudit;
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
  @Mock private BusinessAudit businessAudit;
  @Mock private Cache cache;
  @InjectMocks private StoreActivationService storeActivationService;

  private Store preparingStore() {
    Store store = new Store("準備中店舗", "準", "preparing.kizuna.test");
    store.setId(STORE_ID);
    return store;
  }

  @Test
  @DisplayName("準備中の店舗を稼働中へ移し、ドメイン照会のキャッシュを落とすこと")
  void activate_preparingStore_transitionsAndEvictsCache() {
    Store store = preparingStore();
    when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
    when(cacheManager.getCache("storeByDomain")).thenReturn(cache);

    storeActivationService.activate(STORE_ID);

    assertThat(store.getStatus()).isEqualTo(StoreStatus.ACTIVE);
    verify(storeRepository).saveAndFlush(store);
    verify(cache).evict("preparing.kizuna.test");
  }

  @Test
  @DisplayName("既に稼働中の店舗は 400 で拒み、書き込まないこと")
  void activate_activeStore_isRejected() {
    Store store = preparingStore();
    store.activate();
    when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

    assertThatThrownBy(() -> storeActivationService.activate(STORE_ID))
        .isInstanceOf(ServiceException.class);

    verify(storeRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("存在しない店舗は 404")
  void activate_missingStore_isNotFound() {
    when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> storeActivationService.activate(STORE_ID))
        .isInstanceOf(NotFoundException.class);
  }
}
