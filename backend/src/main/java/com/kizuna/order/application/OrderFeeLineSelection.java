package com.kizuna.order.application;

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

  public List<OrderFeeLineDraft> resolve(
      Long storeId,
      Order original,
      List<OrderFeeLineInput> requests,
      boolean historical,
      boolean saving) {
    if (requests == null) return original == null ? List.of() : original.editableFeeLines();
    var retained = new HashSet<String>();
    return requests.stream()
        .map(
            request -> {
              if (request == null) throw new InvalidOrderFeeLineException("明細は必須です");
              if (request.lineId() != null) {
                if (original == null
                    || !retained.add(request.lineId())
                    || request.kind() != null
                    || request.name() != null
                    || request.amount() != null
                    || request.durationMinutes() != null
                    || request.remuneration() != null
                    || request.serviceId() != null
                    || request.revisionId() != null)
                  throw new InvalidOrderFeeLineException("維持する明細は重複なくIDだけで指定してください");
                var line =
                    original.getFeeLines().stream()
                        .filter(
                            item ->
                                item.getId() != null
                                    && item.getId().toString().equals(request.lineId()))
                        .findFirst()
                        .orElseThrow(() -> new NotFoundException("維持する明細が見つかりません"));
                if (line.getKind() == OrderFeeLineKind.SPECIAL_SERVICE
                    || line.getKind() == OrderFeeLineKind.BASE_COURSE
                    || line.getKind().isSystemOwned())
                  throw new InvalidOrderFeeLineException("コースとポイントの明細は直接変更できません");
                return OrderFeeLineDraft.of(line);
              }
              if (request.kind() != OrderFeeLineKind.SURCHARGE) return manual(request);
              String id = historical ? request.revisionId() : request.serviceId();
              if (id == null
                  || id.isBlank()
                  || request.name() != null
                  || request.amount() != null
                  || request.durationMinutes() != null
                  || request.remuneration() != null
                  || (historical ? request.serviceId() != null : request.revisionId() != null))
                throw new InvalidOrderFeeLineException("加算は設定から選択してください。名称・単価・報酬は上書きできません");
              var terms =
                  historical
                      ? catalog.historical(storeId, id, OrderServiceCatalog.SelectionKind.SURCHARGE)
                      : current(storeId, id, saving);
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

  private static OrderFeeLineDraft manual(OrderFeeLineInput request) {
    if (request.serviceId() != null || request.revisionId() != null)
      throw new InvalidOrderFeeLineException("設定と既存明細は採用処理で解決してください");
    if (request.kind() == null || request.amount() == null || request.amount() < 0)
      throw new InvalidOrderFeeLineException("明細の種別と0以上の整数円を指定してください");
    if (request.kind() == OrderFeeLineKind.EXTENSION) {
      if (request.remuneration() == null || request.durationMinutes() == null)
        throw new InvalidOrderFeeLineException("延長の分数と固定報酬は必須です");
    } else if (request.remuneration() != null || request.durationMinutes() != null) {
      throw new InvalidOrderFeeLineException("この種別では分数・固定報酬を入力できません");
    }
    return new OrderFeeLineDraft(
        null,
        request.kind(),
        request.name(),
        request.kind().signedAmountOf(request.amount()),
        request.durationMinutes(),
        request.remuneration() == null ? 0 : request.remuneration(),
        null);
  }

  private OrderServiceTerms current(Long storeId, String id, boolean saving) {
    try {
      return catalog.current(storeId, id, OrderServiceCatalog.SelectionKind.SURCHARGE);
    } catch (NotFoundException ex) {
      if (saving) throw new OrderConfirmationConflict("confirmation_token");
      throw ex;
    }
  }
}
