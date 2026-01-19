package com.cms.repository.tenant;

import com.cms.model.entity.tenant.Girl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface GirlRepository
    extends JpaRepository<Girl, String>, JpaSpecificationExecutor<Girl> {
  Page<Girl> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
