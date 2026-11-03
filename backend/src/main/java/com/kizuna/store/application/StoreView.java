package com.kizuna.store.application;

import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreStatus;
import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/** 店舗の現在の状態。ドメイン照会のキャッシュ（Redis・既定の JDK 直列化）に載るため Serializable を外さない。 */
public record StoreView(
    Long id,
    String name,
    String shortName,
    String domain,
    StoreStatus status,
    OffsetDateTime createdAt)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  static StoreView of(Store store) {
    return new StoreView(
        store.getId(),
        store.getName(),
        store.getShortName(),
        store.getDomain(),
        store.getStatus(),
        store.getCreatedAt());
  }
}
