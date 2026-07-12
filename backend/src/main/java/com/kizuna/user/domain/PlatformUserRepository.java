package com.kizuna.user.domain;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface PlatformUserRepository extends CrudRepository<PlatformUser, Long> {
  Optional<PlatformUser> findByEmail(String email);
}
