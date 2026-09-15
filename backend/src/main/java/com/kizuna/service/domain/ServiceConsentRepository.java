package com.kizuna.service.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceConsentRepository extends JpaRepository<ServiceConsent, String> {
  Optional<ServiceConsent> findByEnrollmentIdAndServiceId(String enrollmentId, String serviceId);

  List<ServiceConsent> findByEnrollmentIdAndServiceIdIn(
      String enrollmentId, Collection<String> serviceIds);
}
