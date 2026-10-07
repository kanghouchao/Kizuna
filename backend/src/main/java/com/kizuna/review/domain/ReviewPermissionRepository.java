package com.kizuna.review.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewPermissionRepository extends JpaRepository<ReviewPermission, String> {
  Optional<ReviewPermission> findByReviewId(String reviewId);

  Optional<ReviewPermissionView> findProjectedByReviewId(String reviewId);
}
