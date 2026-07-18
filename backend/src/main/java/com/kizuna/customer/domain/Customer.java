package com.kizuna.customer.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_customers")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends StoreScopedEntity {

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

  @Column(name = "rank")
  private String rank;

  @Column(name = "line_id")
  private String lineId;

  @Column(name = "usage_areas")
  private String usageAreas;

  @Column(name = "ng_type")
  private String ngType;

  @Column(name = "ng_content")
  private String ngContent;

  /** 部分更新コマンドを適用する。null のフィールドは変更しない。 */
  public void apply(CustomerPatch patch) {
    if (patch.name() != null) {
      this.name = patch.name();
    }
    if (patch.phoneNumber() != null) {
      this.phoneNumber = patch.phoneNumber();
    }
    if (patch.phoneNumber2() != null) {
      this.phoneNumber2 = patch.phoneNumber2();
    }
    if (patch.address() != null) {
      this.address = patch.address();
    }
    if (patch.buildingName() != null) {
      this.buildingName = patch.buildingName();
    }
    if (patch.classification() != null) {
      this.classification = patch.classification();
    }
    if (patch.rank() != null) {
      this.rank = patch.rank();
    }
    if (patch.lineId() != null) {
      this.lineId = patch.lineId();
    }
    if (patch.usageAreas() != null) {
      this.usageAreas = patch.usageAreas();
    }
    if (patch.hasPet() != null) {
      this.hasPet = patch.hasPet();
    }
    if (patch.ngType() != null) {
      this.ngType = patch.ngType();
    }
    if (patch.ngContent() != null) {
      this.ngContent = patch.ngContent();
    }
  }

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
