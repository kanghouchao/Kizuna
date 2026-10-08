package com.kizuna.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.recruitment.domain.AttachmentUpload;
import com.kizuna.recruitment.infrastructure.PrivateAttachmentStorage;
import com.kizuna.recruitment.infrastructure.RasterImageNormalizer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ResourceBusyException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttachmentServiceTest {
  private final AttachmentTransactions transactions = mock(AttachmentTransactions.class);
  private final PrivateAttachmentStorage storage = mock(PrivateAttachmentStorage.class);
  private final RasterImageNormalizer normalizer = mock(RasterImageNormalizer.class);
  private final AttachmentService service =
      new AttachmentService(transactions, storage, normalizer, new AppProperties());
  private final UUID key = UUID.randomUUID();
  private final Path source = Path.of("synthetic");

  @Test
  void operationUsesCanonicalKeyWithoutUploadPreflightOrStorageAccess() {
    var row =
        AttachmentUpload.reserve(
            "applicant", key, "a".repeat(64), "image/png", "b".repeat(64), 3, "version", 1L);
    when(transactions.operation("applicant", key)).thenReturn(row);
    assertThat(service.operation("applicant", key.toString().toUpperCase()).status())
        .isEqualTo(AttachmentUpload.Status.PENDING);
    verify(storage).requireConfigured();
    verify(transactions).operation("applicant", key);
    verifyNoMoreInteractions(storage, transactions);
    verifyNoInteractions(normalizer);
  }

  @Test
  void operationRejectsNonCanonicalKeysWithoutEchoingThem() {
    for (String raw : new String[] {null, "", "1-1-1-1-1", " " + key, key + " ", "invalid"}) {
      assertThatThrownBy(() -> service.operation("applicant", raw))
          .isInstanceOf(ServiceException.class)
          .hasMessage("アップロードの操作キーが不正です");
    }
    verifyNoInteractions(transactions, normalizer);
  }

  private AttachmentUpload pending(String version) {
    var upload =
        AttachmentUpload.reserve(
            "applicant", key, "a".repeat(64), "image/png", "b".repeat(64), 3, version, 1L);
    upload.setId("upload");
    when(transactions.lookup("applicant", "upload", key, "a".repeat(64), "image/png"))
        .thenReturn(Optional.of(upload));
    return upload;
  }

  private void recover() {
    service.upload("applicant", "upload", key, source, "a".repeat(64), "image/png", "actor");
  }

  @Test
  void missingHistoricalNormalizerRecordsNormalizerFailure() {
    pending("historical-version");
    assertThatThrownBy(this::recover).isInstanceOf(ConflictException.class);
    verify(transactions)
        .recordFailure("applicant", "upload", AttachmentUpload.Failure.NORMALIZER_UNAVAILABLE);
    verifyNoInteractions(normalizer);
  }

  @Test
  void normalizerCapacityAndTimeoutKeepTheirHttpErrorsAndFailureOrigin() {
    pending(RasterImageNormalizer.VERSION);
    for (var failure :
        new ServiceUnavailableException[] {
          new ResourceBusyException("画像処理が混み合っています"),
          new ServiceUnavailableException("画像処理が制限時間を超えました")
        }) {
      doThrow(failure).when(normalizer).normalize(source, "image/png");
      assertThatThrownBy(this::recover).isSameAs(failure);
    }
    verify(transactions, times(2))
        .recordFailure("applicant", "upload", AttachmentUpload.Failure.NORMALIZER_UNAVAILABLE);
  }

  @Test
  void storageFailureRemainsStorageFailure() {
    pending(RasterImageNormalizer.VERSION);
    when(transactions.complete(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new ServiceUnavailableException("保存先を利用できません"));
    assertThatThrownBy(this::recover).isInstanceOf(ServiceUnavailableException.class);
    verify(transactions)
        .recordFailure("applicant", "upload", AttachmentUpload.Failure.STORAGE_UNAVAILABLE);
    verifyNoInteractions(normalizer);
  }
}
