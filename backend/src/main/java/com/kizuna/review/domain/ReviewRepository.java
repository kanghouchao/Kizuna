package com.kizuna.review.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository
    extends StoreScopedRepository<ReviewRecord>, JpaSpecificationExecutor<ReviewRecord> {
  Optional<ReviewDetailsView> findProjectedByIdAndStoreId(String id, Long storeId);

  List<ReviewPublicationView> findByStoreIdAndIdInAndStatusAndPermissionStatus(
      Long storeId,
      Set<String> ids,
      ReviewValues.Status status,
      ReviewValues.PermissionStatus permissionStatus);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from ReviewRecord r where r.id = :id and r.storeId = :storeId")
  Optional<ReviewRecord> findByIdAndStoreIdForUpdate(String id, Long storeId);
}
