package com.kizuna.menu.domain;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, String> {
  @EntityGraph(attributePaths = {"children"})
  List<Menu> findByParentIsNullOrderBySortOrderAsc();
}
