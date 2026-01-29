package com.kizuna.model.dto.menu;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MenuVO {
  private String name; // Label
  private String path; // Href
  private String icon;
  private List<MenuVO> items; // Children
}
