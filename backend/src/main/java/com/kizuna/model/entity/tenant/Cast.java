package com.kizuna.model.entity.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_casts")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cast extends BaseEntity {

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
