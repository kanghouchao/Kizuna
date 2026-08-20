package com.kizuna.order.api.dto;

import com.kizuna.order.domain.OrderFeeLineKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 受注明細 1 行の入力。
 *
 * <p>金額は<b>表示上の値</b>で受ける — 符号が減算に固定された種別（割引・ポイント利用）では「いくら引くか」を正値で送り、符号は種別が決める。
 * 手動調整だけが帯符号のまま入る（合計を機械和から外す唯一の口なので符号を縛らない）。
 *
 * <p>名称は基本コース料金では使わない（受注のコース名の写しを集約が当てる）。上限は行き先の列と同じ（{@code t_order_fee_lines.name} =
 * VARCHAR(255)）。
 */
@Data
public class OrderFeeLineRequest {

  @NotNull(message = "明細の種別は必須です")
  private OrderFeeLineKind kind;

  @Size(max = 255, message = "明細の名称は 255 文字以内です")
  private String name;

  @NotNull(message = "明細の金額は必須です")
  private Integer amount;
}
