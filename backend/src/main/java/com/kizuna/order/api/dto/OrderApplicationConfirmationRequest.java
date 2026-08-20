package com.kizuna.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/**
 * 予約申請の確定内容。確定は申請内容を予填した<b>受注の作成操作</b>で、店舗が補完・調整した値がそのまま受注になる（申請原文は動かない）。
 *
 * <p>ここに無い項目（割引・媒体・派遣先など）は、確定後の受注を汎用更新（{@code PUT /store/orders/{id}}）で整える — 確定は来店の約束に要る項目だけを決める。
 */
@Data
public class OrderApplicationConfirmationRequest {

  /** 受付担当。省略すると、実行者本人が受付候補の条件を満たす場合にだけ補われる（満たさなければ未設定のまま）。 */
  private Long receptionistId;

  @NotNull(message = "営業日は必須です")
  private LocalDate businessDate;

  private LocalTime arrivalScheduledStartTime;
  private LocalTime arrivalScheduledEndTime;

  /** 指名するキャスト。null は指名なし（会員は指名なしで申請できるため、確定でも強制しない）。 */
  private String castId;

  @Min(value = 1, message = "人数は 1 以上です")
  private Integer pax;

  private Integer courseMinutes;

  // 備考の行き先（t_orders.remarks）は TEXT のため上限を持たない
  private String remarks;

  /**
   * ゲスト申請の受注を着ける既存の台帳行。{@link #newCustomer} との併用は撥ねる。
   *
   * <p>どちらも省略すると顧客未設定のまま成立し、申請の連絡先が受注側へ写る（無帰属受注は正規の状態）。
   *
   * <p>会員申請では受け付けない — 顧客は会員の「今の関連」だけが決める一本道であり（ADR 0008）、 店員が別の行を選べると完了時のポイントが別会員へ積まれる。
   */
  private String customerId;

  /** ゲスト申請の受注のために新しく起こす台帳行。画面は申請の連絡先を予填する。 */
  @Valid private NewCustomer newCustomer;

  /**
   * 新規に起こす台帳行の入力。電話番号での自動照合は行わない（ADR 0009）— 機械が 1 行を選ぶことが誤帰属の入口になるため、 既存の行へ着けるかどうかは店員が {@link
   * #customerId} で名指す。
   */
  @Data
  public static class NewCustomer {

    // 文字列は行き先の列と同じ上限を契約で持つ（t_customers.name = VARCHAR(255)、phone_number = VARCHAR(50)）
    @NotBlank(message = "お客様名は必須です")
    @Size(max = 255, message = "お客様名は 255 文字以内です")
    private String name;

    @Size(max = 50, message = "電話番号は 50 文字以内です")
    private String phoneNumber;
  }
}
