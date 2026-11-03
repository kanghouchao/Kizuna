package com.kizuna.recruitment.application;

import com.kizuna.recruitment.api.dto.AttachmentPolicyResponse;
import com.kizuna.recruitment.api.dto.AttachmentSummaryResponse;
import com.kizuna.recruitment.api.dto.AttachmentUploadResponse;
import com.kizuna.recruitment.domain.ApplicantRepository;
import com.kizuna.recruitment.domain.AttachmentObject;
import com.kizuna.recruitment.domain.AttachmentUpload;
import com.kizuna.recruitment.domain.AttachmentUploadRepository;
import com.kizuna.recruitment.infrastructure.AttachmentBodyReceiver;
import com.kizuna.recruitment.infrastructure.PrivateAttachmentFile;
import com.kizuna.recruitment.infrastructure.PrivateAttachmentStorage;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttachmentService {
  public static final String READ =
      "hasAuthority('PERM_RECRUITMENT_VIEW') and hasAuthority('PERM_RECRUITMENT_ATTACHMENT_VIEW')";
  public static final String WRITE =
      READ + " and hasAuthority('PERM_RECRUITMENT_ATTACHMENT_MANAGE')";
  private final ApplicantAttachmentStore store;
  private final ApplicantRepository applicants;
  private final AttachmentUploadRepository uploads;
  private final PrivateAttachmentStorage storage;
  private final AppProperties properties;

  @PreAuthorize(READ)
  public AttachmentPolicyResponse policy() {
    var limits = properties.getPrivateAttachments();
    return new AttachmentPolicyResponse(
        storage.isConfigured(),
        List.of("image/jpeg", "image/png"),
        limits.getMaxFileBytes(),
        limits.getMaxImagePixels(),
        limits.getMaxImageDimension(),
        limits.getMaxDecodedBytes(),
        limits.getMaxApplicantFiles(),
        limits.getMaxApplicantBytes());
  }

  @PreAuthorize(WRITE)
  public UUID preflight(Long storeId, String applicantId, String uploadId, String rawKey) {
    storage.requireConfigured();
    UUID key = operationKey(rawKey);
    store.preflight(storeId, applicantId, uploadId, key);
    return key;
  }

  @PreAuthorize(WRITE)
  public ApplicantAttachmentStore.Receipt receive(
      Long storeId,
      String applicantId,
      String uploadId,
      UUID key,
      AttachmentBodyReceiver.Received image,
      String actorEmail) {
    storage.requireConfigured();
    return store.receive(storeId, applicantId, uploadId, key, image, actorEmail);
  }

  @PreAuthorize(WRITE)
  @Transactional(readOnly = true)
  public AttachmentUploadResponse operation(Long storeId, String applicantId, String rawKey) {
    storage.requireConfigured();
    UUID key = operationKey(rawKey);
    requireApplicant(storeId, applicantId);
    return uploads
        .findByStoreIdAndApplicantIdAndIdempotencyKey(storeId, applicantId, key)
        .map(AttachmentUploadResponse::from)
        .orElseThrow(AttachmentService::missing);
  }

  private static UUID operationKey(String rawKey) {
    UUID key;
    try {
      key = UUID.fromString(rawKey);
      if (!key.toString().equalsIgnoreCase(rawKey)) throw new IllegalArgumentException();
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new ServiceException("アップロードの操作キーが不正です");
    }
    return key;
  }

  @PreAuthorize(READ)
  @Transactional(readOnly = true)
  public Page<AttachmentSummaryResponse> list(Long storeId, String id, Pageable pageable) {
    storage.requireConfigured();
    return page(storeId, id, true, pageable).map(AttachmentSummaryResponse::from);
  }

  @PreAuthorize(WRITE)
  @Transactional(readOnly = true)
  public Page<AttachmentUploadResponse> uploads(Long storeId, String id, Pageable pageable) {
    storage.requireConfigured();
    return page(storeId, id, false, pageable).map(AttachmentUploadResponse::from);
  }

  private Page<AttachmentUpload> page(
      Long storeId, String applicantId, boolean completed, Pageable pageable) {
    requireApplicant(storeId, applicantId);
    Specification<AttachmentUpload> specification =
        (root, query, cb) ->
            cb.and(
                cb.equal(root.get("storeId"), storeId),
                cb.equal(root.get("applicantId"), applicantId),
                completed
                    ? cb.equal(root.get("status"), AttachmentUpload.Status.READY)
                    : cb.notEqual(root.get("status"), AttachmentUpload.Status.READY));
    return uploads.findAll(specification, pageable);
  }

  private void requireApplicant(Long storeId, String id) {
    if (applicants.findByIdAndStoreId(id, storeId).isEmpty()) throw missing();
  }

  @PreAuthorize(READ)
  public Download download(Long storeId, String id, String attachmentId, boolean head) {
    storage.requireConfigured();
    AttachmentObject object = store.stored(storeId, id, attachmentId);
    try {
      if (head) {
        storage.verifyMetadata(object);
        return new Download(object.mediaType(), object.sizeBytes(), null);
      }
      return new Download(object.mediaType(), object.sizeBytes(), storage.readVerified(object));
    } catch (ConflictException exception) {
      throw unavailable();
    }
  }

  private static NotFoundException missing() {
    return new NotFoundException("応募者または添付が見つかりません");
  }

  private static ServiceUnavailableException unavailable() {
    return new ServiceUnavailableException("添付の保存または取得を完了できません。再試行してください");
  }

  public record Download(String mediaType, long sizeBytes, PrivateAttachmentFile file) {}
}
