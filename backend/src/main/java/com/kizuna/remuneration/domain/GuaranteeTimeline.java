package com.kizuna.remuneration.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_guarantee_timelines")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuaranteeTimeline extends StoreScopedEntity {

  @Column(nullable = false, updatable = false)
  private Long personId;

  @Column(nullable = false)
  private long revision;

  @Builder
  public GuaranteeTimeline(Long personId) {
    this.personId = personId;
  }

  public void advance(long expected) {
    if (expected != revision) throw new ConflictException("保証条件が更新されています。再読み込みしてください");
    revision++;
  }
}
