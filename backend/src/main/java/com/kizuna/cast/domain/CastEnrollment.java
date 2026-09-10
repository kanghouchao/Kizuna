package com.kizuna.cast.domain;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
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
@Table(name = "t_cast_enrollments")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CastEnrollment extends StoreScopedEntity {
  @Column(name = "cast_id")
  private Long castId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private CastEnrollmentStatus status = CastEnrollmentStatus.ENROLLED;

  @Column(name = "ended_at")
  private OffsetDateTime endedAt;

  @Type(JsonBinaryType.class)
  @Column(name = "custom_fields", columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private Map<String, String> customFields = new HashMap<>();

  public void linkCast(Long castId) {
    if (this.castId != null) throw new CastInvitationStateException("この在籍には既に本人が紐づいています");
    this.castId = Objects.requireNonNull(castId);
  }

  public void suspend() {
    if (status != CastEnrollmentStatus.ENROLLED) throw new ServiceException("在籍中のキャストのみ停止できます");
    status = CastEnrollmentStatus.SUSPENDED;
  }

  public void resume() {
    if (status != CastEnrollmentStatus.SUSPENDED) throw new ServiceException("在籍停止中のキャストのみ再開できます");
    status = CastEnrollmentStatus.ENROLLED;
  }

  public boolean isMembershipActive() {
    return status != CastEnrollmentStatus.WITHDRAWN;
  }

  public boolean isDeletable() {
    return castId == null && isMembershipActive();
  }

  public void withdraw(OffsetDateTime now) {
    if (status == CastEnrollmentStatus.WITHDRAWN) throw new ServiceException("退店済みの在籍は変更できません");
    status = CastEnrollmentStatus.WITHDRAWN;
    endedAt = Objects.requireNonNull(now);
  }

  public void replaceCustomFields(Map<String, String> values) {
    this.customFields = new HashMap<>(values);
  }

  public void removeCustomField(String key) {
    customFields.remove(key);
  }
}
