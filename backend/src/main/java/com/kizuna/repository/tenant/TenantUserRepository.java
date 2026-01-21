package com.kizuna.repository.tenant;

import com.kizuna.model.entity.tenant.security.TenantUser;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantUserRepository extends CrudRepository<TenantUser, String> {
  Optional<TenantUser> findByEmail(String email);
}
