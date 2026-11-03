package com.kizuna.recruitment.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantRepository;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.AttachmentUpload;
import com.kizuna.recruitment.domain.AttachmentUploadRepository;
import com.kizuna.shared.exception.ServiceException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class ApplicantAttachmentStoreTest {
  private static final Long STORE_ID = 7L;

  @Test
  void terminalApplicantRejectsNewAndPendingUploadsButAllowsReadyReplay() {
    var applicants = mock(ApplicantRepository.class);
    var uploads = mock(AttachmentUploadRepository.class);
    var store =
        new ApplicantAttachmentStore(
            applicants,
            uploads,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            mock(PlatformTransactionManager.class));
    var applicant = mock(Applicant.class);
    when(applicants.findByIdAndStoreId("applicant", STORE_ID)).thenReturn(Optional.of(applicant));
    UUID key = UUID.randomUUID();
    var pending =
        AttachmentUpload.reserve(
            STORE_ID,
            "applicant",
            key,
            "a".repeat(64),
            "image/png",
            "b".repeat(64),
            3,
            "version",
            1L);
    when(applicant.getStatus()).thenReturn(ApplicantStatus.WITHDRAWN);
    when(uploads.findByStoreIdAndApplicantIdAndIdempotencyKey(STORE_ID, "applicant", key))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> store.preflight(STORE_ID, "applicant", null, key))
        .isInstanceOf(ServiceException.class);
    when(uploads.findByStoreIdAndApplicantIdAndIdempotencyKey(STORE_ID, "applicant", key))
        .thenReturn(Optional.of(pending));
    when(uploads.findByIdAndStoreIdAndApplicantId("upload", STORE_ID, "applicant"))
        .thenReturn(Optional.of(pending));
    assertThatThrownBy(() -> store.preflight(STORE_ID, "applicant", null, key))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> store.preflight(STORE_ID, "applicant", "upload", key))
        .isInstanceOf(ServiceException.class);
    pending.complete(1L, OffsetDateTime.now());
    assertThatCode(() -> store.preflight(STORE_ID, "applicant", null, key))
        .doesNotThrowAnyException();
    assertThatCode(() -> store.preflight(STORE_ID, "applicant", "upload", key))
        .doesNotThrowAnyException();
  }
}
