package com.kizuna.user.domain;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CentralUserRepository extends CrudRepository<CentralUser, Long> {
  Optional<CentralUser> findByUsername(String username);
}
