package com.kizuna.order.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/**
 * 公開店面からのゲスト予約申請。店舗は訪問された域名から解決するため、この本体では自称しない。
 *
 * <p>連絡先を必須にするのは、確定が「店舗が折返し連絡で内容を補完・調整する操作」だからで（ADR 0017）、 折返し先の無い申請は処理のしようがない。
 */
@Data
public class GuestOrderApplicationCreateRequest {

  @NotNull(message = "希望日は必須です")
  private LocalDate businessDate;

  private LocalTime arrivalScheduledStartTime;

  @Min(value = 1, message = "人数は 1 以上です")
  private Integer pax;

  /** 希望する指名キャスト。null は指名なし。 */
  private String castId;

  // 文字列はすべて行き先の列と同じ上限を契約で持つ。撥ねないと、溢れた値が挿入時の SQLSTATE 22001
  // になり、理由の分かる 400 ではなく 500 で返る。
  @Size(max = 500, message = "ご要望は 500 文字以内です")
  private String remarks;

  @NotBlank(message = "お名前は必須です")
  @Size(max = 255, message = "お名前は 255 文字以内です")
  private String contactName;

  @NotBlank(message = "電話番号は必須です")
  @Size(max = 50, message = "電話番号は 50 文字以内です")
  private String contactPhoneNumber;
}
