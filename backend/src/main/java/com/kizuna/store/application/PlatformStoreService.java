package com.kizuna.store.application;

import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.store.api.dto.PlatformStoreResponse;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台 principal（授権店舗集合）の店舗一覧ユースケース（#324 統一ログイン）。 StoreScope を解決できない場合は fail-closed に {@link
 * AccessDeniedException} を投げる。
 */
@Service
@RequiredArgsConstructor
public class PlatformStoreService {

  private final StoreRepository storeRepository;

  /** 授権店舗を id 昇順で返す。ALL_STORES は全店、SPECIFIC_STORES は授権集合のみ。 */
  @Transactional(readOnly = true)
  public List<PlatformStoreResponse> listAuthorizedStores() {
    StoreScope scope =
        StoreScope.fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    if (scope == null) {
      throw new AccessDeniedException("授権店舗集合を解決できません");
    }
    Iterable<Store> stores =
        scope.allStores()
            ? storeRepository.findAll()
            : storeRepository.findAllById(scope.storeIds());
    return StreamSupport.stream(stores.spliterator(), false)
        .sorted(Comparator.comparing(Store::getId))
        .map(store -> new PlatformStoreResponse(store.getId(), store.getName()))
        .toList();
  }
}
