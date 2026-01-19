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
@Table(name = "t_customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends BaseEntity {

  @Column(name = "name")
  private String name;

  @Column(name = "phone_number")
  private String phoneNumber;

  @Column(name = "phone_number2")
  private String phoneNumber2;

  @Column(name = "address")
  private String address;

  @Column(name = "building_name")
  private String buildingName;

  @Column(name = "landmark")
  private String landmark;

  @Column(name = "classification")
  private String classification;

  @Column(name = "has_pet")
  private Boolean hasPet;

  @Column(name = "points")
  private Integer points;

  @Column(name = "ng_type")
  private String ngType;

  @Column(name = "ng_content")
  private String ngContent;

  @Override
  public String toString() {
    return "Customer(id="
        + getId()
        + ", name="
        + name
        + ", phoneNumber="
        + phoneNumber
        + ", classification="
        + classification
        + ", points="
        + points
        + ")";
  }
}
