package com.kizuna.advertising.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "t_advertising_months")
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
