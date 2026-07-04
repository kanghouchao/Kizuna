package com.kizuna.tenant.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface TenantRepository
    extends PagingAndSortingRepository<Tenant, Long>, CrudRepository<Tenant, Long> {

  Page<Tenant> findByNameContainingIgnoreCaseOrDomainContainingIgnoreCase(
      String name, String domain, Pageable pageable);

  Optional<Tenant> findByDomain(String domain);
}
