package com.kizuna.user.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface PlatformUserRepository extends CrudRepository<PlatformUser, Long> {
  Optional<PlatformUser> findByEmail(String email);

  List<PlatformUser> findByRoleInOrderByDisplayNameAsc(Collection<PlatformRole> roles);
}
