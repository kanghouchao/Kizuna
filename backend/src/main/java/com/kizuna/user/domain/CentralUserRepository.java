package com.kizuna.user.domain;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface CentralUserRepository extends CrudRepository<CentralUser, Long> {
  Optional<CentralUser> findByUsername(String username);
}
