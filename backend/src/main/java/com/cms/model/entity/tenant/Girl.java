package com.cms.model.entity.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "t_girls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Girl extends BaseEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "status")
  private String status;

  @Override
  public String toString() {
    return "Girl(id="
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
