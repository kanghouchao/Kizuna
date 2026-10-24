package com.kizuna.storeprofile.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.Optional;

public interface StoreProfileRepository extends StoreScopedRepository<StoreProfile> {
  Optional<StoreProfile> findByStoreId(Long storeId);

  boolean existsByStoreId(Long storeId);
}
