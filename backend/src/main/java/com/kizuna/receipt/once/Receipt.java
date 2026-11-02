package com.kizuna.receipt.once;

import java.io.Serializable;
import java.util.Map;

/** 応答に載せる受領。replayed は保存せず、今回の照会が既存の受領に命中したかで決まる。 */
public record Receipt(
    String id,
    String operation,
    String targetId,
    Map<String, Serializable> facts,
    boolean replayed) {

  public long longFact(String name) {
    // JSON から読み戻した小さな整数は Integer になるので、数値型を問わず受ける。
    if (facts == null || !(facts.get(name) instanceof Number value)) {
      throw new IllegalStateException("受領の事実が見つかりません");
    }
    return value.longValue();
  }

  public String textFact(String name) {
    if (facts == null || !(facts.get(name) instanceof String value)) {
      throw new IllegalStateException("受領の事実が見つかりません");
    }
    return value;
  }
}
