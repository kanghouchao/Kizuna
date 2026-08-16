package com.kizuna.menu.api.dto;

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
}
