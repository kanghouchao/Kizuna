package com.kizuna.menu.api.dto;

import com.kizuna.menu.application.MenuTree;
import com.kizuna.user.domain.PermissionTree;
import java.util.List;

public record MenuTreeResponse(PermissionTree tree, List<MenuVO> items) {

  public static MenuTreeResponse of(MenuTree tree) {
    return new MenuTreeResponse(tree.tree(), tree.items().stream().map(MenuVO::of).toList());
  }
}
