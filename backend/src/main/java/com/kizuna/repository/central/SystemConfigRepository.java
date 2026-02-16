package com.kizuna.repository.central;

import com.kizuna.model.entity.central.SystemConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

  Optional<SystemConfig> findByConfigKey(String configKey);

  List<SystemConfig> findByCategory(String category);
}
