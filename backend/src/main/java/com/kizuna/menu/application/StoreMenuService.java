package com.kizuna.menu.application;

import com.kizuna.menu.api.dto.MenuVO;
import com.kizuna.menu.domain.StoreMenu;
import com.kizuna.menu.domain.StoreMenuRepository;
import com.kizuna.shared.tenancy.TenantContext;
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
public class StoreMenuService {

  private final StoreMenuRepository menuRepository;
  private final TenantContext tenantContext;

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

  private MenuVO toVO(StoreMenu menu, Set<String> userPermissions) {
    List<MenuVO> children =
        menu.getChildren() == null
            ? Collections.emptyList()
            : menu.getChildren().stream()
                .filter(child -> hasPermission(child, userPermissions))
                .map(child -> this.toVO(child, userPermissions))
                .collect(Collectors.toList());

    return new MenuVO(menu.getLabel(), menu.getPath(), menu.getIcon(), children);
  }

  private boolean hasPermission(StoreMenu menu, Set<String> userPermissions) {
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
