package com.kizuna.review.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository
    extends JpaRepository<ReviewRecord, String>, JpaSpecificationExecutor<ReviewRecord> {
  Optional<ReviewDetailsView> findProjectedById(String id);

  List<ReviewPublicationView> findByIdInAndStatusAndPermissionStatus(
      Set<String> ids, ReviewValues.Status status, ReviewValues.PermissionStatus permissionStatus);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from ReviewRecord r where r.id = :id")
  Optional<ReviewRecord> lockById(String id);
}
