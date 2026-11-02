package com.kizuna.receipt.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.Optional;

public interface OperationReceiptRepository extends StoreScopedRepository<OperationReceipt> {
  Optional<OperationReceipt> findByStoreIdAndActorIdAndOperationKey(
      Long storeId, Long actorId, String operationKey);
}
