package com.kizuna.user.api.dto;

import com.kizuna.user.application.ServiceIdentityCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * サービスID授権編集リクエスト。ロール×店舗集合のみを更新する（display_name は含めない）。JSON キーは Jackson 設定により snake_case（role_ids /
 * store_ids）。
 */
@Getter
@Setter
@NoArgsConstructor
public class ServiceIdentityUpdateRequest {

  @NotEmpty(message = "role_ids is required")
  private Set<@NotNull(message = "role_ids must not contain null") Long> roleIds;

  // 空集合は「どの店舗も担当しない」で許す。null 要素は永続化時に黙って捨てられ指定と異なる集合が残るため、要素単位で拒む。
  @NotNull(message = "store_ids is required")
  private Set<@NotNull(message = "store_ids must not contain null") Long> storeIds;

  /** 楽観ロック用バージョン（応答の version をそのまま往復する。不一致は 409）。 */
  @NotNull(message = "version is required")
  private Long version;

  public ServiceIdentityCommand.UpdateGrants command() {
    return new ServiceIdentityCommand.UpdateGrants(roleIds, storeIds, version);
  }
}
