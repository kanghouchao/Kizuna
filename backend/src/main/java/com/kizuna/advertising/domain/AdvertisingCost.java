package com.kizuna.advertising.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_advertising_costs")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdvertisingCost extends StoreScopedEntity {

  @Column(nullable = false, updatable = false, length = 7)
  private String month;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AdvertisingCategory category;

  @Column(nullable = false, length = 200)
  private String mediaName;

  @Column(length = 200)
  private String agencyName;

  @Column(length = 200)
  private String planName;

  private Integer inquiryCount;

  @Column(nullable = false)
  private Integer amount;

  @Column(nullable = false)
  private boolean deleted;

  @Column(nullable = false)
  private long revision;

  @Builder
  public AdvertisingCost(String month, AdvertisingValues values) {
    this.month = month;
    replace(values);
  }

  public AdvertisingValues values() {
    return new AdvertisingValues(category, mediaName, agencyName, planName, inquiryCount, amount);
  }

  public void replace(AdvertisingValues values) {
    category = values.category();
    mediaName = values.mediaName();
    agencyName = values.agencyName();
    planName = values.planName();
    inquiryCount = values.inquiryCount();
    amount = values.amount();
    revision++;
  }

  public void requireVersion(long expected) {
    if (!getVersion().equals(expected)) throw new ConflictException("広告費が更新されています。再読み込みしてください");
  }

  public void delete() {
    deleted = true;
    revision++;
  }
}
