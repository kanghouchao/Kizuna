package com.kizuna.settings.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

  Optional<SystemConfig> findByConfigKey(String configKey);

  List<SystemConfig> findByCategory(String category);
}
