package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderFeeLineRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.domain.InvalidOrderFeeLineException;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderFeeLineDraft;
import com.kizuna.order.domain.OrderFeeLineKind;
import com.kizuna.order.domain.OrderServiceAdoption;
import com.kizuna.service.application.OrderServiceCatalog;
import com.kizuna.service.application.OrderServiceTerms;
import com.kizuna.shared.exception.NotFoundException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 未変更行は受注から、選び直す加算は用途に応じた設定版本から解決する。 */
@Component
@RequiredArgsConstructor
public class OrderFeeLineSelection {
  private final OrderServiceCatalog catalog;
  private final OrderMapper mapper;

  public List<OrderFeeLineDraft> resolve(
      Order original, List<OrderFeeLineRequest> requests, boolean historical, boolean saving) {
    if (requests == null) return original == null ? List.of() : original.editableFeeLines();
    var retained = new HashSet<String>();
    return requests.stream()
        .map(
            request -> {
              if (request == null) throw new InvalidOrderFeeLineException("明細は必須です");
              if (request.getLineId() != null) {
                if (original == null
                    || !retained.add(request.getLineId())
                    || request.getKind() != null
                    || request.getName() != null
                    || request.getAmount() != null
                    || request.getDurationMinutes() != null
                    || request.getRemuneration() != null
                    || request.getServiceId() != null
                    || request.getRevisionId() != null)
                  throw new InvalidOrderFeeLineException("維持する明細は重複なくIDだけで指定してください");
                var line =
                    original.getFeeLines().stream()
                        .filter(
                            item ->
                                item.getId() != null
                                    && item.getId().toString().equals(request.getLineId()))
                        .findFirst()
                        .orElseThrow(() -> new NotFoundException("維持する明細が見つかりません"));
                if (line.getKind() == OrderFeeLineKind.BASE_COURSE
                    || line.getKind().isSystemOwned())
                  throw new InvalidOrderFeeLineException("コースとポイントの明細は直接変更できません");
                return OrderFeeLineDraft.of(line);
              }
              if (request.getKind() != OrderFeeLineKind.SURCHARGE)
                return mapper.toFeeLineDrafts(List.of(request)).getFirst();
              String id = historical ? request.getRevisionId() : request.getServiceId();
              if (id == null
                  || id.isBlank()
                  || request.getName() != null
                  || request.getAmount() != null
                  || request.getDurationMinutes() != null
                  || request.getRemuneration() != null
                  || (historical
                      ? request.getServiceId() != null
                      : request.getRevisionId() != null))
                throw new InvalidOrderFeeLineException("加算は設定から選択してください。名称・単価・報酬は上書きできません");
              var terms =
                  historical
                      ? catalog.historical(id, OrderServiceCatalog.SelectionKind.SURCHARGE)
                      : current(id, saving);
              return new OrderFeeLineDraft(
                  null,
                  OrderFeeLineKind.SURCHARGE,
                  terms.name(),
                  terms.price(),
                  null,
                  terms.remuneration(),
                  new OrderServiceAdoption(
                      terms.serviceId(),
                      terms.revisionId(),
                      terms.revisionNumber(),
                      historical ? "HISTORICAL_CORRECTION" : "CURRENT_SETTING",
                      OffsetDateTime.now()));
            })
        .toList();
  }

  private OrderServiceTerms current(String id, boolean saving) {
    try {
      return catalog.current(id, OrderServiceCatalog.SelectionKind.SURCHARGE);
    } catch (NotFoundException ex) {
      if (saving) throw new OrderConfirmationConflict("confirmation_token");
      throw ex;
    }
  }
}
