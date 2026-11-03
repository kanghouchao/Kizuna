package com.kizuna.store.api.dto;

import com.kizuna.store.application.StoreView;
import com.kizuna.store.domain.StoreStatus;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoreVO {
  private String id;
  private String name;
  private String shortName;
  private String domain;
  private StoreStatus status;
  private OffsetDateTime createdAt;

  public static StoreVO of(StoreView view) {
    return new StoreVO(
        String.valueOf(view.id()),
        view.name(),
        view.shortName(),
        view.domain(),
        view.status(),
        view.createdAt());
  }
}
