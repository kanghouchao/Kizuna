package com.kizuna.order.domain;

/** 解決済みの採用条件。lineId は既存明細の維持だけを表す。 */
public record OrderFeeLineDraft(
    String lineId,
    OrderFeeLineKind kind,
    String name,
    int amount,
    Integer durationMinutes,
    int remuneration,
    OrderServiceAdoption adoption) {
  public OrderFeeLineDraft(OrderFeeLineKind kind, String name, int amount) {
    this(null, kind, name, amount, null, 0, null);
  }

  public static OrderFeeLineDraft extension(
      String name, int minutes, int amount, int remuneration) {
    return new OrderFeeLineDraft(
        null, OrderFeeLineKind.EXTENSION, name, amount, minutes, remuneration, null);
  }

  public static OrderFeeLineDraft of(OrderFeeLine line) {
    return new OrderFeeLineDraft(
        line.getId(),
        line.getKind(),
        line.getName(),
        line.getAmount(),
        line.getDurationMinutes(),
        line.getRemuneration(),
        line.getAdoption());
  }
}
