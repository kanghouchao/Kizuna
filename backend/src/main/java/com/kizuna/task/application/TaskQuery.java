package com.kizuna.task.application;

import com.kizuna.task.domain.ExecutionAttempt;
import com.kizuna.task.domain.ExecutionAttemptRepository;
import com.kizuna.task.domain.ExecutionRequest;
import com.kizuna.task.domain.ExecutionRequestRepository;
import com.kizuna.task.execution.ExecutionResult;
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

  public Page<ExecutionResult> list(Pageable pageable) {
    var page = attempts.findAll(pageable);
    var byId =
        requests
            .findAllById(page.stream().map(ExecutionAttempt::getRequestId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(ExecutionRequest::getId, Function.identity()));
    return page.map(attempt -> TaskLifecycle.result(byId.get(attempt.getRequestId()), attempt));
  }
}
