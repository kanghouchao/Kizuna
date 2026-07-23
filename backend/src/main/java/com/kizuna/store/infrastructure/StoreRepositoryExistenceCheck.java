package com.kizuna.store.infrastructure;

import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.domain.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link StoreExistenceCheck} の store モジュール実装。StoreRepository の存在確認へ委譲する。 */
@Component
@RequiredArgsConstructor
class StoreRepositoryExistenceCheck implements StoreExistenceCheck {

  private final StoreRepository storeRepository;

  @Override
  public boolean exists(long storeId) {
    return storeRepository.existsById(storeId);
  }
}
