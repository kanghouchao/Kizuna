package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "t_cast_enrollment_snapshots")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CastEnrollmentSnapshot extends StoreScopedEntity {
  @Column(name = "enrollment_id", nullable = false, updatable = false)
  private String enrollmentId;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private Long actorId;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private OffsetDateTime recordedAt;

  @Type(JsonBinaryType.class)
  @Column(name = "custom_fields", columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private Map<String, String> customFields = new HashMap<>();
}
