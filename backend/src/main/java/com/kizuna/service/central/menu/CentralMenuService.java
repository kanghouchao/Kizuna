package com.kizuna.service.central.menu;

import com.kizuna.model.dto.menu.MenuVO;
import java.util.List;

public interface CentralMenuService {
  List<MenuVO> getMyMenus();
}
