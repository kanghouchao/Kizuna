package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "t_casts")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cast extends StoreScopedEntity {

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

  @Column(name = "platform_user_id")
  private Long platformUserId;

  @Type(JsonBinaryType.class)
  @Column(name = "custom_fields", columnDefinition = "jsonb")
  @Builder.Default
  private Map<String, String> customFields = new HashMap<>();

  /** 平台身分（PlatformUser）を紐づける。既に紐づき済みなら状態例外を投げる（防御的不変条件）。 */
  public void linkPlatformUser(Long platformUserId) {
    if (this.platformUserId != null) {
      throw new CastInvitationStateException("この档案には既に平台身分が紐づいています");
    }
    this.platformUserId = platformUserId;
  }

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
    if (patch.customFields() != null) {
      this.customFields = patch.customFields();
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
