package com.kizuna.task.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionRequestRepository extends JpaRepository<ExecutionRequest, Long> {
  Optional<ExecutionRequest> findByTaskNameAndLogicalKey(String taskName, String logicalKey);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from ExecutionRequest r where r.id = :id")
  Optional<ExecutionRequest> lockById(@Param("id") Long id);

  @Query(
      value = "select * from t_execution_requests where id = :id for update nowait",
      nativeQuery = true)
  Optional<ExecutionRequest> lockIdleById(@Param("id") Long id);
}
