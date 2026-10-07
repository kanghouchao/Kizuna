package com.kizuna.task.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionAttemptRepository
    extends JpaRepository<ExecutionAttempt, Long>, JpaSpecificationExecutor<ExecutionAttempt> {
  Optional<ExecutionAttempt> findFirstByRequestIdOrderByAttemptNumberDesc(Long requestId);

  @Query("select a.requestId from ExecutionAttempt a where a.id = :id")
  Optional<Long> findRequestId(@Param("id") Long id);
}
