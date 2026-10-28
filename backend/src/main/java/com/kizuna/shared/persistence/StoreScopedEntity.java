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

/** 店舗スコープのエンティティ基盤。store_id は ID 参照で保持し、店舗の条件は各クエリが明示する。 */
@MappedSuperclass
@EntityListeners(StoreScopeStampListener.class)
@Getter
@Setter
@NoArgsConstructor
@ToString
public abstract class StoreScopedEntity {

  @Id @SnowflakeId private String id;

  @Column(name = "store_id", updatable = false)
  private Long storeId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** 楽観ロック用バージョン（全実体共通）。 */
  @Setter(AccessLevel.NONE) // 新規 public setter 禁止規約: バージョンは JPA が管理し外部から設定させない
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

  /**
   * @PreUpdate と同じ永続化の時刻印で、集約の振る舞いから行を更新済みにするために使う。
   */
  protected void touch() {
    this.updatedAt = OffsetDateTime.now();
  }
}
