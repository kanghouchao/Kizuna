package com.kizuna.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/** 店舗スコープのエンティティ基盤。store_id は ID 参照（D3）で保持し、storeFilter で行レベル分離する。 */
@MappedSuperclass
@EntityListeners(StoreScopeStampListener.class)
@Getter
@Setter
@NoArgsConstructor
@ToString
@FilterDef(
    name = "storeFilter",
    applyToLoadByKey = true,
    parameters = @ParamDef(name = "storeId", type = Long.class))
@FilterDef(
    name = "storeSetFilter",
    applyToLoadByKey = true,
    parameters = @ParamDef(name = "storeIds", type = Long.class))
public abstract class StoreScopedEntity {

  @Id @SnowflakeId private String id;

  @Column(name = "store_id", updatable = false)
  private Long storeId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** 楽観ロック用バージョン（全実体共通 — #400）。 */
  @Setter(AccessLevel.NONE) // 新規 public setter 禁止規約: バージョンは JPA が管理し外部から設定させない（#400）
  @Version
  @Column(nullable = false)
  private Long version;

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
