package com.kizuna.controller.central;

import com.kizuna.model.dto.menu.MenuVO;
import com.kizuna.service.central.menu.CentralMenuService;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/central/menus")
@RequiredArgsConstructor
public class CentralMenuController {

  private final CentralMenuService menuService;

  @GetMapping("/me")
  @RolesAllowed("ADMIN")
  public ResponseEntity<List<MenuVO>> getMyMenus() {
    return ResponseEntity.ok(menuService.getMyMenus());
  }
}
