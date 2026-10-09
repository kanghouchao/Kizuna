package com.kizuna.remuneration.domain;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_guarantee_terms")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuaranteeTerm extends StoreScopedEntity {

  @Column(nullable = false, updatable = false)
  private Long personId;

  @Column(nullable = false)
  private LocalDate effectiveFrom;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private GuaranteeState state;

  private Long dailyAmount;

  @Column(nullable = false, length = 500)
  private String reason;

  private OffsetDateTime cancelledAt;

  @Builder
  public GuaranteeTerm(
      Long personId, LocalDate date, GuaranteeState state, Long amount, String reason) {
    this.personId = personId;
    replace(date, state, amount, reason);
  }

  public void replace(LocalDate date, GuaranteeState state, Long amount, String reason) {
    requireActive();
    if (date == null || state == null || reason == null || reason.isBlank())
      throw new ServiceException("保証条件と理由を入力してください");
    if (state == GuaranteeState.ACTIVE && amount == null
        || state == GuaranteeState.STOPPED && amount != null)
      throw new ServiceException("有効な保証には日額、停止には日額なしを指定してください");
    if (amount != null) RemunerationAmounts.require(amount);
    this.effectiveFrom = date;
    this.state = state;
    this.dailyAmount = amount;
    this.reason = reason.strip();
  }

  public void cancel() {
    requireActive();
    cancelledAt = OffsetDateTime.now();
  }

  private void requireActive() {
    if (cancelledAt != null) throw new ServiceException("取消済みの保証条件は変更できません");
  }
}
