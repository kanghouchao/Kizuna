package com.kizuna.order.application;

import com.kizuna.shared.exception.ConflictException;
import java.util.Map;

public class OrderConfirmationConflict extends ConflictException {

  public OrderConfirmationConflict(String field) {
    this(field, "受注または採用条件が変更されています。再試算して変更内容を確認してください");
  }

  public OrderConfirmationConflict(String field, String message) {
    super(message, Map.of(field, "再試算して変更内容を確認してください"));
  }
}
