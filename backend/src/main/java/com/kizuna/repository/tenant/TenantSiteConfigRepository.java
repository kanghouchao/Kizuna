package com.kizuna.repository.tenant;

import com.kizuna.model.entity.tenant.TenantSiteConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantSiteConfigRepository extends JpaRepository<TenantSiteConfig, Long> {
  Optional<TenantSiteConfig> findByTenantId(Long tenantId);

  boolean existsByTenantId(Long tenantId);
}
