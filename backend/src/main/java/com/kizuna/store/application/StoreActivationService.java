package com.kizuna.store.application;

import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.store.domain.StoreStatus;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 店舗コンソールへの着地を引き金に、準備中の店舗を稼働中へ移す。 */
@Service
@RequiredArgsConstructor
public class StoreActivationService {

  private final StoreRepository storeRepository;
  private final CacheManager cacheManager;

  /**
   * 稼働中であることを DB で確認できた店舗。
   *
   * <p>遷移は一方向で稼働中から戻らないため、一度確認できた店舗は以後照会しなくてよい。プロセス毎に持つので再起動で 失われるが、失われても店舗あたり 1
   * 回の照会が増えるだけで判定は変わらない。
   */
  private final Set<Long> activated = ConcurrentHashMap.newKeySet();

  /**
   * 店舗コンソールへの着地として稼働中へ遷移させる。既に稼働中なら何もしない。
   *
   * <p>覚えるのは「稼働中を読めた」場合だけで、遷移させた側は覚えない。遷移が確定するのは本メソッド復帰後のコミットであり、
   * 書けたつもりで覚えるとコミットに失敗した店舗をこのプロセスが二度と見に行かなくなる。次の要求が稼働中を読んだ時点で覚える。
   */
  @Transactional
  public void activateOnConsoleAccess(long storeId) {
    if (activated.contains(storeId)) {
      return;
    }
    Optional<Store> found = storeRepository.findById(storeId);
    if (found.isEmpty()) {
      return;
    }
    Store store = found.get();
    if (store.getStatus() == StoreStatus.ACTIVE) {
      activated.add(storeId);
      return;
    }
    store.activate();
    storeRepository.save(store);
    evictDomainLookup(store.getDomain());
  }

  /**
   * ドメイン照会のキャッシュから当該店舗の写しを捨てる。
   *
   * <p>{@code storeByDomain} が配るのは稼働状態を含む店舗の写しなので、遷移を反映しないと稼働中の店舗を準備中として 配り続ける。落とすのは遷移した 1
   * 店舗の鍵だけで、全件失効は使わない — 他店舗の写しを巻き添えにする理由がない。
   */
  private void evictDomainLookup(String domain) {
    Cache cache = cacheManager.getCache("storeByDomain");
    if (cache != null) {
      cache.evict(domain);
    }
  }
}
