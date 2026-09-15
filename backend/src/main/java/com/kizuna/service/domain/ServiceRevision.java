package com.kizuna.service.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_service_revisions")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceRevision extends StoreScopedEntity {
  public enum Operation {
    CREATED,
    UPDATED,
    DELETED
  }

  @Column(nullable = false, updatable = false)
  private String serviceId;

  @Column(nullable = false, updatable = false)
  private long revisionNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private Operation operation;

  @Column(nullable = false, updatable = false)
  private Long actorId;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime occurredAt;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "kind", column = @Column(name = "before_kind", updatable = false)),
    @AttributeOverride(name = "name", column = @Column(name = "before_name", updatable = false)),
    @AttributeOverride(
        name = "durationMinutes",
        column = @Column(name = "before_duration_minutes", updatable = false)),
    @AttributeOverride(
        name = "chargeType",
        column = @Column(name = "before_charge_type", updatable = false)),
    @AttributeOverride(name = "price", column = @Column(name = "before_price", updatable = false)),
    @AttributeOverride(
        name = "remuneration",
        column = @Column(name = "before_remuneration", updatable = false))
  })
  private ServiceTerms beforeTerms;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "kind", column = @Column(name = "after_kind", updatable = false)),
    @AttributeOverride(name = "name", column = @Column(name = "after_name", updatable = false)),
    @AttributeOverride(
        name = "durationMinutes",
        column = @Column(name = "after_duration_minutes", updatable = false)),
    @AttributeOverride(
        name = "chargeType",
        column = @Column(name = "after_charge_type", updatable = false)),
    @AttributeOverride(name = "price", column = @Column(name = "after_price", updatable = false)),
    @AttributeOverride(
        name = "remuneration",
        column = @Column(name = "after_remuneration", updatable = false))
  })
  private ServiceTerms afterTerms;

  public static ServiceRevision record(ServiceItem item, ServiceTerms before, Long actorId) {
    var revision = new ServiceRevision();
    revision.serviceId = item.getId();
    revision.revisionNumber = item.getRevisionNumber();
    revision.operation =
        item.isDeleted()
            ? Operation.DELETED
            : before == null ? Operation.CREATED : Operation.UPDATED;
    revision.actorId = actorId;
    revision.occurredAt = OffsetDateTime.now();
    revision.beforeTerms = before;
    revision.afterTerms = item.getTerms();
    return revision;
  }
}
