package com.kizuna.menu.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kizuna.menu.domain.Menu;
import com.kizuna.menu.domain.MenuRepository;
import com.kizuna.user.domain.PermissionTree;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

  @Mock private MenuRepository menuRepository;

  @InjectMocks private MenuService menuService;

  private static Menu leaf(String label, String permission) {
    Menu menu = new Menu();
    menu.setLabel(label);
    menu.setPath("/" + label);
    menu.setPermission(permission);
    menu.setChildren(List.of());
    return menu;
  }

  private static Menu section(PermissionTree tree, String label, Menu... children) {
    Menu menu = new Menu();
    ReflectionTestUtils.setField(menu, "tree", tree);
    menu.setLabel(label);
    menu.setChildren(Arrays.asList(children));
    return menu;
  }

  private static Menu dashboard(PermissionTree tree) {
    Menu menu = leaf("ダッシュボード", null);
    ReflectionTestUtils.setField(menu, "tree", tree);
    return menu;
  }

  private void seed(Menu... roots) {
    when(menuRepository.findByParentIsNullOrderBySortOrderAsc()).thenReturn(List.of(roots));
  }

  private static List<Menu> bothTrees() {
    return List.of(
        dashboard(PermissionTree.GROUP),
        section(PermissionTree.GROUP, "組織", leaf("従業員管理", "STAFF_MANAGE")),
        dashboard(PermissionTree.STORE),
        section(
            PermissionTree.STORE,
            "キャスト",
            leaf("キャスト管理", "CAST_MANAGE"),
            leaf("出勤管理", "SHIFT_MANAGE")));
  }

  @Test
  void 店舗の木の権限だけを持つ者には店舗の木だけを返す() {
    seed(bothTrees().toArray(Menu[]::new));

    List<MenuTree> result = menuService.getMyMenus(Set.of("PERM_CAST_MANAGE"));

    assertThat(result).extracting(MenuTree::tree).containsExactly(PermissionTree.STORE);
    assertThat(result.getFirst().items())
        .extracting(MenuNode::name)
        .containsExactly("ダッシュボード", "キャスト");
    assertThat(result.getFirst().items().get(1).items())
        .extracting(MenuNode::name)
        .containsExactly("キャスト管理");
  }

  @Test
  void グループの木の権限だけを持つ者にはグループの木だけを返す() {
    seed(bothTrees().toArray(Menu[]::new));

    assertThat(menuService.getMyMenus(Set.of("PERM_STAFF_MANAGE")))
        .extracting(MenuTree::tree)
        .containsExactly(PermissionTree.GROUP);
  }

  @Test
  void 両方の木の権限を持つ者にはグループ店舗の順で両方を返す() {
    seed(bothTrees().toArray(Menu[]::new));

    assertThat(menuService.getMyMenus(Set.of("PERM_SHIFT_MANAGE", "PERM_STAFF_MANAGE")))
        .extracting(MenuTree::tree)
        .containsExactly(PermissionTree.GROUP, PermissionTree.STORE);
  }

  @Test
  void メニュー項目の無い権限でもその木は出てダッシュボードだけが並ぶ() {
    seed(bothTrees().toArray(Menu[]::new));

    List<MenuTree> result = menuService.getMyMenus(Set.of("PERM_MEMBER_WITHDRAW"));

    assertThat(result).extracting(MenuTree::tree).containsExactly(PermissionTree.GROUP);
    assertThat(result.getFirst().items()).extracting(MenuNode::name).containsExactly("ダッシュボード");
  }

  @Test
  void どの木の権限も持たない者には何も返さない() {
    seed(bothTrees().toArray(Menu[]::new));

    assertThat(menuService.getMyMenus(Set.of("ROLE_USER", "PERM_NOT_A_PERMISSION_CODE"))).isEmpty();
  }

  @Test
  void 権限を一つも持たない者には何も返さない() {
    seed(bothTrees().toArray(Menu[]::new));

    assertThat(menuService.getMyMenus(Set.of())).isEmpty();
  }

  @Test
  void 見出しの権限は見ず可視な子があれば出し子が全て落ちた見出しは出さない() {
    Menu heading = section(PermissionTree.STORE, "キャスト", leaf("キャスト管理", "CAST_MANAGE"));
    heading.setPermission("SOMETHING_NOBODY_HOLDS");
    seed(heading, section(PermissionTree.STORE, "顧客", leaf("顧客一覧", "CUSTOMER_MANAGE")));

    assertThat(menuService.getMyMenus(Set.of("PERM_CAST_MANAGE")).getFirst().items())
        .extracting(MenuNode::name)
        .containsExactly("キャスト");
  }
}
