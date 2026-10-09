package com.kizuna.remuneration.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_bonus_awards")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BonusAward extends StoreScopedEntity {

  @Column(nullable = false, updatable = false)
  private Long personId;

  @Column(nullable = false)
  private LocalDate awardDate;

  @Column(nullable = false)
  private long amount;

  @Column(nullable = false, length = 500)
  private String reason;

  private OffsetDateTime cancelledAt;

  @Builder
  public BonusAward(Long personId, LocalDate date, long amount, String reason) {
    this.personId = personId;
    replace(date, amount, reason);
  }

  public void requireVersion(long expected) {
    if (getVersion() != expected) throw new ConflictException("ボーナスが更新されています。再読み込みしてください");
  }

  public void replace(LocalDate date, long amount, String reason) {
    requireActive();
    if (date == null || amount <= 0 || reason == null || reason.isBlank())
      throw new ServiceException("ボーナスの帰属日・正額・理由を入力してください");
    this.awardDate = date;
    this.amount = RemunerationAmounts.require(amount);
    this.reason = reason.strip();
  }

  public void cancel() {
    requireActive();
    cancelledAt = OffsetDateTime.now();
  }

  private void requireActive() {
    if (cancelledAt != null) throw new ServiceException("取消済みのボーナスは変更できません");
  }

  public long effectiveAmount() {
    return cancelledAt == null ? amount : 0;
  }
}
