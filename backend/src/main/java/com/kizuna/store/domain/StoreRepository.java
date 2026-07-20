package com.kizuna.store.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface StoreRepository
    extends PagingAndSortingRepository<Store, Long>, CrudRepository<Store, Long> {

  Page<Store> findByNameContainingIgnoreCaseOrDomainContainingIgnoreCase(
      String name, String domain, Pageable pageable);

  Optional<Store> findByDomain(String domain);
}
