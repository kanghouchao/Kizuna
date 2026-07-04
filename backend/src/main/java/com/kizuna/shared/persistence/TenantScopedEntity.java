package com.kizuna.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/** テナントスコープのエンティティ基盤。tenant_id は ID 参照（D3）で保持し、tenantFilter で行レベル分離する。 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@ToString
@FilterDef(
    name = "tenantFilter",
    applyToLoadByKey = true,
    parameters = @ParamDef(name = "tenantId", type = Long.class))
public abstract class TenantScopedEntity {

  @Id @SnowflakeId private String id;

  @Column(name = "tenant_id", updatable = false)
  private Long tenantId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    var now = OffsetDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = OffsetDateTime.now();
  }
}
