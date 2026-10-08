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
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUserRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttachmentTransactions {
  private final ApplicantRepository applicants;
  private final AttachmentUploadRepository uploads;
  private final StoreRepository stores;
  private final StoreContext storeContext;
  private final PlatformUserRepository users;
  private final BusinessAudit audit;
  private final AppProperties properties;
  private final PrivateAttachmentStorage storage;
  private final Clock clock;

  @StoreScoped
  @Transactional(readOnly = true)
  public void preflight(String applicantId, String uploadId, UUID key) {
    Applicant applicant = requireApplicant(applicantId);
    Optional<AttachmentUpload> existing =
        uploadId == null
            ? uploads.findByApplicantIdAndIdempotencyKey(applicantId, key)
            : Optional.of(requireUpload(applicantId, uploadId));
    existing.ifPresent(
        upload -> upload.requireReplay(key, upload.getOriginalSha256(), upload.getMediaType()));
    if (existing.isEmpty() || existing.get().getStatus() != AttachmentUpload.Status.READY) {
      requireEditable(applicant);
    }
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public Optional<AttachmentUpload> lookup(
      String applicantId, String uploadId, UUID key, String originalHash, String mediaType) {
    requireApplicant(applicantId);
    Optional<AttachmentUpload> existing =
        uploadId == null
            ? uploads.findByApplicantIdAndIdempotencyKey(applicantId, key)
            : Optional.of(requireUpload(applicantId, uploadId));
    existing.ifPresent(upload -> upload.requireReplay(key, originalHash, mediaType));
    return existing;
  }

  @StoreScoped
  @Transactional
  public AttachmentUpload reserve(
      String applicantId, UUID key, NormalizedImage content, String actorEmail) {
    lockStore();
    Applicant applicant =
        applicants.findScopedForUpdate(applicantId).orElseThrow(AttachmentTransactions::missing);
    Optional<AttachmentUpload> existing =
        uploads.findByApplicantIdAndIdempotencyKey(applicantId, key);
    if (existing.isPresent()) {
      existing.get().requireReplay(key, content.originalSha256(), content.mediaType());
      return existing.get();
    }
    requireEditable(applicant);
    var limits = properties.getPrivateAttachments();
    if (uploads.countReserved(applicantId) >= limits.getMaxApplicantFiles()
        || uploads.reservedBytes(applicantId)
            > limits.getMaxApplicantBytes() - content.sizeBytes()) {
      throw new ConflictException("添付の件数または容量が上限に達しています。未完了のアップロードを確認してください");
    }
    AttachmentUpload upload =
        AttachmentUpload.reserve(
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

  @StoreScoped
  @Transactional
  public Completion complete(
      String applicantId,
      String uploadId,
      UUID key,
      String originalHash,
      String mediaType,
      NormalizedImage content,
      String actorEmail) {
    lockStore();
    AttachmentUpload upload =
        uploads.lockScoped(applicantId, uploadId).orElseThrow(AttachmentTransactions::missing);
    upload.requireReplay(key, originalHash, mediaType);
    if (upload.getStatus() == AttachmentUpload.Status.READY)
      return new Completion(false, AttachmentSummaryResponse.from(upload));
    if (applicants
        .findScopedStatus(applicantId)
        .orElseThrow(AttachmentTransactions::missing)
        .isTerminal()) throw new ServiceException("選考が終了した応募者には画像を追加できません");
    AttachmentObject object = object(upload);
    if (content == null) {
      try (PrivateAttachmentFile ignored = storage.readVerified(object)) {
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
        applicants.findScopedForUpdate(applicantId).orElseThrow(AttachmentTransactions::missing);
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

  @StoreScoped
  @Transactional
  public void recordFailure(String applicantId, String uploadId, AttachmentUpload.Failure failure) {
    uploads.lockScoped(applicantId, uploadId).ifPresent(upload -> upload.recordFailure(failure));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<AttachmentUpload> list(
      String applicantId, boolean completed, String cursor, int size) {
    requireApplicant(applicantId);
    if (size < 1 || size > 100 || cursor != null && cursor.length() > 512)
      throw new ServiceException("取得件数または続きの位置が不正です");
    PageCursor after = cursor == null ? null : PageCursor.decode(cursor);
    Specification<AttachmentUpload> specification =
        (root, query, cb) -> {
          var condition =
              cb.and(
                  cb.equal(root.get("applicantId"), applicantId),
                  completed
                      ? cb.equal(root.get("status"), AttachmentUpload.Status.READY)
                      : cb.notEqual(root.get("status"), AttachmentUpload.Status.READY));
          if (after != null)
            condition =
                cb.and(
                    condition,
                    cb.or(
                        cb.lessThan(root.get("createdAt"), after.timestampKey()),
                        cb.and(
                            cb.equal(root.get("createdAt"), after.timestampKey()),
                            cb.lessThan(root.get("id"), after.id()))));
          return condition;
        };
    var rows =
        uploads.findBy(
            specification,
            query ->
                query
                    .sortBy(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
                    .limit(size + 1)
                    .all());
    return CursorPage.of(
        rows,
        size,
        upload -> new PageCursor(upload.getCreatedAt().toString(), upload.getId()).encode());
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public AttachmentObject download(String applicantId, String uploadId) {
    requireApplicant(applicantId);
    AttachmentUpload upload = requireUpload(applicantId, uploadId);
    if (upload.getStatus() != AttachmentUpload.Status.READY) throw missing();
    return object(upload);
  }

  private Applicant requireApplicant(String id) {
    return applicants.findById(id).orElseThrow(AttachmentTransactions::missing);
  }

  private AttachmentUpload requireUpload(String applicantId, String id) {
    return uploads
        .findById(id)
        .filter(upload -> upload.getApplicantId().equals(applicantId))
        .orElseThrow(AttachmentTransactions::missing);
  }

  private void lockStore() {
    stores
        .lockAgainstDeletion(storeContext.getStoreId())
        .orElseThrow(AttachmentTransactions::missing);
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
