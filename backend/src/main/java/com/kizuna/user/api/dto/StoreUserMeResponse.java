package com.kizuna.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StoreUserMeResponse {
  private final String id;
  private final String nickname;
  private final String email;
  private final String role;
}
