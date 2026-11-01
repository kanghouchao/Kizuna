package com.kizuna.recruitment.application;

import com.kizuna.recruitment.api.dto.AttachmentSummaryResponse;
import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantRepository;
import com.kizuna.recruitment.domain.AttachmentUpload;
import com.kizuna.recruitment.domain.AttachmentUploadRepository;
import com.kizuna.recruitment.infrastructure.AttachmentObject;
import com.kizuna.recruitment.infrastructure.AttachmentStorageMissingException;
import com.kizuna.recruitment.infrastructure.NormalizedImage;
import com.kizuna.recruitment.infrastructure.PrivateAttachmentFile;
import com.kizuna.recruitment.infrastructure.PrivateAttachmentStorage;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.UserRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttachmentTransactions {
  private final ApplicantRepository applicants;
  private final AttachmentUploadRepository uploads;
  private final StoreRepository stores;
  private final UserRepository users;
  private final BusinessAudit audit;
  private final AppProperties properties;
  private final PrivateAttachmentStorage storage;
  private final Clock clock;

  @Transactional(readOnly = true)
  public AttachmentUpload operation(Long storeId, String applicantId, UUID key) {
    requireApplicant(storeId, applicantId);
    return uploads
        .findByStoreIdAndApplicantIdAndIdempotencyKey(storeId, applicantId, key)
        .orElseThrow(AttachmentTransactions::missing);
  }

  @Transactional(readOnly = true)
  public void preflight(Long storeId, String applicantId, String uploadId, UUID key) {
    Applicant applicant = requireApplicant(storeId, applicantId);
    Optional<AttachmentUpload> existing =
        uploadId == null
            ? uploads.findByStoreIdAndApplicantIdAndIdempotencyKey(storeId, applicantId, key)
            : Optional.of(requireUpload(storeId, applicantId, uploadId));
    existing.ifPresent(
        upload -> upload.requireReplay(key, upload.getOriginalSha256(), upload.getMediaType()));
    if (existing.isEmpty() || existing.get().getStatus() != AttachmentUpload.Status.READY) {
      requireEditable(applicant);
    }
  }

  @Transactional(readOnly = true)
  public Optional<AttachmentUpload> lookup(
      Long storeId,
      String applicantId,
      String uploadId,
      UUID key,
      String originalHash,
      String mediaType) {
    requireApplicant(storeId, applicantId);
    Optional<AttachmentUpload> existing =
        uploadId == null
            ? uploads.findByStoreIdAndApplicantIdAndIdempotencyKey(storeId, applicantId, key)
            : Optional.of(requireUpload(storeId, applicantId, uploadId));
    existing.ifPresent(upload -> upload.requireReplay(key, originalHash, mediaType));
    return existing;
  }

  @Transactional
  public AttachmentUpload reserve(
      Long storeId, String applicantId, UUID key, NormalizedImage content, String actorEmail) {
    lockStore(storeId);
    Applicant applicant =
        applicants
            .findByIdAndStoreIdForUpdate(applicantId, storeId)
            .orElseThrow(AttachmentTransactions::missing);
    Optional<AttachmentUpload> existing =
        uploads.findByStoreIdAndApplicantIdAndIdempotencyKey(storeId, applicantId, key);
    if (existing.isPresent()) {
      existing.get().requireReplay(key, content.originalSha256(), content.mediaType());
      return existing.get();
    }
    requireEditable(applicant);
    var limits = properties.getPrivateAttachments();
    if (uploads.countReserved(storeId, applicantId) >= limits.getMaxApplicantFiles()
        || uploads.reservedBytes(storeId, applicantId)
            > limits.getMaxApplicantBytes() - content.sizeBytes()) {
      throw new ConflictException("添付の件数または容量が上限に達しています。未完了のアップロードを確認してください");
    }
    AttachmentUpload upload =
        AttachmentUpload.reserve(
            storeId,
            applicantId,
            key,
            content.originalSha256(),
            content.mediaType(),
            content.canonicalSha256(),
            content.sizeBytes(),
            content.normalizerVersion(),
            actorId(actorEmail));
    return uploads.saveAndFlush(upload);
  }

  @Transactional
  public Completion complete(
      Long storeId,
      String applicantId,
      String uploadId,
      UUID key,
      String originalHash,
      String mediaType,
      NormalizedImage content,
      String actorEmail) {
    lockStore(storeId);
    AttachmentUpload upload =
        uploads
            .findByIdAndStoreIdAndApplicantIdForUpdate(uploadId, storeId, applicantId)
            .orElseThrow(AttachmentTransactions::missing);
    upload.requireReplay(key, originalHash, mediaType);
    if (upload.getStatus() == AttachmentUpload.Status.READY)
      return new Completion(false, AttachmentSummaryResponse.from(upload));
    if (applicants
        .findStatusByIdAndStoreId(applicantId, storeId)
        .orElseThrow(AttachmentTransactions::missing)
        .isTerminal()) throw new ServiceException("選考が終了した応募者には画像を追加できません");
    AttachmentObject object = object(upload);
    if (content == null) {
      try (PrivateAttachmentFile _ = storage.readVerified(object)) {
        // 既に存在する正しい正規化画像を使う回復は、現在の符号化器に依存しない。
      } catch (AttachmentStorageMissingException missingObject) {
        return null;
      } catch (IOException cleanupFailure) {
        throw new ServiceUnavailableException("添付の一時領域を利用できません");
      }
    } else {
      if (!upload.getNormalizerVersion().equals(content.normalizerVersion())
          || !upload.getCanonicalSha256().equals(content.canonicalSha256())
          || upload.getSizeBytes() != content.sizeBytes()) {
        throw new AttachmentNormalizationException("元の画像変換を再現できません。保存済み資料を上書きせず回復を待ちます");
      }
      storage.ensureStored(object, content.path());
    }
    Applicant applicant =
        applicants
            .findByIdAndStoreIdForUpdate(applicantId, storeId)
            .orElseThrow(AttachmentTransactions::missing);
    requireEditable(applicant);
    upload.complete(actorId(actorEmail), OffsetDateTime.now(clock));
    uploads.flush();
    audit.recordCurrent(
        upload.getStoreId(),
        "APPLICANT_ATTACHMENT_STORED",
        "APPLICANT_ATTACHMENT",
        upload.getId(),
        Map.of(),
        Map.of("applicant_id", applicantId, "attachment_id", upload.getId()));
    return new Completion(true, AttachmentSummaryResponse.from(upload));
  }

  @Transactional
  public void recordFailure(
      Long storeId, String applicantId, String uploadId, AttachmentUpload.Failure failure) {
    uploads
        .findByIdAndStoreIdAndApplicantIdForUpdate(uploadId, storeId, applicantId)
        .ifPresent(upload -> upload.recordFailure(failure));
  }

  @Transactional(readOnly = true)
  public Page<AttachmentUpload> list(
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

  @Transactional(readOnly = true)
  public AttachmentObject download(Long storeId, String applicantId, String uploadId) {
    requireApplicant(storeId, applicantId);
    AttachmentUpload upload = requireUpload(storeId, applicantId, uploadId);
    if (upload.getStatus() != AttachmentUpload.Status.READY) throw missing();
    return object(upload);
  }

  private Applicant requireApplicant(Long storeId, String id) {
    return applicants.findByIdAndStoreId(id, storeId).orElseThrow(AttachmentTransactions::missing);
  }

  private AttachmentUpload requireUpload(Long storeId, String applicantId, String id) {
    return uploads
        .findByIdAndStoreIdAndApplicantId(id, storeId, applicantId)
        .orElseThrow(AttachmentTransactions::missing);
  }

  private void lockStore(Long storeId) {
    stores.lockAgainstDeletion(storeId).orElseThrow(AttachmentTransactions::missing);
  }

  private Long actorId(String email) {
    return users
        .findByEmail(email)
        .orElseThrow(() -> new StaleSessionException("操作主体を確認できません"))
        .getId();
  }

  private static void requireEditable(Applicant applicant) {
    if (applicant.getStatus().isTerminal()) throw new ServiceException("選考が終了した応募者には画像を追加できません");
  }

  private static AttachmentObject object(AttachmentUpload upload) {
    return new AttachmentObject(
        upload.getObjectId(),
        upload.getMediaType(),
        upload.getSizeBytes(),
        upload.getCanonicalSha256());
  }

  private static NotFoundException missing() {
    return new NotFoundException("応募者または添付が見つかりません");
  }

  public record Completion(boolean created, AttachmentSummaryResponse attachment) {}
}
