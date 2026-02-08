package com.kizuna.repository.tenant;

import com.kizuna.model.entity.tenant.Cast;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CastRepository
    extends JpaRepository<Cast, String>, JpaSpecificationExecutor<Cast> {
  Page<Cast> findByNameContainingIgnoreCase(String name, Pageable pageable);

  List<Cast> findByStatusOrderByDisplayOrderAsc(String status);
}
