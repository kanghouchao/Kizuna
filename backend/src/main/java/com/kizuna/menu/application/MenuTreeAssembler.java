package com.kizuna.menu.application;

import com.kizuna.menu.domain.Menu;
import com.kizuna.shared.security.Authorities;
import com.kizuna.shared.storescope.StoreScope;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** メニュー木の組み立て（権限フィルタ + 節への変換）。統合 Menu 集約の唯一の組み立て実装。 */
final class MenuTreeAssembler {

  private MenuTreeAssembler() {}

  static List<MenuNode> assemble(List<Menu> roots, StoreScope scope) {
    Viewer viewer = new Viewer(currentUserAuthorities(), scope != null && scope.allStores());
    return roots.stream()
        .filter(menu -> visible(menu, viewer))
        .map(menu -> toNode(menu, viewer))
        .filter(MenuTreeAssembler::reachable)
        .collect(Collectors.toList());
  }

  /**
   * 遷移先を持たない根の節（グループ見出し）は、可視な子が 1 つも残らなければ木から落とす。 権限を絞ったロールでは節だけが生き残る組み合わせが普通に起こり、消費側には空の見出しと
   * 「壊れている」の区別が付かないため。判定は根に対してのみ行う — メニューは 2 階層厳守 （seed 02-menus.yaml が正典）で、子は必ず遷移先を持つ葉だからである。3
   * 階層目を導入するなら toNode の子写像にも同じ判定を通すこと。
   */
  private static boolean reachable(MenuNode menu) {
    return menu.path() != null || !menu.items().isEmpty();
  }

  private record Viewer(Set<String> authorities, boolean allStores) {}

  private static MenuNode toNode(Menu menu, Viewer viewer) {
    List<MenuNode> children =
        menu.getChildren() == null
            ? Collections.emptyList()
            : menu.getChildren().stream()
                .filter(child -> visible(child, viewer))
                .map(child -> toNode(child, viewer))
                .collect(Collectors.toList());

    return new MenuNode(menu.getLabel(), menu.getPath(), menu.getIcon(), children);
  }

  /**
   * 見出し節（遷移先を持たず子を持つ）は子に見えるものが一つでもあれば出す — 節自身の permission は見ない。葉は permission で判定し、permission
   * の無い葉は全員に見える。全店舗を要する葉（定期処理・監査・グループ級の通知）は、全店舗を担当しない利用者には出さない。
   */
  private static boolean visible(Menu menu, Viewer viewer) {
    if (menu.getPath() == null && menu.getChildren() != null && !menu.getChildren().isEmpty()) {
      return menu.getChildren().stream().anyMatch(child -> visible(child, viewer));
    }
    if (menu.isRequiresAllStores() && !viewer.allStores()) {
      return false;
    }
    if (menu.getPermission() == null || menu.getPermission().isEmpty()) {
      return true;
    }
    return viewer.authorities().contains(Authorities.permission(menu.getPermission()));
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
