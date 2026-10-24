package com.kizuna.service.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceConsentRepository extends StoreScopedRepository<ServiceConsent> {
  Optional<ServiceConsent> findByStoreIdAndEnrollmentIdAndServiceId(
      Long storeId, String enrollmentId, String serviceId);

  List<ServiceConsent> findByStoreIdAndEnrollmentIdAndServiceIdIn(
      Long storeId, String enrollmentId, Collection<String> serviceIds);
}
