package com.kizuna.task.application;

import com.kizuna.task.api.dto.ExecutionSummary;
import com.kizuna.task.domain.ExecutionAttempt;
import com.kizuna.task.domain.ExecutionAttemptRepository;
import com.kizuna.task.domain.ExecutionRequest;
import com.kizuna.task.domain.ExecutionRequestRepository;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskQuery {
  private final ExecutionAttemptRepository attempts;
  private final ExecutionRequestRepository requests;

  public Page<ExecutionSummary> list(Pageable pageable) {
    var page = attempts.findAll(pageable);
    var byId =
        requests
            .findAllById(page.stream().map(ExecutionAttempt::getRequestId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(ExecutionRequest::getId, Function.identity()));
    return page.map(
        attempt -> {
          var request = byId.get(attempt.getRequestId());
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
