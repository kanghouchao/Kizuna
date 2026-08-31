package com.kizuna.user.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyElevationRepository extends JpaRepository<EmergencyElevation, Long> {

  List<EmergencyElevation> findByActivatedByAndStatus(
      Long activatedBy, EmergencyElevationStatus status);
}
