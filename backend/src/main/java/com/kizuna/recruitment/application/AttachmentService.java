package com.kizuna.recruitment.application;

import com.kizuna.recruitment.api.dto.AttachmentPolicyResponse;
import com.kizuna.recruitment.api.dto.AttachmentSummaryResponse;
import com.kizuna.recruitment.api.dto.AttachmentUploadResponse;
import com.kizuna.recruitment.domain.AttachmentUpload;
import com.kizuna.recruitment.infrastructure.AttachmentObject;
import com.kizuna.recruitment.infrastructure.NormalizedImage;
import com.kizuna.recruitment.infrastructure.PrivateAttachmentFile;
import com.kizuna.recruitment.infrastructure.PrivateAttachmentStorage;
import com.kizuna.recruitment.infrastructure.RasterImageNormalizer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.web.CursorPage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttachmentService {
  public static final String READ =
      "hasAuthority('PERM_RECRUITMENT_VIEW') and hasAuthority('PERM_RECRUITMENT_ATTACHMENT_VIEW')";
  public static final String WRITE =
      READ + " and hasAuthority('PERM_RECRUITMENT_ATTACHMENT_MANAGE')";
  private final AttachmentTransactions transactions;
  private final PrivateAttachmentStorage storage;
  private final RasterImageNormalizer normalizer;
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
  public UUID preflight(String applicantId, String uploadId, String rawKey) {
    storage.requireConfigured();
    UUID key;
    try {
      key = UUID.fromString(rawKey);
      if (!key.toString().equalsIgnoreCase(rawKey)) throw new IllegalArgumentException();
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new ServiceException("アップロードの操作キーが不正です");
    }
    transactions.preflight(applicantId, uploadId, key);
    return key;
  }

  @PreAuthorize(READ)
  public CursorPage<AttachmentSummaryResponse> list(String id, String cursor, int size) {
    storage.requireConfigured();
    return transactions.list(id, true, cursor, size).map(AttachmentSummaryResponse::from);
  }

  @PreAuthorize(WRITE)
  public CursorPage<AttachmentUploadResponse> uploads(String id, String cursor, int size) {
    storage.requireConfigured();
    return transactions.list(id, false, cursor, size).map(AttachmentUploadResponse::from);
  }

  @PreAuthorize(WRITE)
  public AttachmentTransactions.Completion upload(
      String id,
      String uploadId,
      UUID key,
      Path original,
      String originalHash,
      String mediaType,
      String actorEmail) {
    storage.requireConfigured();
    AttachmentUpload reserved = null;
    try {
      reserved = transactions.lookup(id, uploadId, key, originalHash, mediaType).orElse(null);
      if (reserved != null) {
        if (reserved.getStatus() == AttachmentUpload.Status.READY)
          return new AttachmentTransactions.Completion(
              false, AttachmentSummaryResponse.from(reserved));
        var completed =
            transactions.complete(
                id, reserved.getId(), key, originalHash, mediaType, null, actorEmail);
        if (completed != null) return completed;
        if (!reserved.getNormalizerVersion().equals(RasterImageNormalizer.VERSION))
          throw new ConflictException("元の画像変換方式を利用できません。回復を待ってください");
      }
      try (NormalizedImage content = normalizer.normalize(original, mediaType)) {
        if (!originalHash.equals(content.originalSha256()))
          throw new ConflictException("受信画像の一致を確認できません。再選択してください");
        if (reserved == null) reserved = transactions.reserve(id, key, content, actorEmail);
        return transactions.complete(
            id, reserved.getId(), key, originalHash, mediaType, content, actorEmail);
      }
    } catch (PessimisticLockingFailureException exception) {
      throw new ConflictException("同じアップロードを処理中です。時間をおいて再試行してください");
    } catch (AuthenticationException | AccessDeniedException | StaleSessionException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      if (reserved != null)
        markFailure(
            id,
            reserved.getId(),
            exception instanceof ConflictException
                ? AttachmentUpload.Failure.CONTENT_MISMATCH
                : AttachmentUpload.Failure.STORAGE_UNAVAILABLE);
      if (exception instanceof ServiceException
          || exception instanceof ConflictException
          || exception instanceof NotFoundException
          || exception instanceof ServiceUnavailableException) throw exception;
      throw unavailable();
    } catch (IOException exception) {
      if (reserved != null)
        markFailure(id, reserved.getId(), AttachmentUpload.Failure.STORAGE_UNAVAILABLE);
      throw unavailable();
    }
  }

  @PreAuthorize(READ)
  public Download download(String id, String attachmentId, boolean head) {
    storage.requireConfigured();
    AttachmentObject object = transactions.download(id, attachmentId);
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

  private void markFailure(String id, String uploadId, AttachmentUpload.Failure failure) {
    try {
      transactions.recordFailure(id, uploadId, failure);
    } catch (RuntimeException ignored) {
      // 保存失敗時にも、先行確定したPENDINGの所有記録を残して回復可能にする。
    }
  }

  private static ServiceUnavailableException unavailable() {
    return new ServiceUnavailableException("添付の保存または取得を完了できません。再試行してください");
  }

  public record Download(String mediaType, long sizeBytes, PrivateAttachmentFile file) {}
}
