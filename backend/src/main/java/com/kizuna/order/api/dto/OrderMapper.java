package com.kizuna.order.api.dto;

import com.kizuna.order.domain.InvalidOrderFeeLineException;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderFeeLine;
import com.kizuna.order.domain.OrderFeeLineDraft;
import com.kizuna.order.domain.OrderFeeLineKind;
import com.kizuna.order.domain.OrderPatch;
import com.kizuna.order.domain.OrderView;
import com.kizuna.order.domain.PlatformOrderView;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 注文エンティティとDTOのマッピングを行うMapStructマッパー。 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

  /** 読み側 projection をレスポンスDTOに変換します。明細は集約から別に載せます。 */
  @Mapping(target = "feeLines", ignore = true)
  @Mapping(target = "totalDurationMinutes", ignore = true)
  @Mapping(target = "totalRemuneration", ignore = true)
  @Mapping(target = "specialServices", ignore = true)
  OrderResponse toResponse(OrderView view);

  default List<OrderFeeLineDraft> toFeeLineDrafts(List<OrderFeeLineRequest> requests) {
    if (requests == null) return null;
    return requests.stream()
        .map(
            request -> {
              if (request.getLineId() != null
                  || request.getServiceId() != null
                  || request.getRevisionId() != null)
                throw new InvalidOrderFeeLineException("設定と既存明細は採用処理で解決してください");
              if (request.getKind() == null
                  || request.getAmount() == null
                  || request.getAmount() < 0)
                throw new InvalidOrderFeeLineException("明細の種別と0以上の整数円を指定してください");
              if (request.getKind() == OrderFeeLineKind.EXTENSION) {
                if (request.getRemuneration() == null || request.getDurationMinutes() == null)
                  throw new InvalidOrderFeeLineException("延長の分数と固定報酬は必須です");
              } else if (request.getRemuneration() != null
                  || request.getDurationMinutes() != null) {
                throw new InvalidOrderFeeLineException("この種別では分数・固定報酬を入力できません");
              }
              return new OrderFeeLineDraft(
                  null,
                  request.getKind(),
                  request.getName(),
                  request.getKind().signedAmountOf(request.getAmount()),
                  request.getDurationMinutes(),
                  request.getRemuneration() == null ? 0 : request.getRemuneration(),
                  null);
            })
        .toList();
  }

  /** 明細をレスポンスへ変換します。保存されている帯符号金額を表示上の値へ翻します。 */
  default List<OrderFeeLineResponse> toFeeLineResponses(List<OrderFeeLine> lines) {
    return lines.stream()
        .map(
            line ->
                OrderFeeLineResponse.builder()
                    .lineId(line.getId() == null ? null : line.getId().toString())
                    .durationMinutes(line.getDurationMinutes())
                    .remuneration(line.getRemuneration())
                    .serviceId(line.getServiceId())
                    .revisionId(line.getAdoption() == null ? null : line.getAdoption().revisionId())
                    .revisionNumber(
                        line.getAdoption() == null ? null : line.getAdoption().revisionNumber())
                    .adoptionBasis(
                        line.getAdoption() == null ? null : line.getAdoption().adoptionBasis())
                    .adoptedAt(line.getAdoption() == null ? null : line.getAdoption().adoptedAt())
                    .kind(line.getKind().name())
                    .name(line.getName())
                    .amount(line.getKind().displayedAmountOf(line.getAmount()))
                    .systemOwned(line.getKind().isSystemOwned())
                    .build())
        .toList();
  }

  /** 読み側 projection を作業キューの行に変換します。 */
  OrderWorkQueueResponse toWorkQueueResponse(OrderView view);

  /** 読み側 projection をアーカイブの行に変換します。 */
  OrderArchiveResponse toArchiveResponse(OrderView view);

  /** 読み側 projection を顧客詳細の注文履歴の行に変換します。 */
  OrderSummaryResponse toSummaryResponse(OrderView view);

  /** 平台横断一覧の projection をレスポンスDTOに変換します（集合作用域）。 */
  PlatformOrderResponse toPlatformResponse(PlatformOrderView view);

  /** 注文作成リクエストDTOを注文エンティティに変換します。 */
  @Mapping(target = "locationAddress", source = "address")
  @Mapping(target = "locationBuilding", source = "buildingName")
  // すべての受注は確定で出生する（ADR 0017）。会員の申請は別記録（OrderApplication）が受け、
  // 店舗の確定操作が同じく CONFIRMED の受注を生む
  @Mapping(target = "status", constant = "CONFIRMED")
  @Mapping(target = "surveyStatus", ignore = true)
  @Mapping(target = "actualArrivalTime", ignore = true)
  @Mapping(target = "actualEndTime", ignore = true)
  // 合計は明細の総和として集約が導出し、付与ポイントは完了処理でのみ確定する
  @Mapping(target = "totalFee", ignore = true)
  @Mapping(target = "feeLines", ignore = true)
  @Mapping(target = "autoGrantPoints", ignore = true)
  // 連絡先の写しは顧客に着かなかった受注にだけ入る（判定はサービス層）
  @Mapping(target = "contactName", ignore = true)
  @Mapping(target = "contactPhoneNumber", ignore = true)
  // 関連 ID - サービス層で存在確認後に割り当て
  @Mapping(target = "customerId", ignore = true)
  @Mapping(target = "castId", ignore = true)
  @Mapping(target = "receptionistId", ignore = true)
  // 申請者は会員ポータル経由の受注だけが持つ
  @Mapping(target = "requesterMemberId", ignore = true)
  @Mapping(target = "requesterMemberCode", ignore = true)
  @Mapping(target = "requesterDeclaredName", ignore = true)
  // 取消の記録は専用の取消操作だけが書く
  @Mapping(target = "cancelledReason", ignore = true)
  @Mapping(target = "cancelledBy", ignore = true)
  @Mapping(target = "cancelledAt", ignore = true)
  @Mapping(target = "course", ignore = true)
  @Mapping(target = "extensionMinutes", ignore = true)
  @Mapping(target = "specialServiceLines", ignore = true)
  @Mapping(target = "startedAt", ignore = true)
  @Mapping(target = "startedBy", ignore = true)
  @Mapping(target = "startReason", ignore = true)
  @Mapping(target = "completedAt", ignore = true)
  @Mapping(target = "accruedRemuneration", ignore = true)
  @Mapping(target = "completionInvalidated", ignore = true)
  Order toEntity(OrderCreateRequest request);

  /** 注文更新リクエストをドメインの部分更新コマンドに変換します。null フィールドは「変更しない」。 */
  @Mapping(target = "feeLines", ignore = true)
  OrderPatch toPatch(OrderUpdateRequest request);
}
