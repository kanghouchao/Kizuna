package com.kizuna.order.api.dto;

import com.kizuna.order.domain.ReceptionRoute;
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

  private String customerId;

  // 文字列はすべて行き先の列と同じ上限を契約で持つ。撥ねないと、溢れた値が挿入時の SQLSTATE 22001
  // になり、理由の分かる 400 ではなく 500 で返る。連絡先の 2 つは行き先が 2 つあるが上限は同じ
  // （t_customers.name / t_orders.contact_name = VARCHAR(255)、
  // t_customers.phone_number / t_orders.contact_phone_number = VARCHAR(50)）。
  @Size(max = 255, message = "お客様名は 255 文字以内です")
  private String customerName;

  @Size(max = 50, message = "電話番号は 50 文字以内です")
  private String phoneNumber;

  /** t_customers.phone_number2 = VARCHAR(50)。 */
  @Size(max = 50, message = "電話番号2は 50 文字以内です")
  private String phoneNumber2;

  /** 派遣先の住所と顧客台帳の住所を兼ねる。上限はどちらも VARCHAR(500) で一致する。 */
  @Size(max = 500, message = "住所は 500 文字以内です")
  private String address;

  /** 建物名も同じく 2 つの行き先を兼ねる（どちらも VARCHAR(255)）。 */
  @Size(max = 255, message = "建物名は 255 文字以内です")
  private String buildingName;

  /** t_customers.classification = VARCHAR(50)。 */
  @Size(max = 50, message = "区分は 50 文字以内です")
  private String classification;

  /** t_customers.landmark = VARCHAR(255)。 */
  @Size(max = 255, message = "目印は 255 文字以内です")
  private String landmark;

  private Boolean hasPet;

  /** t_customers.ng_type = VARCHAR(50)。 */
  @Size(max = 50, message = "NG 種別は 50 文字以内です")
  private String ngType;

  /** t_customers.ng_content は TEXT のため上限を持たない。 */
  private String ngContent;

  @NotBlank(message = "キャストIDは必須です")
  private String castId;

  @Min(value = 1, message = "人数は 1 以上です")
  private Integer pax;

  private Integer courseMinutes;
  private Integer extensionMinutes;
  private List<String> optionCodes;

  /** t_orders.discount_name = VARCHAR(255)。 */
  @Size(max = 255, message = "割引名は 255 文字以内です")
  private String discountName;

  private Integer manualDiscount;

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
