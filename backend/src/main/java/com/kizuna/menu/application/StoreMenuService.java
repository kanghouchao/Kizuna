package com.kizuna.menu.application;

import com.kizuna.menu.api.dto.MenuVO;
import com.kizuna.menu.domain.StoreMenuRepository;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shared.tenancy.TenantScoped;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreMenuService {

  private final StoreMenuRepository menuRepository;
  private final TenantContext tenantContext;

  @TenantScoped
  @Transactional(readOnly = true)
  public List<MenuVO> getMyMenus() {
    Long tenantId = tenantContext.getTenantId();
    if (tenantId == null) {
      return Collections.emptyList();
    }
    return MenuTreeAssembler.assemble(
        menuRepository.findByStoreIdAndParentIsNullOrderBySortOrderAsc(tenantId));
  }
}
