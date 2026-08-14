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
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
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

  /**
   * 統合先の顧客。NULL がこの行は生きていることを意味し、値があればこの行は墓標でその値が統合先である（ADR 0010）。
   *
   * <p>{@code updatable = false} を付けてはならない。連鎖統合の圧平は「被統合行を指している既存の墓標」の指す先を
   * 書き換えるので、不変列として写像すると圧平が静かに効かなくなる。
   */
  @Column(name = "merged_into_id")
  private String mergedIntoId;

  /**
   * 統合の被統合行としてこの行を墓標にする。行は削除せず、以後は統合先への道標としてだけ残る。
   *
   * <p>既に墓標の行を再び統合できないのは呼出側（{@code CustomerMergeService}）が実行の瞬間に判定する。
   * その判定は押さえた行の状態からではなく別問い合わせで行うため、ここでは 自分自身の状態を見ない。
   */
  public void mergeInto(String survivingCustomerId) {
    this.mergedIntoId = survivingCustomerId;
  }

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
        + ")";
  }
}
