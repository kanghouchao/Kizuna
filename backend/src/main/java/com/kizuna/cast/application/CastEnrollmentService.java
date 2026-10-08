package com.kizuna.cast.application;

import com.kizuna.cast.api.dto.CastEnrollmentSnapshotResponse;
import com.kizuna.cast.api.dto.CastEnrollmentStatusHistoryResponse;
import com.kizuna.cast.api.dto.CastEnrollmentStatusResponse;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastEnrollmentSnapshot;
import com.kizuna.cast.domain.CastEnrollmentSnapshotRepository;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.cast.domain.CastEnrollmentStatusHistory;
import com.kizuna.cast.domain.CastEnrollmentStatusHistoryRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CastEnrollmentService {
  private final CastEnrollmentRepository enrollments;
  private final CastEnrollmentStatusHistoryRepository histories;
  private final CastEnrollmentSnapshotRepository snapshots;
  private final PlatformUserRepository users;
  private final StoreRepository stores;
  private final StoreContext storeContext;
  private final BusinessAudit audit;

  @StoreScoped
  @Transactional
  public CastEnrollmentStatusResponse suspend(String id, String actorEmail) {
    CastEnrollment enrollment = requireLocked(id);
    var before = snapshot(enrollment);
    CastEnrollmentStatus previous = enrollment.getStatus();
    enrollment.suspend();
    var history = record(enrollment, previous, actorEmail, now());
    enrollments.flush();
    recordAudit(enrollment, history, actorEmail, before);
    return response(enrollment);
  }

  @StoreScoped
  @Transactional
  public CastEnrollmentStatusResponse resume(String id, String actorEmail) {
    CastEnrollment enrollment = requireLocked(id);
    var before = snapshot(enrollment);
    CastEnrollmentStatus previous = enrollment.getStatus();
    enrollment.resume();
    var history = record(enrollment, previous, actorEmail, now());
    enrollments.flush();
    recordAudit(enrollment, history, actorEmail, before);
    return response(enrollment);
  }

  @StoreScoped
  @Transactional
  public CastEnrollmentStatusResponse withdraw(String id, String actorEmail) {
    CastEnrollment enrollment = requireLocked(id);
    var before = snapshot(enrollment);
    CastEnrollmentStatus previous = enrollment.getStatus();
    OffsetDateTime at = now();
    enrollment.withdraw(at);
    var history = record(enrollment, previous, actorEmail, at);
    enrollments.flush();
    recordAudit(enrollment, history, actorEmail, before);
    return response(enrollment);
  }

  @StoreScoped
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCreation(CastEnrollment enrollment, String actorEmail) {
    record(enrollment, null, actorEmail, now());
  }

  @StoreScoped
  @Transactional(propagation = Propagation.MANDATORY)
  public void replaceInternalFields(
      CastEnrollment enrollment, Map<String, String> values, String actorEmail) {
    if (enrollment.getCustomFields().equals(values)) return;
    saveSnapshot(enrollment, actorId(actorEmail));
    enrollment.replaceCustomFields(values);
  }

  @StoreScoped
  @Transactional(propagation = Propagation.MANDATORY)
  public void removeInternalField(List<CastEnrollment> enrollments, String key, String actorEmail) {
    var affected =
        enrollments.stream()
            .filter(enrollment -> enrollment.getCustomFields().containsKey(key))
            .toList();
    if (affected.isEmpty()) return;
    Long actorId = actorId(actorEmail);
    for (CastEnrollment enrollment : affected) {
      saveSnapshot(enrollment, actorId);
      enrollment.removeCustomField(key);
    }
  }

  private void saveSnapshot(CastEnrollment enrollment, Long actorId) {
    snapshots.save(
        CastEnrollmentSnapshot.builder()
            .enrollmentId(enrollment.getId())
            .actorId(actorId)
            .recordedAt(now())
            .customFields(new HashMap<>(enrollment.getCustomFields()))
            .build());
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<CastEnrollmentStatusHistoryResponse> history(
      String id, String cursor, int requestedSize) {
    require(id);
    int size = CursorPage.clampSize(requestedSize);
    Limit limit = Limit.of(size + 1);
    PageCursor after = cursor == null ? null : PageCursor.decode(cursor);
    var rows =
        after == null
            ? histories.findByEnrollmentIdOrderByRecordedAtDescIdDesc(id, limit)
            : histories.findAfter(id, after.timestampKey(), after.id(), limit);
    return CursorPage.of(
            rows, size, row -> new PageCursor(row.getRecordedAt().toString(), row.getId()).encode())
        .map(
            row ->
                new CastEnrollmentStatusHistoryResponse(
                    row.getId(),
                    row.getPreviousStatus(),
                    row.getNewStatus(),
                    row.getActorId(),
                    row.getRecordedAt()));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<CastEnrollmentSnapshotResponse> snapshots(
      String id, String cursor, int requestedSize) {
    require(id);
    int size = CursorPage.clampSize(requestedSize);
    Limit limit = Limit.of(size + 1);
    PageCursor after = cursor == null ? null : PageCursor.decode(cursor);
    var rows =
        after == null
            ? snapshots.findByEnrollmentIdOrderByRecordedAtDescIdDesc(id, limit)
            : snapshots.findAfter(id, after.timestampKey(), after.id(), limit);
    return CursorPage.of(
            rows, size, row -> new PageCursor(row.getRecordedAt().toString(), row.getId()).encode())
        .map(
            row ->
                new CastEnrollmentSnapshotResponse(
                    row.getId(),
                    row.getActorId(),
                    row.getRecordedAt(),
                    new HashMap<>(row.getCustomFields())));
  }

  private CastEnrollment require(String id) {
    return enrollments.findById(id).orElseThrow(() -> new NotFoundException("在籍が見つかりません"));
  }

  private CastEnrollment requireLocked(String id) {
    stores.lockCastFields(storeContext.getStoreId());
    return enrollments
        .findScopedByIdForUpdate(id)
        .orElseThrow(() -> new NotFoundException("在籍が見つかりません"));
  }

  private Long actorId(String email) {
    return users
        .findByEmail(email)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }

  private CastEnrollmentStatusHistory record(
      CastEnrollment enrollment,
      CastEnrollmentStatus previous,
      String actorEmail,
      OffsetDateTime at) {
    return histories.save(
        CastEnrollmentStatusHistory.builder()
            .enrollmentId(enrollment.getId())
            .previousStatus(previous)
            .newStatus(enrollment.getStatus())
            .actorId(actorId(actorEmail))
            .recordedAt(at)
            .build());
  }

  private void recordAudit(
      CastEnrollment enrollment,
      CastEnrollmentStatusHistory history,
      String actorEmail,
      Map<String, String> before) {
    String action =
        switch (enrollment.getStatus()) {
          case SUSPENDED -> "CAST_ENROLLMENT_SUSPENDED";
          case ENROLLED -> "CAST_ENROLLMENT_RESUMED";
          case WITHDRAWN -> "CAST_ENROLLMENT_WITHDRAWN";
        };
    audit.record(
        actorEmail,
        enrollment.getStoreId(),
        action,
        "CAST_ENROLLMENT",
        enrollment.getId(),
        "CAST_ENROLLMENT_STATUS_HISTORY",
        history.getId(),
        before,
        snapshot(enrollment));
  }

  private static Map<String, String> snapshot(CastEnrollment enrollment) {
    return Map.of(
        "exists", "true",
        "cast_id", Objects.toString(enrollment.getCastId(), ""),
        "status", enrollment.getStatus().name(),
        "ended_at", Objects.toString(enrollment.getEndedAt(), ""),
        "version", Objects.toString(enrollment.getVersion(), ""));
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }

  private static CastEnrollmentStatusResponse response(CastEnrollment enrollment) {
    return new CastEnrollmentStatusResponse(
        enrollment.getId(), enrollment.getStatus(), enrollment.getEndedAt());
  }
}
