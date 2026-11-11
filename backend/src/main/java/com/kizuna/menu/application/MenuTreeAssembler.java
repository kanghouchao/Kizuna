package com.kizuna.menu.application;

import com.kizuna.menu.domain.Menu;
import com.kizuna.shared.security.Authorities;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionTree;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * メニューの組み立て。木（グループ / 店舗）はその木の権限を一つでも持つ者にだけ返し、レールの文脈の有無はこれで決まる。木の中は項目の permission
 * で絞る。メニュー項目を持たない権限（代行退会など）だけでもその木は出し、パネルにはダッシュボードだけが並ぶ。
 */
final class MenuTreeAssembler {

  private MenuTreeAssembler() {}

  static List<MenuTree> assemble(List<Menu> roots) {
    Viewer viewer = new Viewer(currentUserAuthorities());
    return Arrays.stream(PermissionTree.values())
        .filter(tree -> holdsAny(tree, viewer))
        .map(
            tree ->
                new MenuTree(
                    tree,
                    roots.stream()
                        .filter(menu -> menu.getTree() == tree)
                        .filter(menu -> visible(menu, viewer))
                        .map(menu -> toNode(menu, viewer))
                        .filter(MenuTreeAssembler::reachable)
                        .toList()))
        .toList();
  }

  private static boolean holdsAny(PermissionTree tree, Viewer viewer) {
    return Arrays.stream(PermissionCode.values())
        .anyMatch(
            code -> code.getTree() == tree && viewer.authorities().contains(code.authority()));
  }

  /**
   * 遷移先を持たない根の節（グループ見出し）は、可視な子が 1 つも残らなければ木から落とす。 権限を絞ったロールでは節だけが生き残る組み合わせが普通に起こり、消費側には空の見出しと
   * 「壊れている」の区別が付かないため。判定は根に対してのみ行う — メニューの行は 2 階層厳守 （seed 02-menus.yaml が正典）で、子は必ず遷移先を持つ葉だからである。3
   * 階層目を導入するなら toNode の子写像にも同じ判定を通すこと。
   */
  private static boolean reachable(MenuNode menu) {
    return menu.path() != null || !menu.items().isEmpty();
  }

  private record Viewer(Set<String> authorities) {}

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
   * の無い葉は全員に見える。
   */
  private static boolean visible(Menu menu, Viewer viewer) {
    if (menu.getPath() == null && menu.getChildren() != null && !menu.getChildren().isEmpty()) {
      return menu.getChildren().stream().anyMatch(child -> visible(child, viewer));
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
