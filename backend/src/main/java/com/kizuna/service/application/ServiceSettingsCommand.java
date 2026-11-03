package com.kizuna.service.application;

import com.kizuna.service.domain.ChargeType;
import com.kizuna.service.domain.ServiceKind;

/** サービス設定の書き込み命令。種別は作成時だけ受け、更新では既存の行から引き継ぐ。 */
public sealed interface ServiceSettingsCommand {
  record Create(
      ServiceKind kind,
      String name,
      Integer durationMinutes,
      ChargeType chargeType,
      Integer price,
      Integer remuneration)
      implements ServiceSettingsCommand {}

  record Update(
      String name,
      Integer durationMinutes,
      ChargeType chargeType,
      Integer price,
      Integer remuneration,
      long expectedVersion)
      implements ServiceSettingsCommand {}
}
