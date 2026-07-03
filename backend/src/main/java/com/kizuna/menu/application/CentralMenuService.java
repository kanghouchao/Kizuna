package com.kizuna.menu.application;

import com.kizuna.menu.api.dto.MenuVO;
import java.util.List;

public interface CentralMenuService {
  List<MenuVO> getMyMenus();
}
