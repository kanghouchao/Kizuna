package com.kizuna.user.domain;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

  List<Permission> findByCodeIn(Set<String> codes);
}
