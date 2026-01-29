package com.kizuna.service.central.menu;

import com.kizuna.model.dto.menu.MenuVO;
import com.kizuna.model.entity.central.menu.CentralMenu;
import com.kizuna.repository.central.menu.CentralMenuRepository;
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
public class CentralMenuServiceImpl implements CentralMenuService {

  private final CentralMenuRepository menuRepository;

  @Override
  @Transactional(readOnly = true)
  public List<MenuVO> getMyMenus() {
    Set<String> userPermissions = getUserPermissions();
    return menuRepository.findByParentIsNullOrderBySortOrderAsc().stream()
        .filter(menu -> hasPermission(menu, userPermissions))
        .map(menu -> this.toVO(menu, userPermissions))
        .collect(Collectors.toList());
  }

  private MenuVO toVO(CentralMenu menu, Set<String> userPermissions) {
    List<MenuVO> children =
        menu.getChildren() == null
            ? Collections.emptyList()
            : menu.getChildren().stream()
                .filter(child -> hasPermission(child, userPermissions))
                .map(child -> this.toVO(child, userPermissions))
                .collect(Collectors.toList());

    return new MenuVO(menu.getLabel(), menu.getPath(), menu.getIcon(), children);
  }

  private boolean hasPermission(CentralMenu menu, Set<String> userPermissions) {
    if (menu.getPermission() == null || menu.getPermission().isEmpty()) {
      return true;
    }
    // Permissions in DB are like "TENANT_MANAGE"
    // Permissions in SecurityContext (via CustomUserDetailsService) are "PERM_TENANT_MANAGE"
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
