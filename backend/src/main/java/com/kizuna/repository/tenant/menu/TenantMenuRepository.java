package com.kizuna.repository.tenant.menu;

import com.kizuna.model.entity.tenant.menu.TenantMenu;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantMenuRepository extends JpaRepository<TenantMenu, String> {
  @EntityGraph(attributePaths = {"children"})
  List<TenantMenu> findByTenantIdAndParentIsNullOrderBySortOrderAsc(Long tenantId);
}
