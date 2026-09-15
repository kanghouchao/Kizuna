package com.kizuna.service.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ServiceItemRepository
    extends JpaRepository<ServiceItem, String>, JpaSpecificationExecutor<ServiceItem> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from ServiceItem s where s.id = :id")
  Optional<ServiceItem> findForUpdate(String id);
}
