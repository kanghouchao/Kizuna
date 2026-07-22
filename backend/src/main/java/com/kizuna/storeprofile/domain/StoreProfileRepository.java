package com.kizuna.storeprofile.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreProfileRepository extends JpaRepository<StoreProfile, String> {
  Optional<StoreProfile> findByStoreId(Long storeId);

  boolean existsByStoreId(Long storeId);
}
