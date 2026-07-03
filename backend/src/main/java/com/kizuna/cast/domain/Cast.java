package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_casts")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cast extends TenantScopedEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "status")
  private String status;

  @Column(name = "photo_url", length = 500)
  private String photoUrl;

  @Column(name = "introduction", columnDefinition = "TEXT")
  private String introduction;

  @Column(name = "age")
  private Integer age;

  @Column(name = "height")
  private Integer height;

  @Column(name = "bust")
  private Integer bust;

  @Column(name = "waist")
  private Integer waist;

  @Column(name = "hip")
  private Integer hip;

  @Column(name = "display_order")
  private Integer displayOrder;

  /** 部分更新コマンドを適用する。null のフィールドは変更しない。 */
  public void apply(CastPatch patch) {
    if (patch.name() != null) {
      this.name = patch.name();
    }
    if (patch.status() != null) {
      this.status = patch.status();
    }
    if (patch.photoUrl() != null) {
      this.photoUrl = patch.photoUrl();
    }
    if (patch.introduction() != null) {
      this.introduction = patch.introduction();
    }
    if (patch.age() != null) {
      this.age = patch.age();
    }
    if (patch.height() != null) {
      this.height = patch.height();
    }
    if (patch.bust() != null) {
      this.bust = patch.bust();
    }
    if (patch.waist() != null) {
      this.waist = patch.waist();
    }
    if (patch.hip() != null) {
      this.hip = patch.hip();
    }
    if (patch.displayOrder() != null) {
      this.displayOrder = patch.displayOrder();
    }
  }

  @Override
  public String toString() {
    return "Cast(id="
        + getId()
        + ", name="
        + name
        + ", status="
        + status
        + ", createdAt="
        + getCreatedAt()
        + ", updatedAt="
        + getUpdatedAt()
        + ")";
  }
}
