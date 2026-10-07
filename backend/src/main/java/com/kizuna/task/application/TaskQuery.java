package com.kizuna.task.application;

import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.task.api.dto.ExecutionSummary;
import com.kizuna.task.domain.ExecutionAttempt;
import com.kizuna.task.domain.ExecutionRequest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskQuery {
  private final EntityManager entityManager;

  public CursorPage<ExecutionSummary> list(String cursor, int requestedSize) {
    int size = CursorPage.clampSize(requestedSize);
    String jpql = "select a from ExecutionAttempt a";
    PageCursor after = cursor == null ? null : PageCursor.decode(cursor);
    if (after != null) jpql += " where a.startedAt < :at or (a.startedAt = :at and a.id < :id)";
    var query =
        entityManager.createQuery(
            jpql + " order by a.startedAt desc, a.id desc", ExecutionAttempt.class);
    if (after != null)
      query.setParameter("at", after.timestampKey()).setParameter("id", after.longId());
    var rows = query.setMaxResults(size + 1).getResultList();
    if (rows.isEmpty()) return new CursorPage<>(List.of(), null);
    var requests =
        entityManager
            .createQuery(
                "select r from ExecutionRequest r where r.id in :ids", ExecutionRequest.class)
            .setParameter(
                "ids", rows.stream().map(ExecutionAttempt::getRequestId).distinct().toList())
            .getResultStream()
            .collect(Collectors.toMap(ExecutionRequest::getId, request -> request));
    return CursorPage.of(
            rows,
            size,
            row -> new PageCursor(row.getStartedAt().toString(), row.getId().toString()).encode())
        .map(
            attempt -> {
              var request = requests.get(attempt.getRequestId());
              return new ExecutionSummary(
                  attempt.getId(),
                  request.getId(),
                  attempt.getAttemptNumber(),
                  request.getTaskName(),
                  request.getServiceUserId(),
                  attempt.getServiceName(),
                  request.getStoreId(),
                  attempt.getStoreName(),
                  request.getPeriodStart(),
                  request.getPeriodEnd(),
                  attempt.getOrigin(),
                  attempt.getStatus().name(),
                  attempt.getStartedAt(),
                  attempt.getFinishedAt(),
                  attempt.getProcessedCount(),
                  attempt.getFailureCode());
            });
  }
}
