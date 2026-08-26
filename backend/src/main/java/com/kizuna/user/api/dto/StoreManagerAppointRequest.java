package com.kizuna.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 店長任命リクエスト。JSON キーは Jackson 設定により snake_case。
 *
 * <p>要求は二択で、{@code user_id} を送れば既存アカウントの任命、残り 3 項目を送れば新規作成しての任命になる（初代店長の冷起動）。 双方混在・双方欠落はいずれも 400
 * で、判定は必須注解では表せないため application 層が行う。
 */
@Getter
@Setter
@NoArgsConstructor
public class StoreManagerAppointRequest {

  private Long userId;

  @Email(message = "email format is invalid")
  // 永続化前の小文字化で最大2倍に伸長する文字(U+0130 等)があっても t_users.email VARCHAR(255) に収まる上限。
  @Size(max = 127)
  private String email;

  private String password;

  // t_users.display_name VARCHAR(150)。列長超過は制約名を持たない整合性違反として写像に載らず 500 に落ちるため、
  // 上限は要求の側で止める。
  @Size(max = 150)
  private String displayName;
}
