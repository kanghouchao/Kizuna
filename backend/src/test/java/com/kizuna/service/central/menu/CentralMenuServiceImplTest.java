package com.kizuna.service.central.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.kizuna.model.dto.menu.MenuVO;
import com.kizuna.model.entity.central.menu.CentralMenu;
import com.kizuna.repository.central.menu.CentralMenuRepository;
import java.util.List;
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
class CentralMenuServiceImplTest {

  @Mock private CentralMenuRepository menuRepository;
  @Mock private SecurityContext securityContext;
  @Mock private Authentication authentication;

  @InjectMocks private CentralMenuServiceImpl menuService;

  @Test
  void getMyMenus_filtersByPermission() {
    SecurityContextHolder.setContext(securityContext);
    when(securityContext.getAuthentication()).thenReturn(authentication);

    List<? extends GrantedAuthority> authorities =
        List.of(
            new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("PERM_USER_READ"));
    doReturn(authorities).when(authentication).getAuthorities();

    CentralMenu m1 = new CentralMenu();
    m1.setLabel("Users");
    m1.setPermission("USER_READ"); // Will be checked as PERM_USER_READ
    m1.setChildren(List.of());

    CentralMenu m2 = new CentralMenu();
    m2.setLabel("Secret");
    m2.setPermission("SECRET_READ"); // Missing in authorities
    m2.setChildren(List.of());

    when(menuRepository.findByParentIsNullOrderBySortOrderAsc()).thenReturn(List.of(m1, m2));

    List<MenuVO> result = menuService.getMyMenus();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("Users");
  }

  @Test
  void getMyMenus_returnsAllIfNoPermissionRequired() {
    SecurityContextHolder.setContext(securityContext);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    doReturn(List.of()).when(authentication).getAuthorities();

    CentralMenu m1 = new CentralMenu();
    m1.setLabel("Public");
    m1.setPermission(null);
    m1.setChildren(List.of());

    when(menuRepository.findByParentIsNullOrderBySortOrderAsc()).thenReturn(List.of(m1));

    List<MenuVO> result = menuService.getMyMenus();
    assertThat(result).hasSize(1);
  }
}
