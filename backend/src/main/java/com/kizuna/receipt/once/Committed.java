package com.kizuna.receipt.once;

import java.io.Serializable;
import java.util.Map;

/** 成立した操作が受領に残すもの。facts は操作自身が生んだ不変の事実（成立時の版・採番 ID・件数）だけで、対象の属性や応答の写しは置かない。 */
public record Committed(String operation, String targetId, Map<String, Serializable> facts) {
  public Committed {
    if (operation == null
        || operation.isBlank()
        || operation.length() > 80
        || targetId == null
        || targetId.isBlank()
        || targetId.length() > 100) {
      throw new IllegalArgumentException("受領の対象の指定が不正です");
    }
    facts = facts == null ? null : Map.copyOf(facts);
  }
}
