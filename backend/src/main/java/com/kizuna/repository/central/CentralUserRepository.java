package com.kizuna.repository.central;

import com.kizuna.model.entity.central.security.CentralUser;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CentralUserRepository extends CrudRepository<CentralUser, Long> {
  Optional<CentralUser> findByUsername(String username);
}
