package com.kizuna.remuneration.domain;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** 明細の承諾。追加型の時点記録で、書き換え・削除は DB が拒否する（ADR 0041）。 */
@Entity
@Table(name = "t_statement_acceptances")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StatementAcceptance extends StoreScopedEntity {

  @Column(nullable = false, updatable = false)
  private Long personId;

  @Column(nullable = false, updatable = false)
  private LocalDate businessDate;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime acceptedAt;

  @Column(nullable = false, updatable = false, length = 64)
  private String contentDigest;

  @Column(nullable = false, updatable = false)
  private long orderAmount;

  @Column(nullable = false, updatable = false)
  private long guaranteeAmount;

  @Column(nullable = false, updatable = false)
  private long bonusAmount;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String content;

  public static StatementAcceptance accept(
      Long storeId,
      Long personId,
      LocalDate businessDate,
      StatementContent content,
      OffsetDateTime acceptedAt) {
    if (content.guaranteeAmount() == null) throw new ServiceException("保証の判定が確定していない日は承諾できません");
    var row = new StatementAcceptance();
    row.setStoreId(storeId);
    row.personId = personId;
    row.businessDate = businessDate;
    row.acceptedAt = acceptedAt;
    row.contentDigest = content.digest();
    row.orderAmount = content.orderAmount();
    row.guaranteeAmount = content.guaranteeAmount();
    row.bonusAmount = content.bonusAmount();
    row.content = content.canonical();
    return row;
  }

  public long total() {
    return RemunerationAmounts.add(
        RemunerationAmounts.add(orderAmount, guaranteeAmount), bonusAmount);
  }
}
