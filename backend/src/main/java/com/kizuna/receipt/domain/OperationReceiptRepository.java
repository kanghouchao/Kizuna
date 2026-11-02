package com.kizuna.receipt.domain;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OperationReceiptRepository extends Repository<OperationReceipt, String> {
  OperationReceipt saveAndFlush(OperationReceipt receipt);

  Optional<OperationReceipt> findByStoreIdAndActorIdAndOperationKey(
      Long storeId, Long actorId, String operationKey);
}
