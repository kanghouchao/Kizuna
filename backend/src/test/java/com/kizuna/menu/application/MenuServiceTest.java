package com.kizuna.menu.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.kizuna.menu.api.dto.MenuVO;
import com.kizuna.menu.domain.Menu;
import com.kizuna.menu.domain.MenuRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

  @Mock private MenuRepository menuRepository;
  @Mock private SecurityContext securityContext;
  @Mock private Authentication authentication;

  @InjectMocks private MenuService menuService;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private Menu menu(String label, String permission) {
    Menu menu = new Menu();
    menu.setLabel(label);
    menu.setPermission(permission);
    menu.setChildren(List.of());
    return menu;
  }

  @Test
  void getMyMenus_filtersRootsByPermission() {
    SecurityContextHolder.setContext(securityContext);
    when(securityContext.getAuthentication()).thenReturn(authentication);

    List<? extends GrantedAuthority> authorities =
        List.of(
            new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("PERM_ORDER_READ"));
    doReturn(authorities).when(authentication).getAuthorities();

    Menu visible = menu("Orders", "ORDER_READ"); // PERM_ORDER_READ として評価される
    Menu hidden = menu("Secret", "SECRET_READ"); // 権限に存在しない

    when(menuRepository.findByParentIsNullOrderBySortOrderAsc())
        .thenReturn(List.of(visible, hidden));

    List<MenuVO> result = menuService.getMyMenus();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("Orders");
  }

  @Test
  void getMyMenus_returnsAllWhenNoPermissionRequired() {
    SecurityContextHolder.setContext(securityContext);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    doReturn(List.of()).when(authentication).getAuthorities();

    when(menuRepository.findByParentIsNullOrderBySortOrderAsc())
        .thenReturn(List.of(menu("Public", null)));

    List<MenuVO> result = menuService.getMyMenus();

    assertThat(result).hasSize(1);
  }

  @Test
  void getMyMenus_filtersChildrenByPermission() {
    SecurityContextHolder.setContext(securityContext);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    doReturn(List.of(new SimpleGrantedAuthority("PERM_CAST_MANAGE")))
        .when(authentication)
        .getAuthorities();

    Menu visibleChild = menu("キャスト管理", "CAST_MANAGE");
    Menu hiddenChild = menu("出勤管理", "SHIFT_MANAGE"); // 権限に存在しない
    Menu root = new Menu();
    root.setLabel("HRM");
    root.setPermission(null); // グループ見出しは無条件可視
    root.setChildren(List.of(visibleChild, hiddenChild));

    when(menuRepository.findByParentIsNullOrderBySortOrderAsc()).thenReturn(List.of(root));

    List<MenuVO> result = menuService.getMyMenus();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getItems()).hasSize(1);
    assertThat(result.get(0).getItems().get(0).getName()).isEqualTo("キャスト管理");
  }

  @Test
  void getMyMenus_returnsEmptyWhenAuthenticationMissing() {
    SecurityContextHolder.clearContext();
    when(menuRepository.findByParentIsNullOrderBySortOrderAsc()).thenReturn(List.of());

    List<MenuVO> result = menuService.getMyMenus();

    assertThat(result).isEmpty();
  }
}
