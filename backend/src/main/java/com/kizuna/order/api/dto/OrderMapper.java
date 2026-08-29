package com.kizuna.order.api.dto;

import com.kizuna.customer.domain.Customer;
import com.kizuna.order.domain.InvalidOrderFeeLineException;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderFeeLine;
import com.kizuna.order.domain.OrderFeeLineDraft;
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
  OrderResponse toResponse(OrderView view);

  /**
   * 明細の入力をドメインの下書きへ変換します。金額は表示上の値で届くため、種別の符号約定に合わせて帯符号へ翻します。
   *
   * <p>符号が減算に固定された種別へ負値が届いたら撥ねる。翻すと正の割引になり、集約の符号検査を素通りして 加算として記録される。
   */
  default List<OrderFeeLineDraft> toFeeLineDrafts(List<OrderFeeLineRequest> requests) {
    if (requests == null) {
      return null;
    }
    return requests.stream()
        .map(
            request -> {
              if (request.getKind() != null
                  && request.getKind().isDeduction()
                  && request.getAmount() != null
                  && request.getAmount() < 0) {
                throw new InvalidOrderFeeLineException("減算の明細の金額は 0 以上で指定してください（引くことは種別が表します）");
              }
              int amount = request.getAmount() == null ? 0 : request.getAmount();
              return new OrderFeeLineDraft(
                  request.getKind(),
                  request.getName(),
                  request.getKind() == null ? amount : request.getKind().signedAmountOf(amount));
            })
        .toList();
  }

  /** 明細をレスポンスへ変換します。保存されている帯符号金額を表示上の値へ翻します。 */
  default List<OrderFeeLineResponse> toFeeLineResponses(List<OrderFeeLine> lines) {
    return lines.stream()
        .map(
            line ->
                OrderFeeLineResponse.builder()
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
  Order toEntity(OrderCreateRequest request);

  /** 注文更新リクエストをドメインの部分更新コマンドに変換します。null フィールドは「変更しない」。 */
  OrderPatch toPatch(OrderUpdateRequest request);

  /** 電話番号からの顧客スマートリンク用に、作成リクエストから顧客行を起こします。 */
  @Mapping(target = "name", source = "customerName")
  @Mapping(target = "lineId", ignore = true)
  @Mapping(target = "usageAreas", ignore = true)
  // 起こしたばかりの行は定義上まだ生きている。統合先参照は統合だけが立てる。
  @Mapping(target = "mergedIntoId", ignore = true)
  Customer toCustomer(OrderCreateRequest request);
}
