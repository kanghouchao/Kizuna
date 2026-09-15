package com.kizuna.service.domain;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRevisionRepository extends JpaRepository<ServiceRevision, String> {
  List<ServiceRevision> findByServiceIdOrderByRevisionNumberDesc(String serviceId, Limit limit);

  List<ServiceRevision> findByServiceIdAndRevisionNumberLessThanOrderByRevisionNumberDesc(
      String serviceId, long revisionNumber, Limit limit);
}
