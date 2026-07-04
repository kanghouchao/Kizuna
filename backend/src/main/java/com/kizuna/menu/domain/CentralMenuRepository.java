package com.kizuna.menu.domain;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CentralMenuRepository extends JpaRepository<CentralMenu, Long> {
  @EntityGraph(attributePaths = {"children"})
  List<CentralMenu> findByParentIsNullOrderBySortOrderAsc();
}
