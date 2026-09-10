package com.kizuna.cast.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CastRepository extends JpaRepository<Cast, Long> {
  Optional<Cast> findByPlatformUserId(Long platformUserId);
}
