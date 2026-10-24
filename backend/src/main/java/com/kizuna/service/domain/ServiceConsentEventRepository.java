package com.kizuna.service.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.Optional;

public interface ServiceConsentEventRepository extends StoreScopedRepository<ServiceConsentEvent> {
  Optional<ServiceConsentEvent> findByStoreIdAndConsentIdAndRevisionNumber(
      Long storeId, String consentId, long revisionNumber);
}
