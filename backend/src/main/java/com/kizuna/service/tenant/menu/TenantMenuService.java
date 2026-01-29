package com.kizuna.service.tenant.menu;

import com.kizuna.model.dto.menu.MenuVO;
import java.util.List;

public interface TenantMenuService {
  List<MenuVO> getMyMenus();
}
