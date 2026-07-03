package com.kizuna.service.tenant.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.kizuna.model.dto.menu.MenuVO;
import com.kizuna.model.entity.tenant.menu.TenantMenu;
import com.kizuna.repository.tenant.menu.TenantMenuRepository;
import com.kizuna.shared.tenancy.TenantContext;
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
class TenantMenuServiceImplTest {

  @Mock private TenantMenuRepository menuRepository;
  @Mock private TenantContext tenantContext;
  @Mock private SecurityContext securityContext;
  @Mock private Authentication authentication;

  @InjectMocks private TenantMenuServiceImpl menuService;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getMyMenus_filtersByPermission() {
    SecurityContextHolder.setContext(securityContext);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(tenantContext.getTenantId()).thenReturn(1L);

    List<? extends GrantedAuthority> authorities =
        List.of(
            new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("PERM_ORDER_READ"));
    doReturn(authorities).when(authentication).getAuthorities();

    TenantMenu m1 = new TenantMenu();
    m1.setLabel("Orders");
    m1.setPermission("ORDER_READ"); // PERM_ORDER_READ として評価される
    m1.setChildren(List.of());

    TenantMenu m2 = new TenantMenu();
    m2.setLabel("Config");
    m2.setPermission("ADMIN_ONLY"); // 権限に存在しない
    m2.setChildren(List.of());

    when(menuRepository.findByTenantIdAndParentIsNullOrderBySortOrderAsc(1L))
        .thenReturn(List.of(m1, m2));

    List<MenuVO> result = menuService.getMyMenus();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("Orders");
  }

  @Test
  void getMyMenus_returnsEmptyWhenTenantIdMissing() {
    when(tenantContext.getTenantId()).thenReturn(null);

    List<MenuVO> result = menuService.getMyMenus();

    assertThat(result).isEmpty();
  }
}
