package com.kizuna.storeprofile.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreProfileRepository extends JpaRepository<StoreProfile, Long> {
  Optional<StoreProfile> findByTenantId(Long tenantId);

  boolean existsByTenantId(Long tenantId);
}
