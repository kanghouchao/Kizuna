package com.kizuna.menu.application;

import com.kizuna.menu.domain.MenuRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuService {

  private final MenuRepository menuRepository;

  @Transactional(readOnly = true)
  public List<MenuTree> getMyMenus(Set<String> authorities) {
    return MenuTreeAssembler.assemble(
        menuRepository.findByParentIsNullOrderBySortOrderAsc(), authorities);
  }
}
