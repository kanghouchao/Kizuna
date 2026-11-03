package com.kizuna.menu.api.dto;

import com.kizuna.menu.application.MenuNode;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MenuVO {
  private String name;
  private String path;
  private String icon;
  private List<MenuVO> items;

  public static MenuVO of(MenuNode node) {
    return new MenuVO(
        node.name(), node.path(), node.icon(), node.items().stream().map(MenuVO::of).toList());
  }
}
