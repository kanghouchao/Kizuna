package com.kizuna.service.central.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.model.dto.menu.MenuVO;
import com.kizuna.model.entity.central.menu.CentralMenu;
import com.kizuna.repository.central.menu.CentralMenuRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CentralMenuServiceImplTest {

  @Mock private CentralMenuRepository menuRepository;
  @InjectMocks private CentralMenuServiceImpl menuService;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getMyMenus_returnsFilteredMenus() {
    // Mock Authentication with PERM_TENANT_MANAGE
    Authentication auth = mock(Authentication.class);
    doReturn(List.of(new SimpleGrantedAuthority("PERM_TENANT_MANAGE")))
        .when(auth)
        .getAuthorities();
    SecurityContext context = mock(SecurityContext.class);
    when(context.getAuthentication()).thenReturn(auth);
    SecurityContextHolder.setContext(context);

    // Prepare data
    CentralMenu m1 = new CentralMenu();
    m1.setLabel("Main");
    m1.setPermission(null);

    CentralMenu m2 = new CentralMenu();
    m2.setLabel("Tenants");
    m2.setPermission("TENANT_MANAGE");

    CentralMenu m3 = new CentralMenu();
    m3.setLabel("Admin Only");
    m3.setPermission("SUPER_ADMIN");

    when(menuRepository.findByParentIsNullOrderBySortOrderAsc()).thenReturn(List.of(m1, m2, m3));

    // Execute
    List<MenuVO> results = menuService.getMyMenus();

    // Verify
    assertThat(results).hasSize(2);
    assertThat(results).extracting(MenuVO::getName).containsExactly("Main", "Tenants");
  }
}
