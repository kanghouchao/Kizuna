package com.kizuna.service.domain;

import com.kizuna.shared.exception.ServiceException;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceTerms {
  @Enumerated(EnumType.STRING)
  private ServiceKind kind;

  private String name;
  private Integer durationMinutes;

  @Enumerated(EnumType.STRING)
  private ChargeType chargeType;

  private Integer price;
  private Integer remuneration;

  public ServiceTerms(
      ServiceKind kind,
      String name,
      Integer durationMinutes,
      ChargeType chargeType,
      Integer price,
      Integer remuneration) {
    if (kind == null || name == null || name.isBlank() || name.strip().length() > 255) {
      throw new ServiceException("種別と 255 文字以内の名称を指定してください");
    }
    if (price == null
        || remuneration == null
        || price < 0
        || remuneration < 0
        || remuneration > price) {
      throw new ServiceException("価格は零以上、報酬は零以上かつ価格以下の整数円で指定してください");
    }
    if (kind == ServiceKind.COURSE) {
      if (durationMinutes == null || durationMinutes <= 0) {
        throw new ServiceException("コースの所要時間は正の整数分で指定してください");
      }
    } else if (durationMinutes != null) {
      throw new ServiceException("所要時間はコースにのみ指定できます");
    }
    if (kind == ServiceKind.SPECIAL_SERVICE) {
      if (chargeType == null) throw new ServiceException("特殊サービスの有料・無料を指定してください");
    } else if (chargeType != null) {
      throw new ServiceException("有料・無料区分は特殊サービスにのみ指定できます");
    }
    if (chargeType == ChargeType.FREE ? price != 0 : price == 0) {
      throw new ServiceException("無料の価格と報酬は零、有料の価格は正の整数円で指定してください");
    }
    this.kind = kind;
    this.name = name.strip();
    this.durationMinutes = durationMinutes;
    this.chargeType = chargeType;
    this.price = price;
    this.remuneration = remuneration;
  }
}
