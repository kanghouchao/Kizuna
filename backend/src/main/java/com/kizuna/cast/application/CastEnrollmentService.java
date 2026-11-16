package com.kizuna.cast.application;

import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastEnrollmentSnapshot;
import com.kizuna.cast.domain.CastEnrollmentSnapshotRepository;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.cast.domain.CastEnrollmentStatusHistory;
import com.kizuna.cast.domain.CastEnrollmentStatusHistoryRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.persistence.Timestamps;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CastEnrollmentService {
  private final CastEnrollmentRepository enrollments;
  private final CastEnrollmentStatusHistoryRepository histories;
  private final CastEnrollmentSnapshotRepository snapshots;
  private final StoreRepository stores;
  private final BusinessAudit audit;

  @Transactional
  public CastEnrollment suspend(Long storeId, String id, long actorId) {
    CastEnrollment enrollment = requireLocked(storeId, id);
    var before = snapshot(enrollment);
    CastEnrollmentStatus previous = enrollment.getStatus();
    enrollment.suspend();
    var history = record(enrollment, previous, actorId, Timestamps.now());
    enrollments.flush();
    recordAudit(enrollment, history, actorId, before);
    return enrollment;
  }

  @Transactional
  public CastEnrollment resume(Long storeId, String id, long actorId) {
    CastEnrollment enrollment = requireLocked(storeId, id);
    var before = snapshot(enrollment);
    CastEnrollmentStatus previous = enrollment.getStatus();
    enrollment.resume();
    var history = record(enrollment, previous, actorId, Timestamps.now());
    enrollments.flush();
    recordAudit(enrollment, history, actorId, before);
    return enrollment;
  }

  @Transactional
  public CastEnrollment withdraw(Long storeId, String id, long actorId) {
    CastEnrollment enrollment = requireLocked(storeId, id);
    var before = snapshot(enrollment);
    CastEnrollmentStatus previous = enrollment.getStatus();
    OffsetDateTime at = Timestamps.now();
    enrollment.withdraw(at);
    var history = record(enrollment, previous, actorId, at);
    enrollments.flush();
    recordAudit(enrollment, history, actorId, before);
    return enrollment;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCreation(CastEnrollment enrollment, long actorId) {
    record(enrollment, null, actorId, Timestamps.now());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void replaceInternalFields(
      CastEnrollment enrollment, Map<String, String> values, long actorId) {
    if (enrollment.getCustomFields().equals(values)) return;
    saveSnapshot(enrollment, actorId);
    enrollment.replaceCustomFields(values);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public List<CastEnrollmentSnapshot> removeInternalField(
      List<CastEnrollment> enrollments, String key, long actorId) {
    var affected =
        enrollments.stream()
            .filter(enrollment -> enrollment.getCustomFields().containsKey(key))
            .toList();
    if (affected.isEmpty()) return List.of();
    var recorded = new ArrayList<CastEnrollmentSnapshot>();
    for (CastEnrollment enrollment : affected) {
      recorded.add(saveSnapshot(enrollment, actorId));
      enrollment.removeCustomField(key);
    }
    return recorded;
  }

  private CastEnrollmentSnapshot saveSnapshot(CastEnrollment enrollment, Long actorId) {
    CastEnrollmentSnapshot snapshot =
        CastEnrollmentSnapshot.builder()
            .enrollmentId(enrollment.getId())
            .actorId(actorId)
            .recordedAt(Timestamps.now())
            .customFields(new HashMap<>(enrollment.getCustomFields()))
            .build();
    snapshot.setStoreId(enrollment.getStoreId());
    return snapshots.save(snapshot);
  }

  @Transactional(readOnly = true)
  public Page<CastEnrollmentStatusHistory> history(Long storeId, String id, Pageable pageable) {
    require(storeId, id);
    return histories.findByStoreIdAndEnrollmentId(storeId, id, pageable);
  }

  @Transactional(readOnly = true)
  public Page<CastEnrollmentSnapshot> snapshots(Long storeId, String id, Pageable pageable) {
    require(storeId, id);
    return snapshots.findByStoreIdAndEnrollmentId(storeId, id, pageable);
  }

  private CastEnrollment require(Long storeId, String id) {
    return enrollments
        .findByIdAndStoreId(id, storeId)
        .orElseThrow(() -> new NotFoundException("在籍が見つかりません"));
  }

  private CastEnrollment requireLocked(Long storeId, String id) {
    stores.lockCastFields(storeId);
    return enrollments
        .findByIdAndStoreIdForUpdate(id, storeId)
        .orElseThrow(() -> new NotFoundException("在籍が見つかりません"));
  }

  private CastEnrollmentStatusHistory record(
      CastEnrollment enrollment, CastEnrollmentStatus previous, long actorId, OffsetDateTime at) {
    CastEnrollmentStatusHistory history =
        CastEnrollmentStatusHistory.builder()
            .enrollmentId(enrollment.getId())
            .previousStatus(previous)
            .newStatus(enrollment.getStatus())
            .actorId(actorId)
            .recordedAt(at)
            .build();
    history.setStoreId(enrollment.getStoreId());
    return histories.save(history);
  }

  private void recordAudit(
      CastEnrollment enrollment,
      CastEnrollmentStatusHistory history,
      long actorId,
      Map<String, String> before) {
    String action =
        switch (enrollment.getStatus()) {
          case SUSPENDED -> "CAST_ENROLLMENT_SUSPENDED";
          case ENROLLED -> "CAST_ENROLLMENT_RESUMED";
          case WITHDRAWN -> "CAST_ENROLLMENT_WITHDRAWN";
        };
    audit.record(
        actorId,
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
}
