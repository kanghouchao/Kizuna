package com.kizuna.shift.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行を書いた操作の実行者（PlatformUser）。
 *
 * <p>利用者が削除されると行側の参照が落ちる（FK が SET NULL）ため、実行者そのものが欠けうる。 キャストが実行主体である申請の提出だけは、他の読み口と揃えて裸の cast_id
 * で表す（キャスト名は 呼び手が既に持つキャスト一覧から解決する）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActorResponse {
  private Long id;
  private String name;
}
