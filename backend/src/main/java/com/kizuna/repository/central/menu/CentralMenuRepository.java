package com.kizuna.repository.central.menu;

import com.kizuna.model.entity.central.menu.CentralMenu;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CentralMenuRepository extends JpaRepository<CentralMenu, Long> {
  @EntityGraph(attributePaths = {"children"})
  List<CentralMenu> findByParentIsNullOrderBySortOrderAsc();
}
