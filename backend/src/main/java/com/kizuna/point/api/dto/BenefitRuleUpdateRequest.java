package com.kizuna.point.api.dto;

import com.kizuna.point.domain.BenefitRuleRepeatPolicy;
import com.kizuna.user.domain.StoreScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;
import lombok.Data;

/**
 * 特典規則の更新（種別以外の五要素の全量置換）。
 *
 * <p>種別をこの型に<b>持たせない</b>のが凍結の表現である。付与仕訳が規則を指し返した後に種別を翻すと、記帳済みの付与の取消方法（受注連動 /
 * 取消なし）が遡って変わる。未知の項目は撥ねられる設定なので、型に無いことがそのまま 400 になる。
 */
@Data
public class BenefitRuleUpdateRequest {

  @NotBlank(message = "規則名は必須です")
  @Size(max = 100, message = "規則名は 100 文字以内で入力してください")
  private String name;

  @NotNull(message = "適用店舗の種別は必須です")
  private StoreScopeType storeScopeType;

  private Set<Long> storeIds;

  private LocalDate effectiveFrom;

  private LocalDate effectiveUntil;

  private Integer grantValidityDays;

  @NotNull(message = "重複可否は必須です")
  private BenefitRuleRepeatPolicy repeatPolicy;

  private Integer points;

  private Integer referrerPoints;

  private Integer referredPoints;
}
