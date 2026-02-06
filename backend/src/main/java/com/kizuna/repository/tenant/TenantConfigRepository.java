package com.kizuna.repository.tenant;

import com.kizuna.model.entity.tenant.TenantConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantConfigRepository extends JpaRepository<TenantConfig, Long> {
  Optional<TenantConfig> findByTenantId(Long tenantId);

  boolean existsByTenantId(Long tenantId);
}
