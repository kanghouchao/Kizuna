package com.kizuna.service.tenant.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.model.dto.menu.MenuVO;
import com.kizuna.model.entity.tenant.menu.TenantMenu;
import com.kizuna.repository.tenant.menu.TenantMenuRepository;
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
class TenantMenuServiceImplTest {

  @Mock private TenantMenuRepository menuRepository;
  @Mock private TenantContext tenantContext;
  @InjectMocks private TenantMenuServiceImpl menuService;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getMyMenus_returnsEmpty_whenNoTenant() {
    when(tenantContext.getTenantId()).thenReturn(null);
    assertThat(menuService.getMyMenus()).isEmpty();
  }

  @Test
  void getMyMenus_returnsFilteredTenantMenus() {
    when(tenantContext.getTenantId()).thenReturn(1L);

    Authentication auth = mock(Authentication.class);
    doReturn(List.of(new SimpleGrantedAuthority("PERM_ORDER_READ")))
        .when(auth)
        .getAuthorities();
    SecurityContext context = mock(SecurityContext.class);
    when(context.getAuthentication()).thenReturn(auth);
    SecurityContextHolder.setContext(context);

    TenantMenu m1 = new TenantMenu();
    m1.setLabel("Dashboard");
    m1.setPermission(null);

    TenantMenu m2 = new TenantMenu();
    m2.setLabel("Orders");
    m2.setPermission("ORDER_READ");

    TenantMenu m3 = new TenantMenu();
    m3.setLabel("Secret");
    m3.setPermission("ADMIN");

    when(menuRepository.findByTenantIdAndParentIsNullOrderBySortOrderAsc(1L))
        .thenReturn(List.of(m1, m2, m3));

    List<MenuVO> results = menuService.getMyMenus();

    assertThat(results).hasSize(2);
    assertThat(results).extracting(MenuVO::getName).containsExactly("Dashboard", "Orders");
  }
}
