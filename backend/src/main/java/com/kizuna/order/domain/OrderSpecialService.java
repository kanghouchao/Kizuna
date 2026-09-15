package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_order_special_services")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSpecialService extends StoreScopedEntity {
  @Column(name = "order_id", insertable = false, updatable = false)
  private String orderId;

  private String serviceId;
  private String revisionId;
  private long revisionNumber;
  private long termsVersion;
  private String name;
  private String chargeType;
  private int price;
  private int remuneration;
  private String adoptionBasis;
  private OffsetDateTime adoptedAt;
  private String enrollmentId;
  private String consentEventId;
  private Long consentVersion;

  public SpecialServiceSnapshot getSnapshot() {
    return new SpecialServiceSnapshot(
        serviceId,
        revisionId,
        revisionNumber,
        termsVersion,
        name,
        chargeType,
        price,
        remuneration,
        adoptionBasis,
        adoptedAt,
        enrollmentId,
        consentEventId,
        consentVersion);
  }

  public static OrderSpecialService of(SpecialServiceSnapshot snapshot) {
    var line = new OrderSpecialService();
    line.adopt(snapshot);
    return line;
  }

  void adopt(SpecialServiceSnapshot snapshot) {
    serviceId = snapshot.serviceId();
    revisionId = snapshot.revisionId();
    revisionNumber = snapshot.revisionNumber();
    termsVersion = snapshot.termsVersion();
    name = snapshot.name();
    chargeType = snapshot.chargeType();
    price = snapshot.price();
    remuneration = snapshot.remuneration();
    adoptionBasis = snapshot.adoptionBasis();
    adoptedAt = snapshot.adoptedAt();
    enrollmentId = snapshot.enrollmentId();
    consentEventId = snapshot.consentEventId();
    consentVersion = snapshot.consentVersion();
  }
}
