package com.kizuna.advertising.domain;

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
@Table(name = "t_advertising_months")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdvertisingMonth extends StoreScopedEntity {

  @Column(nullable = false, updatable = false, length = 7)
  private String month;

  @Column(nullable = false)
  private long revision;

  @Builder
  public AdvertisingMonth(String month) {
    this.month = month;
  }

  public void advance() {
    revision++;
  }
}
