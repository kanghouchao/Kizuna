package com.kizuna.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantRepository;
import com.kizuna.recruitment.domain.AttachmentUpload;
import com.kizuna.recruitment.domain.AttachmentUploadRepository;
import com.kizuna.recruitment.infrastructure.PrivateAttachmentStorage;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttachmentServiceTest {
  private static final Long STORE_ID = 7L;
  private final ApplicantAttachmentStore store = mock(ApplicantAttachmentStore.class);
  private final ApplicantRepository applicants = mock(ApplicantRepository.class);
  private final AttachmentUploadRepository uploads = mock(AttachmentUploadRepository.class);
  private final PrivateAttachmentStorage storage = mock(PrivateAttachmentStorage.class);
  private final AttachmentService service =
      new AttachmentService(store, applicants, uploads, storage, new AppProperties());
  private final UUID key = UUID.randomUUID();

  private AttachmentUpload reserved() {
    return AttachmentUpload.reserve(
        STORE_ID, "a", key, "a".repeat(64), "image/png", "b".repeat(64), 3, "version", 1L);
  }

  @Test
  void operationUsesCanonicalKeyWithoutUploadPreflightOrStorageAccess() {
    when(applicants.findByIdAndStoreId("a", STORE_ID))
        .thenReturn(Optional.of(mock(Applicant.class)));
    when(uploads.findByStoreIdAndApplicantIdAndIdempotencyKey(STORE_ID, "a", key))
        .thenReturn(Optional.of(reserved()));
    assertThat(service.operation(STORE_ID, "a", key.toString().toUpperCase()).status())
        .isEqualTo(AttachmentUpload.Status.PENDING);
    verify(storage).requireConfigured();
    verifyNoMoreInteractions(storage);
    verifyNoInteractions(store);
  }

  @Test
  void operationRejectsNonCanonicalKeysWithoutEchoingThem() {
    for (String raw : new String[] {null, "", "1-1-1-1-1", " " + key, key + " ", "invalid"}) {
      assertThatThrownBy(() -> service.operation(STORE_ID, "a", raw))
          .isInstanceOf(ServiceException.class)
          .hasMessage("アップロードの操作キーが不正です");
    }
    verifyNoInteractions(applicants, uploads, store);
  }

  @Test
  void operationReadsEveryStateOnTerminalApplicantsWithoutMutation() {
    var applicant = mock(Applicant.class);
    when(applicants.findByIdAndStoreId("a", STORE_ID)).thenReturn(Optional.of(applicant));
    var row = reserved();
    when(uploads.findByStoreIdAndApplicantIdAndIdempotencyKey(STORE_ID, "a", key))
        .thenReturn(Optional.of(row));
    for (var state : AttachmentUpload.Status.values()) {
      if (state == AttachmentUpload.Status.RECOVERY_REQUIRED)
        row.recordFailure(AttachmentUpload.Failure.CONTENT_MISMATCH);
      if (state == AttachmentUpload.Status.READY) row.complete(1L, OffsetDateTime.now());
      clearInvocations(applicants, uploads);
      assertThat(service.operation(STORE_ID, "a", key.toString()).status()).isEqualTo(state);
      assertThat(row.getStatus()).isEqualTo(state);
      verify(applicants).findByIdAndStoreId("a", STORE_ID);
      verify(uploads).findByStoreIdAndApplicantIdAndIdempotencyKey(STORE_ID, "a", key);
      verifyNoMoreInteractions(applicants, uploads);
      verify(applicant, never()).getStatus();
    }
    assertThatThrownBy(() -> service.operation(STORE_ID, "hidden", key.toString()))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.operation(STORE_ID, "a", UUID.randomUUID().toString()))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.operation(STORE_ID + 1, "a", key.toString()))
        .isInstanceOf(NotFoundException.class);
  }
}
