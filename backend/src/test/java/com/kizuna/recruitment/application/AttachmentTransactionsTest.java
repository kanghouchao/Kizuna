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

class AttachmentTransactionsTest {
  @Test
  void terminalApplicantRejectsNewAndPendingUploadsButAllowsReadyReplay() {
    var applicants = mock(ApplicantRepository.class);
    var uploads = mock(AttachmentUploadRepository.class);
    var service =
        new AttachmentTransactions(applicants, uploads, null, null, null, null, null, null, null);
    var applicant = mock(Applicant.class);
    when(applicants.findById("applicant")).thenReturn(Optional.of(applicant));
    UUID key = UUID.randomUUID();
    var pending =
        AttachmentUpload.reserve(
            "applicant", key, "a".repeat(64), "image/png", "b".repeat(64), 3, "version", 1L);
    when(applicant.getStatus()).thenReturn(ApplicantStatus.WITHDRAWN);
    when(uploads.findByApplicantIdAndIdempotencyKey("applicant", key)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.preflight("applicant", null, key))
        .isInstanceOf(ServiceException.class);
    when(uploads.findByApplicantIdAndIdempotencyKey("applicant", key))
        .thenReturn(Optional.of(pending));
    when(uploads.findById("upload")).thenReturn(Optional.of(pending));
    assertThatThrownBy(() -> service.preflight("applicant", null, key))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> service.preflight("applicant", "upload", key))
        .isInstanceOf(ServiceException.class);
    pending.complete(1L, OffsetDateTime.now());
    assertThatCode(() -> service.preflight("applicant", null, key)).doesNotThrowAnyException();
    assertThatCode(() -> service.preflight("applicant", "upload", key)).doesNotThrowAnyException();
  }
}
