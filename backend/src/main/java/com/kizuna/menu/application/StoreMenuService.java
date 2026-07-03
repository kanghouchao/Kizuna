package com.kizuna.menu.application;

import com.kizuna.menu.api.dto.MenuVO;
import java.util.List;

public interface StoreMenuService {
  List<MenuVO> getMyMenus();
}
