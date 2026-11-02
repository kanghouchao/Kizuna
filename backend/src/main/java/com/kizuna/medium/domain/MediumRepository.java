package com.kizuna.medium.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.Collection;
import java.util.List;

public interface MediumRepository extends StoreScopedRepository<Medium> {
  List<Medium> findAllByStoreIdAndIdIn(Long storeId, Collection<String> ids);

  boolean existsByStoreIdAndName(Long storeId, String name);

  List<Medium> findAllByStoreIdOrderByNameAscIdAsc(Long storeId);

  List<Medium> findAllByStoreIdAndStatusOrderByNameAscIdAsc(Long storeId, MediumStatus status);
}
