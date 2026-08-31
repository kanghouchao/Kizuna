package com.kizuna.user.domain;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyElevationRepository extends JpaRepository<EmergencyElevation, Long> {

  /** 自然失効は status を書き換えないため、「まだ有効」は期限の述語まで含めて初めて成立する。 */
  List<EmergencyElevation> findByActivatedByAndStatusAndExpiresAtAfter(
      Long activatedBy, EmergencyElevationStatus status, OffsetDateTime expiresAfter);
}
