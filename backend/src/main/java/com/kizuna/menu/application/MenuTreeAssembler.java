package com.kizuna.menu.application;

import com.kizuna.menu.api.dto.MenuVO;
import com.kizuna.menu.domain.Menu;
import com.kizuna.user.domain.Authorities;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** メニュー木の組み立て（権限フィルタ + VO 変換）。統合 Menu 集約の唯一の組み立て実装。 */
final class MenuTreeAssembler {

  private MenuTreeAssembler() {}

  static List<MenuVO> assemble(List<Menu> roots) {
    Set<String> userAuthorities = currentUserAuthorities();
    return roots.stream()
        .filter(menu -> visible(menu, userAuthorities))
        .map(menu -> toVO(menu, userAuthorities))
        .filter(MenuTreeAssembler::reachable)
        .collect(Collectors.toList());
  }

  /**
   * 遷移先を持たない根の節（グループ見出し）は、可視な子が 1 つも残らなければ木から落とす。 権限を絞ったロールでは節だけが生き残る組み合わせが普通に起こり、消費側には空の見出しと
   * 「壊れている」の区別が付かないため。判定は根に対してのみ行う — メニューは 2 階層厳守 （seed 02-menus.yaml が正典）で、子は必ず遷移先を持つ葉だからである。3
   * 階層目を導入するなら toVO の子写像にも同じ判定を通すこと。
   */
  private static boolean reachable(MenuVO menu) {
    return menu.getPath() != null || !menu.getItems().isEmpty();
  }

  private static MenuVO toVO(Menu menu, Set<String> userAuthorities) {
    List<MenuVO> children =
        menu.getChildren() == null
            ? Collections.emptyList()
            : menu.getChildren().stream()
                .filter(child -> visible(child, userAuthorities))
                .map(child -> toVO(child, userAuthorities))
                .collect(Collectors.toList());

    return new MenuVO(menu.getLabel(), menu.getPath(), menu.getIcon(), children);
  }

  private static boolean visible(Menu menu, Set<String> userAuthorities) {
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
