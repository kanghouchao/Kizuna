package com.kizuna.advertising.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdvertisingChangeRepository extends StoreScopedRepository<AdvertisingChange> {

  Page<AdvertisingChange> findByStoreIdAndMonth(Long storeId, String month, Pageable pageable);
}
