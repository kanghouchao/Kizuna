package com.kizuna.model.dto.central;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminDto {
  private final Long id;
  private final String name;
  private final String username;
}
