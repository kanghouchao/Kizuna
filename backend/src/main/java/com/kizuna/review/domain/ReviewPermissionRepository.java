package com.kizuna.review.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.Optional;

public interface ReviewPermissionRepository extends StoreScopedRepository<ReviewPermission> {
  Optional<ReviewPermission> findByStoreIdAndReviewId(Long storeId, String reviewId);

  Optional<ReviewPermissionView> findProjectedByStoreIdAndReviewId(Long storeId, String reviewId);
}
