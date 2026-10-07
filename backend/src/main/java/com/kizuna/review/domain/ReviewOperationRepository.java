package com.kizuna.review.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewOperationRepository extends JpaRepository<ReviewOperation, String> {
  Optional<ReviewOperation> findByActorIdAndDedupeKey(Long actorId, String key);
}
