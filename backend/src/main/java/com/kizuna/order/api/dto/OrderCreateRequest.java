package com.kizuna.order.api.dto;

import com.kizuna.order.domain.ReceptionRoute;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Data;

@Data
public class OrderCreateRequest {
  @Size(min = 1, max = 64)
  private String replacementForOrderId;

  private List<String> specialServiceIds;
  private String confirmationToken;

  @NotBlank(message = "コースは必須です")
  private String courseId;

  /**
   * 受付担当。省略すると実行者本人が受付担当として書き込まれる（確定操作と同じ適格述語で判定し、適格でなければ 400）。
   *
   * <p>契約で必須にしないのは、店舗スタッフの大多数にとって受付担当が自分自身であり、毎回自分を選び直す手間を強いるため。 前端は既定でこの項目を省略して送る — JWT にも {@code
   * /platform/me} にも利用者 id が無く、画面の側で「自分」を選択値として組み立てられない。
   */
  private Long receptionistId;

  @NotNull private LocalDate businessDate;

  private LocalTime arrivalScheduledStartTime;
  private LocalTime arrivalScheduledEndTime;

  @NotNull @Valid private CustomerSelectionRequest customerSelection;
  @Valid private ContactSnapshotRequest contactSnapshot;

  @Valid
  @Size(max = 3, message = "今回の連絡可否は3件以内で指定してください")
  private List<@NotNull(message = "連絡可否の要素は必須です") BusinessContactPermissionRequest>
      businessContactPermissions;

  @Size(max = 500, message = "住所は 500 文字以内です")
  private String address;

  @Size(max = 255, message = "建物名は 255 文字以内です")
  private String buildingName;

  @NotBlank(message = "キャストIDは必須です")
  private String castId;

  @Min(value = 1, message = "人数は 1 以上です")
  private Integer pax;

  /** 受注金額の内訳。省略は「内訳なし」で、合計は 0 になる。 */
  @Valid private List<@NotNull(message = "明細の要素は必須です") OrderFeeLineRequest> feeLines;

  /**
   * 受付経路。実際の受付手段を記録する値で、未指定は「不明」を意味する（既定値で補完しない）。
   *
   * <p>{@code MEMBER_WEB} / {@code GUEST_WEB} は予約申請の確定だけが書く値のため、この契約では拒否される（{@link
   * com.kizuna.order.application.OrderService#create}）。広告費・効果集計の根拠になる経路記録が代理入力で偽装されないようにするため。
   */
  private ReceptionRoute receptionRoute;

  /** t_orders.carrier = VARCHAR(100)。 */
  @Size(max = 100, message = "キャリアは 100 文字以内です")
  private String carrier;

  /** t_orders.media_name = VARCHAR(100)。 */
  @Size(max = 100, message = "媒体名は 100 文字以内です")
  private String mediaName;

  // 備考とメッセージの行き先は TEXT のため上限を持たない
  private String remarks;
  private String castDriverMessage;
}
