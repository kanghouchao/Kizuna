package com.kizuna.recruitment.application;

import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantRepository;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.AttachmentObject;
import com.kizuna.recruitment.domain.AttachmentUpload;
import com.kizuna.recruitment.domain.AttachmentUploadRepository;
import com.kizuna.recruitment.infrastructure.AttachmentBodyReceiver;
import com.kizuna.recruitment.infrastructure.AttachmentStorageMissingException;
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
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.UserRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 応募者添付の保存。受け取り（予約から完了まで）と匿名化の消去を持ち、失敗コードは失敗した箇所で決める。
 *
 * <p>ロック順は店舗（KEY SHARE）→ 添付行 → 応募者。完了は添付行を握ったまま実体を書いてから応募者を取るので、消去も同じ順で書き込みの終わりを待つ。
 * 終端化と競って完了が拒まれた実体も PENDING の行が指し続けるため、消去が行から辿って消せる。
 */
@Service
public class ApplicantAttachmentStore {
  private final ApplicantRepository applicants;
  private final AttachmentUploadRepository uploads;
  private final StoreRepository stores;
  private final UserRepository users;
  private final BusinessAudit audit;
  private final AppProperties properties;
  private final PrivateAttachmentStorage storage;
  private final RasterImageNormalizer normalizer;
  private final Clock clock;
  private final TransactionTemplate writing;
  private final TransactionTemplate reading;

  public ApplicantAttachmentStore(
      ApplicantRepository applicants,
      AttachmentUploadRepository uploads,
      StoreRepository stores,
      UserRepository users,
      BusinessAudit audit,
      AppProperties properties,
      PrivateAttachmentStorage storage,
      RasterImageNormalizer normalizer,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.applicants = applicants;
    this.uploads = uploads;
    this.stores = stores;
    this.users = users;
    this.audit = audit;
    this.properties = properties;
    this.storage = storage;
    this.normalizer = normalizer;
    this.clock = clock;
    this.writing = new TransactionTemplate(transactions);
    this.reading = new TransactionTemplate(transactions);
    this.reading.setReadOnly(true);
  }

  /** 本文を受け取る前の判定。完了済みの再送だけは終端の応募者にも許す。 */
  public void preflight(Long storeId, String applicantId, String uploadId, UUID key) {
    reading.executeWithoutResult(
        status -> {
          Applicant applicant = requireApplicant(storeId, applicantId);
          Optional<AttachmentUpload> existing = find(storeId, applicantId, uploadId, key);
          existing.ifPresent(
              upload ->
                  upload.requireReplay(key, upload.getOriginalSha256(), upload.getMediaType()));
          if (existing.isEmpty() || existing.get().getStatus() != AttachmentUpload.Status.READY)
            requireEditable(applicant.getStatus());
        });
  }

  public Receipt receive(
      Long storeId,
      String applicantId,
      String uploadId,
      UUID key,
      AttachmentBodyReceiver.Received image,
      String actorEmail) {
    AttachmentUpload reserved =
        reading.execute(
            status -> {
              requireApplicant(storeId, applicantId);
              Optional<AttachmentUpload> existing = find(storeId, applicantId, uploadId, key);
              existing.ifPresent(
                  upload -> upload.requireReplay(key, image.originalSha256(), image.mediaType()));
              return existing.orElse(null);
            });
    if (reserved != null && reserved.getStatus() == AttachmentUpload.Status.READY)
      return new Receipt(false, AttachmentViews.Summary.of(reserved));
    try {
      if (reserved != null) {
        Optional<Receipt> recovered = recover(storeId, applicantId, reserved, actorEmail);
        if (recovered.isPresent()) return recovered.get();
        if (!reserved.getNormalizerVersion().equals(RasterImageNormalizer.VERSION))
          throw new Failed(
              AttachmentUpload.Failure.NORMALIZER_UNAVAILABLE,
              new ConflictException("元の画像変換方式を利用できません。回復を待ってください"));
      }
      try (NormalizedImage content = normalize(image)) {
        // 受信から正規化までの間の一時ファイルの破損で、保存先は関わらないため記録しない。
        if (!image.originalSha256().equals(content.originalSha256()))
          throw new ConflictException("受信画像の一致を確認できません。再選択してください");
        if (reserved == null) reserved = reserve(storeId, applicantId, key, content, actorEmail);
        return complete(storeId, applicantId, reserved, content, actorEmail);
      } catch (IOException cleanupFailure) {
        throw new Failed(AttachmentUpload.Failure.STORAGE_UNAVAILABLE, unavailable());
      }
    } catch (Failed failed) {
      if (reserved != null) recordFailure(storeId, applicantId, reserved.getId(), failed.failure);
      throw failed.response;
    }
  }

