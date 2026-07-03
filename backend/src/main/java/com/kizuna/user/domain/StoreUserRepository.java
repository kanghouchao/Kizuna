package com.kizuna.user.domain;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreUserRepository extends CrudRepository<StoreUser, String> {
  Optional<StoreUser> findByEmail(String email);
}
