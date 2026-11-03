package com.kizuna.recruitment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ConflictException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttachmentUploadTest {
  @Test
  void sameOriginalRequestCompletesOnceAndFailureCannotDowngradeSuccess() {
    UUID key = UUID.randomUUID();
    AttachmentUpload upload =
        AttachmentUpload.reserve(
            7L,
            "applicant",
            key,
            "a".repeat(64),
            "image/png",
            "b".repeat(64),
            100L,
            "raster-v1",
            1L);
    assertThat(upload.getStoreId()).isEqualTo(7L);
    upload.requireReplay(key, "a".repeat(64), "image/png");
    OffsetDateTime completed = OffsetDateTime.parse("2026-10-07T12:00:00Z");
    assertThat(upload.complete(2L, completed)).isTrue();
    assertThat(upload.complete(3L, completed.plusSeconds(10))).isFalse();
    upload.recordFailure(AttachmentUpload.Failure.STORAGE_UNAVAILABLE);
    assertThat(upload.getStatus()).isEqualTo(AttachmentUpload.Status.READY);
    assertThat(upload.getCompletedBy()).isEqualTo(2L);
    assertThat(upload.getCompletedAt()).isEqualTo(completed);
    assertThat(upload.getFailure()).isNull();
  }

  @Test
  void canonicalEqualityNeverMakesDifferentOriginalBytesTheSameRequest() {
    UUID key = UUID.randomUUID();
    AttachmentUpload upload =
        AttachmentUpload.reserve(
            7L,
            "applicant",
            key,
            "a".repeat(64),
            "image/png",
            "b".repeat(64),
            100L,
            "raster-v1",
            1L);
    assertThatThrownBy(() -> upload.requireReplay(key, "c".repeat(64), "image/png"))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> upload.requireReplay(UUID.randomUUID(), "a".repeat(64), "image/png"))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> upload.requireReplay(key, "a".repeat(64), "image/jpeg"))
        .isInstanceOf(ConflictException.class);
    upload.recordFailure(AttachmentUpload.Failure.CONTENT_MISMATCH);
    assertThat(upload.getStatus()).isEqualTo(AttachmentUpload.Status.RECOVERY_REQUIRED);
    upload.requireReplay(key, "a".repeat(64), "image/png");
    assertThat(upload.getCanonicalSha256()).isEqualTo("b".repeat(64));
  }

  // 実体は正規化後の副本なので、照合値は原画像ではなく正規化後のハッシュを使う。
  @Test
  void storedObjectIsTheCanonicalCopyAndRejectsInvalidValues() {
    AttachmentUpload upload =
        AttachmentUpload.reserve(
            7L,
            "applicant",
            UUID.randomUUID(),
            "a".repeat(64),
            "image/png",
            "b".repeat(64),
            100L,
            "raster-v1",
            1L);
    assertThat(upload.object())
        .isEqualTo(new AttachmentObject(upload.getObjectId(), "image/png", 100L, "b".repeat(64)));
    assertThatThrownBy(
            () -> new AttachmentObject(UUID.randomUUID(), "image/gif", 1L, "b".repeat(64)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new AttachmentObject(UUID.randomUUID(), "image/png", 0L, "b".repeat(64)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AttachmentObject(UUID.randomUUID(), "image/png", 1L, "B"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
