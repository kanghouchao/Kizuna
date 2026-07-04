package com.kizuna.menu.domain;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreMenuRepository extends JpaRepository<StoreMenu, String> {
  @EntityGraph(attributePaths = {"children"})
  List<StoreMenu> findByTenantIdAndParentIsNullOrderBySortOrderAsc(Long tenantId);
}
