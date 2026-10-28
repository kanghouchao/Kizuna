package com.kizuna.order.application;

import com.kizuna.customer.contact.GuestContactImport;
import com.kizuna.order.domain.OrderQuote;
import java.util.List;

/** 試算の結果。確認値は {@link OrderConsent} だけを署名し、残高・採用時刻・同意の現況は表示のために添える。 */
public record OrderPreview(
    String confirmationToken,
    OrderQuote quote,
    OrderPoints points,
    List<SpecialServiceStatus> specialServices,
    List<GuestContactImport> contactImports) {

  public int unresolvedSpecialServiceCount() {
    return (int) specialServices.stream().filter(SpecialServiceStatus::requiresAttention).count();
  }
}