  /** 応募者の添付を実体と行ごと消し、ロック済みの応募者を返す。呼び手の取引の中で確定する。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public Applicant purge(Long storeId, String applicantId) {
    lockStore(storeId);
    List<AttachmentUpload> attachments =
        uploads.findByStoreIdAndApplicantIdForUpdate(storeId, applicantId);
    Applicant applicant = lockApplicant(storeId, applicantId);
    for (AttachmentUpload upload : attachments) storage.delete(upload.object());
    attachments.forEach(uploads::delete);
    return applicant;
  }

  public AttachmentObject stored(Long storeId, String applicantId, String attachmentId) {
    return reading.execute(
        status -> {
          requireApplicant(storeId, applicantId);
          return uploads
              .findByIdAndStoreIdAndApplicantId(attachmentId, storeId, applicantId)
              .filter(upload -> upload.getStatus() == AttachmentUpload.Status.READY)
              .orElseThrow(ApplicantAttachmentStore::missing)
              .object();
        });
  }

  /** 既に正しい実体があれば、現在の変換方式に依らず完了させる。実体が無ければ空を返し、正規化からやり直させる。 */
  private Optional<Receipt> recover(
      Long storeId, String applicantId, AttachmentUpload reserved, String actorEmail) {
    return write(
        status -> {
          AttachmentUpload upload = lockUpload(storeId, applicantId, reserved.getId());
          if (upload.getStatus() == AttachmentUpload.Status.READY)
            return Optional.of(new Receipt(false, AttachmentViews.Summary.of(upload)));
          requireEditable(storeId, applicantId);
          try (PrivateAttachmentFile _ = storage.readVerified(upload.object())) {
            // 読めて照合も通ったので、その実体で完了する。
          } catch (AttachmentStorageMissingException absent) {
            return Optional.empty();
          } catch (ConflictException mismatch) {
            throw new Failed(AttachmentUpload.Failure.CONTENT_MISMATCH, mismatch);
          } catch (ServiceUnavailableException unavailable) {
            throw new Failed(AttachmentUpload.Failure.STORAGE_UNAVAILABLE, unavailable);
          } catch (IOException cleanupFailure) {
            throw new Failed(
                AttachmentUpload.Failure.STORAGE_UNAVAILABLE,
                new ServiceUnavailableException("添付の一時領域を利用できません"));
          }
          return Optional.of(finish(storeId, applicantId, upload, actorEmail));
        });
  }

  private NormalizedImage normalize(AttachmentBodyReceiver.Received image) {
    try {
      return normalizer.normalize(image.path(), image.mediaType());
    } catch (ServiceException | ServiceUnavailableException failure) {
      throw new Failed(AttachmentUpload.Failure.NORMALIZER_UNAVAILABLE, failure);
    } catch (RuntimeException failure) {
      throw new Failed(AttachmentUpload.Failure.NORMALIZER_UNAVAILABLE, unavailable());
    }
  }

  AttachmentUpload reserve(
      Long storeId, String applicantId, UUID key, NormalizedImage content, String actorEmail) {
    return write(
        status -> {
          lockStore(storeId);
          Applicant applicant = lockApplicant(storeId, applicantId);
          Optional<AttachmentUpload> existing =
              uploads.findByStoreIdAndApplicantIdAndIdempotencyKey(storeId, applicantId, key);
          if (existing.isPresent()) {
            existing.get().requireReplay(key, content.originalSha256(), content.mediaType());
            return existing.get();
          }
          requireEditable(applicant.getStatus());
          var limits = properties.getPrivateAttachments();
          if (uploads.countReserved(storeId, applicantId) >= limits.getMaxApplicantFiles()
              || uploads.reservedBytes(storeId, applicantId)
                  > limits.getMaxApplicantBytes() - content.sizeBytes()) {
            throw new ConflictException("添付の件数または容量が上限に達しています。未完了のアップロードを確認してください");
          }
          return uploads.saveAndFlush(
              AttachmentUpload.reserve(
                  storeId,
                  applicantId,
                  key,
                  content.originalSha256(),
                  content.mediaType(),
                  content.canonicalSha256(),
                  content.sizeBytes(),
                  content.normalizerVersion(),
                  actorId(actorEmail)));
        });
  }

  Receipt complete(
      Long storeId,
      String applicantId,
      AttachmentUpload reserved,
      NormalizedImage content,
      String actorEmail) {
    return write(
        status -> {
          AttachmentUpload upload = lockUpload(storeId, applicantId, reserved.getId());
          if (upload.getStatus() == AttachmentUpload.Status.READY)
            return new Receipt(false, AttachmentViews.Summary.of(upload));
          // 実体を書く前に終端を読み、拒まれる完了のために書かない。確定は応募者のロック後に改めて判じる。
          requireEditable(storeId, applicantId);
          if (!upload.getNormalizerVersion().equals(content.normalizerVersion())
              || !upload.getCanonicalSha256().equals(content.canonicalSha256())
              || upload.getSizeBytes() != content.sizeBytes()) {
            throw new Failed(
                AttachmentUpload.Failure.NORMALIZER_UNAVAILABLE,
                new ConflictException("元の画像変換を再現できません。保存済み資料を上書きせず回復を待ちます"));
          }
          try {
            storage.ensureStored(upload.object(), content.path());
          } catch (ConflictException mismatch) {
            throw new Failed(AttachmentUpload.Failure.CONTENT_MISMATCH, mismatch);
          } catch (ServiceUnavailableException unavailable) {
            throw new Failed(AttachmentUpload.Failure.STORAGE_UNAVAILABLE, unavailable);
          }
          return finish(storeId, applicantId, upload, actorEmail);
        });
  }

