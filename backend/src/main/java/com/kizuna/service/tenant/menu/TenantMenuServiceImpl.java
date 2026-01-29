package com.kizuna.service.tenant.menu;

import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.model.dto.menu.MenuVO;
import com.kizuna.model.entity.tenant.menu.TenantMenu;
import com.kizuna.repository.tenant.menu.TenantMenuRepository;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantMenuServiceImpl implements TenantMenuService {

  private final TenantMenuRepository menuRepository;
  private final TenantContext tenantContext;

  @Override
  @Transactional(readOnly = true)
  public List<MenuVO> getMyMenus() {
    Long tenantId = tenantContext.getTenantId();
    if (tenantId == null) {
      return Collections.emptyList();
    }
    Set<String> userPermissions = getUserPermissions();
    return menuRepository.findByTenantIdAndParentIsNullOrderBySortOrderAsc(tenantId).stream()
        .filter(menu -> hasPermission(menu, userPermissions))
        .map(menu -> this.toVO(menu, userPermissions))
        .collect(Collectors.toList());
  }

  private MenuVO toVO(TenantMenu menu, Set<String> userPermissions) {
    List<MenuVO> children =
        menu.getChildren() == null
            ? Collections.emptyList()
            : menu.getChildren().stream()
                .filter(child -> hasPermission(child, userPermissions))
                .map(child -> this.toVO(child, userPermissions))
                .collect(Collectors.toList());

    return new MenuVO(menu.getLabel(), menu.getPath(), menu.getIcon(), children);
  }

  private boolean hasPermission(TenantMenu menu, Set<String> userPermissions) {
    if (menu.getPermission() == null || menu.getPermission().isEmpty()) {
      return true;
    }
    return userPermissions.contains("PERM_" + menu.getPermission());
  }

  private Set<String> getUserPermissions() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return Collections.emptySet();
    }
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }
}
