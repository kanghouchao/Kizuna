package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_cast_enrollment_status_histories")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CastEnrollmentStatusHistory extends StoreScopedEntity {
  @Column(name = "enrollment_id", nullable = false, updatable = false)
  private String enrollmentId;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private Long actorId;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private OffsetDateTime recordedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status")
  private CastEnrollmentStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_status", nullable = false)
  private CastEnrollmentStatus newStatus;
}