  private Receipt finish(
      Long storeId, String applicantId, AttachmentUpload upload, String actorEmail) {
    requireEditable(lockApplicant(storeId, applicantId).getStatus());
    upload.complete(actorId(actorEmail), OffsetDateTime.now(clock));
    uploads.flush();
    audit.recordCurrent(
        storeId,
        "APPLICANT_ATTACHMENT_STORED",
        "APPLICANT_ATTACHMENT",
        upload.getId(),
        Map.of(),
        Map.of("applicant_id", applicantId, "attachment_id", upload.getId()));
    return new Receipt(true, AttachmentViews.Summary.of(upload));
  }

  void recordFailure(
      Long storeId, String applicantId, String uploadId, AttachmentUpload.Failure failure) {
    try {
      writing.executeWithoutResult(
          status ->
              uploads
                  .findByIdAndStoreIdAndApplicantIdForUpdate(uploadId, storeId, applicantId)
                  .ifPresent(upload -> upload.recordFailure(failure)));
    } catch (RuntimeException ignored) {
      // 記録できなくても、先に確定した PENDING の所有記録が残るので回復できる。
    }
  }

  // 同じ行の並行処理は NOWAIT で 409 に、DB・監査の障害は保存の失敗として記録する（予約があれば回復待ちにする）。
  // 業務の拒否と Failed はそのまま通す。
  private <T> T write(TransactionCallback<T> step) {
    try {
      return writing.execute(step);
    } catch (PessimisticLockingFailureException busy) {
      throw new ConflictException("同じアップロードを処理中です。時間をおいて再試行してください");
    } catch (DataAccessException | TransactionException databaseFailure) {
      throw new Failed(AttachmentUpload.Failure.STORAGE_UNAVAILABLE, unavailable());
    }
  }

  private Optional<AttachmentUpload> find(
      Long storeId, String applicantId, String uploadId, UUID key) {
    return uploadId == null
        ? uploads.findByStoreIdAndApplicantIdAndIdempotencyKey(storeId, applicantId, key)
        : Optional.of(
            uploads
                .findByIdAndStoreIdAndApplicantId(uploadId, storeId, applicantId)
                .orElseThrow(ApplicantAttachmentStore::missing));
  }

  private Applicant requireApplicant(Long storeId, String id) {
    return applicants
        .findByIdAndStoreId(id, storeId)
        .orElseThrow(ApplicantAttachmentStore::missing);
  }

  private void lockStore(Long storeId) {
    stores.lockAgainstDeletion(storeId).orElseThrow(ApplicantAttachmentStore::missing);
  }

  private AttachmentUpload lockUpload(Long storeId, String applicantId, String uploadId) {
    lockStore(storeId);
    return uploads
        .findByIdAndStoreIdAndApplicantIdForUpdate(uploadId, storeId, applicantId)
        .orElseThrow(ApplicantAttachmentStore::missing);
  }

  private Applicant lockApplicant(Long storeId, String applicantId) {
    return applicants
        .findByIdAndStoreIdForUpdate(applicantId, storeId)
        .orElseThrow(ApplicantAttachmentStore::missing);
  }

  private void requireEditable(Long storeId, String applicantId) {
    requireEditable(
        applicants
            .findStatusByIdAndStoreId(applicantId, storeId)
            .orElseThrow(ApplicantAttachmentStore::missing));
  }

  private static void requireEditable(ApplicantStatus status) {
    if (status.isTerminal()) throw new ServiceException("選考が終了した応募者には画像を追加できません");
  }

  private Long actorId(String email) {
    return users
        .findByEmail(email)
        .orElseThrow(() -> new StaleSessionException("操作主体を確認できません"))
        .getId();
  }

  private static NotFoundException missing() {
    return new NotFoundException("応募者または添付が見つかりません");
  }

  private static ServiceUnavailableException unavailable() {
    return new ServiceUnavailableException("添付の保存または取得を完了できません。再試行してください");
  }

  public record Receipt(boolean created, AttachmentViews.Summary attachment) {}

  /** 回復待ちにする失敗（正規化器・非公開ストレージ・DB）。予約があればコードを記録してから response を返す。 */
  private static final class Failed extends RuntimeException {
    private final AttachmentUpload.Failure failure;
    private final RuntimeException response;

    Failed(AttachmentUpload.Failure failure, RuntimeException response) {
      super(null, null, false, false);
      this.failure = failure;
      this.response = response;
    }
  }
}
