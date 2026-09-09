package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "t_cast_profiles")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CastProfile extends StoreScopedEntity {
  @Column(name = "enrollment_id", nullable = false, unique = true, updatable = false)
  private String enrollmentId;

  @Column(nullable = false)
  private String name;

  @Column(name = "photo_url", length = 500)
  private String photoUrl;

  @Column(columnDefinition = "TEXT")
  private String introduction;

  private Integer age;
  private Integer height;
  private Integer bust;
  private Integer waist;
  private Integer hip;

  @Column(name = "display_order", nullable = false)
  @Builder.Default
  private Integer displayOrder = 0;

  @Enumerated(EnumType.STRING)
  @Column(name = "publication_status", nullable = false)
  @Builder.Default
  private CastPublicationStatus publicationStatus = CastPublicationStatus.UNPUBLISHED;

  @Type(JsonBinaryType.class)
  @Column(name = "custom_fields", columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private Map<String, String> customFields = new HashMap<>();

  public void changePublication(CastPublicationStatus status) {
    this.publicationStatus = Objects.requireNonNull(status);
  }

  public void replaceCustomFields(Map<String, String> values) {
    this.customFields = new HashMap<>(values);
  }

  public void removeCustomField(String key) {
    customFields.remove(key);
  }

  public void apply(CastProfilePatch patch) {
    if (patch.name() != null) this.name = patch.name();
    if (patch.photoUrl() != null) this.photoUrl = patch.photoUrl();
    if (patch.introduction() != null) this.introduction = patch.introduction();
    if (patch.age() != null) this.age = patch.age();
    if (patch.height() != null) this.height = patch.height();
    if (patch.bust() != null) this.bust = patch.bust();
    if (patch.waist() != null) this.waist = patch.waist();
    if (patch.hip() != null) this.hip = patch.hip();
    if (patch.displayOrder() != null) this.displayOrder = patch.displayOrder();
  }
}
