package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.Status;
import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository
    extends StoreScopedRepository<ReviewRecord>, JpaSpecificationExecutor<ReviewRecord> {
  Optional<ReviewDetailsView> findProjectedByIdAndStoreId(String id, Long storeId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from ReviewRecord r where r.id = :id and r.storeId = :storeId")
  Optional<ReviewRecord> findByIdAndStoreIdForUpdate(String id, Long storeId);

  /** {@link ReviewSummaryView#displayedAt} の公開設定以外の前提を、店舗を絞った問い合わせとして表す。 */
  static Specification<ReviewRecord> prerequisites(Long storeId) {
    return (root, q, cb) ->
        cb.and(
            cb.equal(root.get("storeId"), storeId),
            cb.equal(root.get("status"), Status.APPROVED),
            cb.equal(root.get("permissionStatus"), PermissionStatus.GRANTED));
  }
}
