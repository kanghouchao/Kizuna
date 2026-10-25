package com.kizuna.review.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.Optional;

public interface ReviewOperationRepository extends StoreScopedRepository<ReviewOperation> {
  Optional<ReviewOperation> findByStoreIdAndActorIdAndDedupeKey(
      Long storeId, Long actorId, String key);
}
