package com.kizuna.cast.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CastCreateRequest {
  @NotBlank private String name;

  /**
   * 在籍状態。省略は既定の在籍中（{@code CastMapper} の defaultValue）。
   *
   * <p>2 択に縛るのは、この値が指名の可否と候補一覧の判定根拠になるため — 打ち間違いはそのキャストを恒久的に指名不能かつ候補から不可視にする。
   */
  @Pattern(regexp = "ENROLLED|SUSPENDED", message = "在籍状態は ENROLLED または SUSPENDED です")
  private String status;

  /** 入店日。未来日も受け付ける（入店予定の先行登録）。 */
  @NotNull(message = "入店日を入力してください")
  private LocalDate startedOn;

  private String photoUrl;
  private String introduction;
  private Integer age;
  private Integer height;
  private Integer bust;
  private Integer waist;
  private Integer hip;
  private Integer displayOrder;
}
