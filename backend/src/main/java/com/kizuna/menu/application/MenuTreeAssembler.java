package com.kizuna.menu.application;

import com.kizuna.menu.api.dto.MenuVO;
import com.kizuna.menu.domain.MenuNode;
import com.kizuna.user.domain.Authorities;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** メニュー木の組み立て（権限フィルタ + VO 変換）。Central / Store 両作用域が共用する唯一の実装。 */
final class MenuTreeAssembler {

  private MenuTreeAssembler() {}

  static List<MenuVO> assemble(List<? extends MenuNode> roots) {
    Set<String> userAuthorities = currentUserAuthorities();
    return roots.stream()
        .filter(menu -> visible(menu, userAuthorities))
        .map(menu -> toVO(menu, userAuthorities))
        .collect(Collectors.toList());
  }

  private static MenuVO toVO(MenuNode menu, Set<String> userAuthorities) {
    List<MenuVO> children =
        menu.getChildren() == null
            ? Collections.emptyList()
            : menu.getChildren().stream()
                .filter(child -> visible(child, userAuthorities))
                .map(child -> toVO(child, userAuthorities))
                .collect(Collectors.toList());

    return new MenuVO(menu.getLabel(), menu.getPath(), menu.getIcon(), children);
  }

  private static boolean visible(MenuNode menu, Set<String> userAuthorities) {
    if (menu.getPermission() == null || menu.getPermission().isEmpty()) {
      return true;
    }
    return userAuthorities.contains(Authorities.permission(menu.getPermission()));
  }

  private static Set<String> currentUserAuthorities() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return Collections.emptySet();
    }
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }
}
