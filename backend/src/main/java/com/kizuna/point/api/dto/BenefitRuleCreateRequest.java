package com.kizuna.point.api.dto;

import com.kizuna.point.domain.BenefitRuleRepeatPolicy;
import com.kizuna.point.domain.BenefitRuleType;
import com.kizuna.user.domain.StoreScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;
import lombok.Data;

/**
 * 特典規則の新規作成。種別はここでだけ決まり、以後動かない（更新の要求型には存在しない）。
 *
 * <p>種別・適用店舗の種別・重複可否を enum 型で受けるので、目録に無い値は束縛の段階で 400 になる。
 *
 * <p>点数は種別が形を決める — 紹介は紹介者点数・被紹介者点数、それ以外は付与ポイント。組合せの検証は集約が担う。
 */
@Data
public class BenefitRuleCreateRequest {

  @NotBlank(message = "規則名は必須です")
  @Size(max = 100, message = "規則名は 100 文字以内で入力してください")
  private String name;

  @NotNull(message = "種別は必須です")
  private BenefitRuleType type;

  @NotNull(message = "適用店舗の種別は必須です")
  private StoreScopeType storeScopeType;

  /** 発火を拾う店舗。全店舗の規則では空で送る。 */
  private Set<Long> storeIds;

  /** 規則の適用期間（発火の窓）。省略は常設。 */
  private LocalDate effectiveFrom;

  private LocalDate effectiveUntil;

  /** 付与ポイントの有効期間（日数）。省略は無期限。 */
  private Integer grantValidityDays;

  @NotNull(message = "重複可否は必須です")
  private BenefitRuleRepeatPolicy repeatPolicy;

  private Integer points;

  private Integer referrerPoints;

  private Integer referredPoints;
}
