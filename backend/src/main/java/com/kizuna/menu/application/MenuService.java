package com.kizuna.menu.application;

import com.kizuna.menu.domain.MenuRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuService {

  private final MenuRepository menuRepository;

  @Transactional(readOnly = true)
  public List<MenuNode> getMyMenus() {
    return MenuTreeAssembler.assemble(menuRepository.findByParentIsNullOrderBySortOrderAsc());
  }
}
