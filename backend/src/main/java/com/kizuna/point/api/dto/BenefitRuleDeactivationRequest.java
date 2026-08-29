package com.kizuna.point.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 特典規則の停用。再開の口が無い一方通行なので、確認した規則の版を運ぶ。
 *
 * <p>版は一覧の行が持つものをそのまま送る — 操作者が見て停用を決めたのはその行であり、承認の画面を開いている間に別の管理者が 内容を書き換えていれば、見ていない規則を消すことになる。不一致は
 * 409。
 */
@Data
public class BenefitRuleDeactivationRequest {

  @NotNull(message = "停用の対象バージョンは必須です")
  private Long version;
}
