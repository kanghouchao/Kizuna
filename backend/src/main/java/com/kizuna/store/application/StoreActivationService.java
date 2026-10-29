package com.kizuna.store.application;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 店長の明示操作で準備中の店舗を稼働中へ移す。 */
@Service
@RequiredArgsConstructor
public class StoreActivationService {

  private final StoreRepository storeRepository;
  private final CacheManager cacheManager;
  private final BusinessAudit businessAudit;

  /** 稼働中へ遷移させる。既に稼働中なら集約が 400 で拒む。 */
  @Transactional
  public void activate(long storeId) {
    Store store =
        storeRepository
            .findById(storeId)
            .orElseThrow(() -> new NotFoundException("店舗が見つかりません: " + storeId));
    var before =
        Map.of("status", store.getStatus().name(), "version", String.valueOf(store.getVersion()));
    store.activate();
    storeRepository.saveAndFlush(store);
    businessAudit.recordCurrent(
        storeId,
        "STORE_ACTIVATED",
        "STORE",
        String.valueOf(storeId),
        before,
        Map.of("status", store.getStatus().name(), "version", String.valueOf(store.getVersion())));
    evictDomainLookup(store.getDomain());
  }

  /**
   * ドメイン照会のキャッシュから当該店舗の写しを捨てる。{@code storeByDomain} が配るのは稼働状態を含む店舗の写しなので、
   * 遷移を反映しないと稼働中の店舗を準備中として配り続ける。落とすのは遷移した 1 店舗の鍵だけ。
   */
  private void evictDomainLookup(String domain) {
    Cache cache = cacheManager.getCache("storeByDomain");
    if (cache != null) {
      cache.evict(domain);
    }
  }
}
