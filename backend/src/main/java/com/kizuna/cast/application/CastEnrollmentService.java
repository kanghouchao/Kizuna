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
import com.kizuna.cast.domain.CastInvitation;
import com.kizuna.cast.domain.CastInvitationRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final CastInvitationRepository invitations;
  private final StoreRepository stores;
  private final StoreContext storeContext;

  @StoreScoped
  @Transactional
  public CastEnrollmentStatusResponse suspend(String id, String actorEmail) {
    CastEnrollment enrollment = requireLocked(id);
    CastEnrollmentStatus previous = enrollment.getStatus();
    enrollment.suspend();
    record(enrollment, previous, actorEmail, now());
    return response(enrollment);
  }

  @StoreScoped
  @Transactional
  public CastEnrollmentStatusResponse resume(String id, String actorEmail) {
    CastEnrollment enrollment = requireLocked(id);
    CastEnrollmentStatus previous = enrollment.getStatus();
    enrollment.resume();
    record(enrollment, previous, actorEmail, now());
    return response(enrollment);
  }

  @StoreScoped
  @Transactional
  public CastEnrollmentStatusResponse withdraw(String id, String actorEmail) {
    CastEnrollment enrollment = requireLocked(id);
    CastEnrollmentStatus previous = enrollment.getStatus();
    OffsetDateTime at = now();
    enrollment.withdraw(at);
    invitations.invalidatePending(
        id, CastInvitation.Status.PENDING, CastInvitation.Status.INVALIDATED);
    record(enrollment, previous, actorEmail, at);
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
    stores.lockAgainstDeletion(storeContext.getStoreId());
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

  private void record(
      CastEnrollment enrollment,
      CastEnrollmentStatus previous,
      String actorEmail,
      OffsetDateTime at) {
    histories.save(
        CastEnrollmentStatusHistory.builder()
            .enrollmentId(enrollment.getId())
            .previousStatus(previous)
            .newStatus(enrollment.getStatus())
            .actorId(actorId(actorEmail))
            .recordedAt(at)
            .build());
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }

  private static CastEnrollmentStatusResponse response(CastEnrollment enrollment) {
    return new CastEnrollmentStatusResponse(
        enrollment.getId(), enrollment.getStatus(), enrollment.getEndedAt());
  }
}
