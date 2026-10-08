package com.kizuna.recruitment.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_applicant_attachment_uploads")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
public class AttachmentUpload extends StoreScopedEntity {
  public enum Status {
    PENDING,
    RECOVERY_REQUIRED,
    READY
  }

  public enum Failure {
    STORAGE_UNAVAILABLE,
    CONTENT_MISMATCH,
    NORMALIZER_UNAVAILABLE
  }

  @Column(nullable = false, updatable = false, length = 64)
  private String applicantId;

  @Column(nullable = false, updatable = false)
  private UUID idempotencyKey;

  @Column(nullable = false, updatable = false)
  private UUID objectId;

  @Column(nullable = false, updatable = false, length = 64)
  private String originalSha256;

  @Column(nullable = false, updatable = false, length = 64)
  private String canonicalSha256;

  @Column(nullable = false, updatable = false, length = 20)
  private String mediaType;

  @Column(nullable = false, updatable = false)
  private long sizeBytes;

  @Column(nullable = false, updatable = false, length = 40)
  private String normalizerVersion;

  @Column(nullable = false, updatable = false)
  private Long createdBy;

  @Column private Long completedBy;
  @Column private OffsetDateTime completedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Status status = Status.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(length = 40)
  private Failure failure;

  public static AttachmentUpload reserve(
      String applicantId,
      UUID key,
      String originalSha256,
      String mediaType,
      String canonicalSha256,
      long sizeBytes,
      String normalizerVersion,
      Long actorId) {
    if (applicantId == null
        || applicantId.isBlank()
        || applicantId.length() > 64
        || !validHash(originalSha256)
        || !validHash(canonicalSha256)
        || sizeBytes <= 0
        || !("image/png".equals(mediaType) || "image/jpeg".equals(mediaType))
        || normalizerVersion == null
        || normalizerVersion.isBlank()
        || normalizerVersion.length() > 40) {
      throw new IllegalArgumentException("添付の予約情報が不正です");
    }
    AttachmentUpload upload = new AttachmentUpload();
    upload.applicantId = applicantId;
    upload.idempotencyKey = Objects.requireNonNull(key);
    upload.objectId = UUID.randomUUID();
    upload.originalSha256 = originalSha256;
    upload.canonicalSha256 = canonicalSha256;
    upload.mediaType = mediaType;
    upload.sizeBytes = sizeBytes;
    upload.normalizerVersion = normalizerVersion;
    upload.createdBy = Objects.requireNonNull(actorId);
    return upload;
  }

  public void requireReplay(UUID key, String originalSha256, String mediaType) {
    if (!idempotencyKey.equals(key)
        || !this.originalSha256.equals(originalSha256)
        || !this.mediaType.equals(mediaType)) {
      throw new ConflictException("元のアップロードと同じ画像・形式・操作キーで再送してください");
    }
  }

  public boolean complete(Long actorId, OffsetDateTime now) {
    if (status == Status.READY) return false;
    completedBy = Objects.requireNonNull(actorId);
    completedAt = Objects.requireNonNull(now);
    status = Status.READY;
    failure = null;
    return true;
  }

  public void recordFailure(Failure failure) {
    if (status == Status.READY) return;
    this.failure = Objects.requireNonNull(failure);
    status = Status.RECOVERY_REQUIRED;
  }

  private static boolean validHash(String value) {
    return value != null && value.matches("[a-f0-9]{64}");
  }
}
