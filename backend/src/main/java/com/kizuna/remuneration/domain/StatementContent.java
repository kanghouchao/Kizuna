package com.kizuna.remuneration.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * 一店舗・一営業日の報酬明細の内容（三項の額と根拠）。承諾の写しと内容の要約の正本。
 *
 * <p>要約は正規形の SHA-256。正規形は入力の並びに依らず、額が同じでも根拠が違えば別の値になる。 根拠には識別子と額だけを載せ、理由文やサービス名のような自由記述は載せない。
 */
public record StatementContent(
    long orderAmount,
    Long guaranteeAmount,
    long bonusAmount,
    List<OrderBasis> orders,
    String closedDuration,
    DailyGuarantee.Status guaranteeStatus,
    GuaranteeState guaranteeState,
    Long dailyAmount,
    List<BonusBasis> bonuses) {
  public record OrderBasis(String orderId, long accruedRemuneration, boolean invalidated) {
    public long effectiveAmount() {
      return invalidated ? 0 : accruedRemuneration;
    }
  }

  public record BonusBasis(String bonusId, long amount) {}

  public StatementContent {
    orders = List.copyOf(orders);
    bonuses = List.copyOf(bonuses);
  }

  public NotAcceptableReason notAcceptableReason(boolean businessDayEnded) {
    if (!businessDayEnded) return NotAcceptableReason.BUSINESS_DAY_NOT_ENDED;
    if (guaranteeStatus == DailyGuarantee.Status.PENDING_ATTENDANCE
        || guaranteeStatus == DailyGuarantee.Status.NOT_CONFIGURED)
      return NotAcceptableReason.GUARANTEE_UNDETERMINED;
    if (orderAmount == 0 && guaranteeAmount == 0 && bonusAmount == 0)
      return NotAcceptableReason.ALL_ZERO;
    return null;
  }

  public String canonical() {
    var text = new StringBuilder("v1\n");
    line(text, "order_amount", orderAmount);
    line(text, "guarantee_amount", guaranteeAmount);
    line(text, "bonus_amount", bonusAmount);
    line(text, "closed_duration", closedDuration);
    line(text, "guarantee_status", guaranteeStatus);
    line(text, "guarantee_state", guaranteeState);
    line(text, "daily_amount", dailyAmount);
    orders.stream()
        .sorted(Comparator.comparing(OrderBasis::orderId))
        .forEach(
            o ->
                line(
                    text,
                    "order",
                    o.orderId()
                        + ","
                        + o.accruedRemuneration()
                        + ","
                        + (o.invalidated() ? "invalidated" : "valid")));
    bonuses.stream()
        .sorted(Comparator.comparing(BonusBasis::bonusId))
        .forEach(b -> line(text, "bonus", b.bonusId() + "," + b.amount()));
    return text.toString();
  }

  public String digest() {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void line(StringBuilder text, String key, Object value) {
    text.append(key).append('=').append(value == null ? "" : value).append('\n');
  }
}
