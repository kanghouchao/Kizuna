package com.kizuna.remuneration.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RemunerationChangeRepository extends StoreScopedRepository<RemunerationChange> {

  Page<RemunerationChange> findByStoreIdAndResourceTypeAndResourceId(
      Long storeId, String resourceType, String resourceId, Pageable pageable);
}
