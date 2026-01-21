package com.kizuna.repository.central;

import com.kizuna.model.entity.central.tenant.Tenant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository
    extends PagingAndSortingRepository<Tenant, Long>, CrudRepository<Tenant, Long> {

  Page<Tenant> findByNameContainingIgnoreCaseOrDomainContainingIgnoreCase(
      String name, String domain, Pageable pageable);

  Optional<Tenant> findByDomain(String domain);
}
