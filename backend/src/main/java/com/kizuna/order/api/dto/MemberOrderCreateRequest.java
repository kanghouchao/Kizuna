package com.kizuna.order.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/**
 * 会員ポータルからの予約申請。
 *
 * <p>受付担当・顧客は申請時点では決まらないため受け取らない。受付経路も会員経路であることがサーバ側で自明なため受け取らない。
 */
@Data
public class MemberOrderCreateRequest {

  @NotNull(message = "店舗は必須です")
  private Long storeId;

  @NotNull(message = "利用日は必須です")
  private LocalDate businessDate;

  private LocalTime arrivalScheduledStartTime;

  @NotNull(message = "人数は必須です")
  @Min(value = 1, message = "人数は 1 以上です")
  private Integer pax;

  /** 指名するキャスト。null は指名なし。 */
  private String castId;

  @Size(max = 500, message = "備考は 500 文字以内です")
  private String remarks;
}
