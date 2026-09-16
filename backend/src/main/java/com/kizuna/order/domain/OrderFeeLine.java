package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/** 受注が採用した費用・時間・固定報酬の一行。 */
@Entity
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Table(name = "t_order_fee_lines")
@Getter
@NoArgsConstructor
public class OrderFeeLine extends StoreScopedEntity {
  @Column(name = "order_id", insertable = false, updatable = false)
  private String orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 30)
  private OrderFeeLineKind kind;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(nullable = false)
  private Integer amount;

  private Integer durationMinutes;

  @Column(nullable = false)
  private int remuneration;

  @Embedded private OrderServiceAdoption adoption;

  public static OrderFeeLine of(OrderFeeLineKind kind, String name, int amount) {
    return from(new OrderFeeLineDraft(kind, name, amount));
  }

  static OrderFeeLine specialService(SpecialServiceSnapshot snapshot) {
    var line = of(OrderFeeLineKind.SPECIAL_SERVICE, snapshot.name(), snapshot.price());
    line.applySpecialService(snapshot);
    return line;
  }

  void applySpecialService(SpecialServiceSnapshot snapshot) {
    adoption =
        new OrderServiceAdoption(
            snapshot.serviceId(),
            snapshot.revisionId(),
            snapshot.revisionNumber(),
            snapshot.adoptionBasis(),
            snapshot.adoptedAt());
    name = snapshot.name();
    amount = snapshot.price();
    remuneration = snapshot.remuneration();
  }

  public String getServiceId() {
    return adoption == null ? null : adoption.serviceId();
  }

  static OrderFeeLine from(OrderFeeLineDraft draft) {
    var kind = draft.kind();
    if (kind == null
        || draft.name() == null
        || draft.name().isBlank()
        || draft.name().length() > 255)
      throw new InvalidOrderFeeLineException("明細の種別と255文字以内の名称は必須です");
    if (!kind.allows(draft.amount())) throw new InvalidOrderFeeLineException("明細の金額の符号が正しくありません");
    boolean timed = kind == OrderFeeLineKind.EXTENSION || kind == OrderFeeLineKind.BASE_COURSE;
    boolean configured = kind == OrderFeeLineKind.SURCHARGE || kind == OrderFeeLineKind.BASE_COURSE;
    if (timed
        ? draft.durationMinutes() == null || draft.durationMinutes() <= 0
        : draft.durationMinutes() != null)
      throw new InvalidOrderFeeLineException("コースと延長は正の整数分で指定してください");
    if (configured != (draft.adoption() != null))
      throw new InvalidOrderFeeLineException("コースと加算は設定から選択してください");
    if (draft.remuneration() < 0
        || ((timed || configured)
            ? draft.remuneration() > draft.amount()
            : draft.remuneration() != 0))
      throw new InvalidOrderFeeLineException("固定報酬は0以上かつ顧客費用以下です");
    if (kind == OrderFeeLineKind.DISCOUNT && draft.amount() >= 0)
      throw new InvalidOrderFeeLineException("割引は正の整数円で指定してください");
    var line = new OrderFeeLine();
    line.kind = kind;
    line.name = draft.name();
    line.amount = draft.amount();
    line.durationMinutes = draft.durationMinutes();
    line.remuneration = draft.remuneration();
    line.adoption = draft.adoption();
    return line;
  }

  static OrderFeeLine course(OrderCourse course) {
    return from(
        new OrderFeeLineDraft(
            null,
            OrderFeeLineKind.BASE_COURSE,
            course.name(),
            course.price(),
            course.durationMinutes(),
            course.remuneration(),
            OrderServiceAdoption.of(course)));
  }

  void applyCourse(OrderCourse course) {
    var next = course(course);
    this.name = next.name;
    this.amount = next.amount;
    this.durationMinutes = next.durationMinutes;
    this.remuneration = next.remuneration;
    this.adoption = next.adoption;
  }

  void reselectSurcharge(OrderFeeLineDraft draft) {
    var next = from(draft);
    if (kind != OrderFeeLineKind.SURCHARGE
        || next.kind != OrderFeeLineKind.SURCHARGE
        || !adoption.serviceId().equals(next.adoption.serviceId()))
      throw new InvalidOrderFeeLineException("同じ加算の採用条件だけを更新できます");
    this.name = next.name;
    this.amount = next.amount;
    this.remuneration = next.remuneration;
    this.adoption = next.adoption;
  }

  void attachStore(Long storeId) {
    setStoreId(storeId);
  }
}
