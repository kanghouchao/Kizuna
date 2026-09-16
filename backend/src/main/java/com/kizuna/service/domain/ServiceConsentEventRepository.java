package com.kizuna.service.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceConsentEventRepository extends JpaRepository<ServiceConsentEvent, String> {
  Optional<ServiceConsentEvent> findByConsentIdAndRevisionNumber(
      String consentId, long revisionNumber);
}
