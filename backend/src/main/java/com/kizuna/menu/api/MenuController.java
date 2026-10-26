package com.kizuna.menu.api;

import com.kizuna.menu.api.dto.MenuVO;
import com.kizuna.menu.application.MenuService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/menus")
@RequiredArgsConstructor
public class MenuController {

  private final MenuService menuService;

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<MenuVO>> getMyMenus() {
    return ResponseEntity.ok(menuService.getMyMenus());
  }
}
