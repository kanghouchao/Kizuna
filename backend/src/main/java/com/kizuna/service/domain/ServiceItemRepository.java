package com.kizuna.service.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ServiceItemRepository
    extends StoreScopedRepository<ServiceItem>, JpaSpecificationExecutor<ServiceItem> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from ServiceItem s where s.id = :id and s.storeId = :storeId")
  Optional<ServiceItem> findByIdAndStoreIdForUpdate(String id, Long storeId);
}
