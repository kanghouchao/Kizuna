package com.kizuna.receipt.once;

public record ReceiptKey(Long storeId, Long actorId, String operationKey) {
  public ReceiptKey {
    if (storeId == null
        || actorId == null
        || operationKey == null
        || operationKey.isBlank()
        || operationKey.length() > 120) {
      throw new IllegalArgumentException("受領キーの指定が不正です");
    }
  }
}
