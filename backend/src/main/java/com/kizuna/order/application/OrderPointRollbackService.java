package com.kizuna.order.application;

import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.point.application.PointLedgerService.PointRollbackHistory;
import com.kizuna.point.application.PointLedgerService.PointRollbackResult;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受注 1 件を宛先にポイントの授受を打ち消す明示操作（巻き戻し）。
 *
 * <p>受注取消（CONFIRMED / IN_SERVICE → CANCELLED）へは配線しない。付与も利用も完了と事後申領の契機でしか記帳されないため、
 * 取消できる受注に打ち消す仕訳は構造上存在しない。 完了後訂正の門とも繋がない — 門は台帳を読みも書きもしないまま（ADR 0019）で、画面の導線だけがここを指す。
 *
 * <p>店舗の境界を決めるのは受注だけである。台帳も操作記録も グループ帰属で店舗列を持たないため、 先に受注を引き、引けなければそこで終える。
 */
@Service
@RequiredArgsConstructor
public class OrderPointRollbackService {

  private final OrderRepository orderRepository;
  private final OrderAttributionService orderAttributionService;
  private final OrderAttributionRepository orderAttributionRepository;
  private final OrderReceiptTokenRepository orderReceiptTokenRepository;
  private final PointLedgerService pointLedgerService;
  private final ActorIdentityService actorIdentityService;
  private final BusinessAudit businessAudit;

  /**
   * 巻き戻し済みでないことを確かめてから伝票を再発行する。
   *
   * <p>判定と発行を<b>受注行を押さえたままの 1 つの取引</b>で行う。判定だけ別の取引で先に済ませると、その隙間へ
   * 巻き戻しが割り込んだとき、古い「まだ巻き戻されていない」を根拠に、申領が必ず拒む QR を発行してしまう。 巻き戻しも同じ受注行を押さえるので、先に取れたほうが結果を決める。
   *
   * <p>判定をここへ置くのは、再発行のサービスが台帳へ依存を持たないこと自体が「無効化は台帳へ波及しない」（ADR
   * 0009）の構造的な証跡だからである。あちらへ台帳の読みを足すとその証跡が失われる。
   */
  @Transactional
  public String reissueReceiptTokenUnlessRolledBack(
      Long storeId, String orderId, String actorEmail) {
    orderRepository
        .findByIdAndStoreIdForUpdate(orderId, storeId)
        .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + orderId));
    if (pointLedgerService.isRolledBack(orderId)) {
      throw new ServiceException("ポイントを巻き戻した受注には伝票を再発行できません");
    }
    return orderAttributionService.reissueReceiptToken(storeId, orderId, actorEmail);
  }

  /** 実行前の下見。画面はこれで「何がいくら動くか」を示し、済んでいる受注では実行の導線を出さない。 */
  @Transactional
  public OrderPointRollbackPreview preview(Long storeId, String orderId) {
    Order order =
        orderRepository
            .findByIdAndStoreIdForUpdate(orderId, storeId)
            .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + orderId));
    order.requirePointRollbackable();
    PointLedgerService.PointRollbackPreview preview =
        pointLedgerService.previewRollbackForOrder(orderId);
    if (preview.reversibleUsedPoints() + order.getTotalFee() > Integer.MAX_VALUE) {
      throw new ServiceException("相殺後の請求が扱える上限を超えます。受注の内容を確認してください");
    }
    return new OrderPointRollbackPreview(
        preview.alreadyRolledBack(),
        activeMemberCode(storeId, orderId),
        preview.cancellablePoints(),
        preview.reversibleUsedPoints(),
        order.getTotalFee(),
        Math.toIntExact(preview.reversibleUsedPoints()),
        Math.toIntExact(order.getTotalFee() + preview.reversibleUsedPoints()),
        pointLedgerService.rollbackHistory(orderId).orElse(null));
  }

  /**
   * 巻き戻しを実行する。
   *
   * <p>対象は<b>完了した受注だけ</b>。未完了の受注を許すと、記録だけが先に書かれたあとの完了が付与を積み、 その付与を打ち消す手立てが残らない（二度目の巻き戻しは 409
   * で撥ねられる）。取消済みの受注には打ち消す 授受がそもそも無い。
   *
   * <p>押さえる順は受注行 → 伝票トークン行 → 会員の加算ロット（ADR 0016 の向き）。トークン行を押さえるのは事後申領と
   * 直列化するためで、押さえないと申領は操作記録を見ず、巻き戻しは申領の付与を見ないまま双方が成立し、 打ち消せない付与が残る。
   */
  @Transactional
  public PointRollbackHistory rollback(
      Long storeId, String orderId, OrderPointRollbackCommand command, String actorEmail) {
    Order order =
        orderRepository
            .findByIdAndStoreIdForUpdate(orderId, storeId)
            .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + orderId));
    order.requirePointRollbackable();
    orderReceiptTokenRepository.findByOrderIdForUpdate(orderId);
    String beforeVersion = String.valueOf(order.getVersion());
    int beforeTotalFee = order.getTotalFee();
    Long actorUserId = actorIdentityService.requireUserId(actorEmail);
    if (pointLedgerService.isRolledBack(orderId)) {
      throw new ConflictException("この受注のポイントは既に巻き戻されています");
    }
    String attributedBefore = Boolean.toString(activeMemberCode(storeId, orderId) != null);
    long offset = pointLedgerService.previewRollbackForOrder(orderId).reversibleUsedPoints();
    if (!order.getTotalFee().equals(command.expectedTotalFee())
        || offset != command.expectedOffsetAmount().longValue()) {
      throw new ConflictException("請求または相殺額が変わりました。内容を再確認してください");
    }
    PointRollbackResult result =
        pointLedgerService.rollbackForOrder(
            orderId, command.reason().trim(), actorUserId, order.getTotalFee());
    order.offsetPointRedemption(result.restoredPoints());
    orderRepository.flush();
    PointRollbackHistory history = pointLedgerService.rollbackHistory(orderId).orElseThrow();
    businessAudit.recordById(
        actorUserId,
        storeId,
        "ORDER_POINTS_ROLLED_BACK",
        "ORDER",
        orderId,
        "POINT_ROLLBACK",
        history.id(),
        Map.of(
            "version",
            beforeVersion,
            "total_fee",
            Integer.toString(beforeTotalFee),
            "rolled_back",
            "false",
            "attributed",
            attributedBefore),
        Map.of(
            "version", String.valueOf(order.getVersion()),
            "total_fee", order.getTotalFee().toString(),
            "rolled_back", "true",
            "attributed", Boolean.toString(activeMemberCode(storeId, orderId) != null),
            "cancelled_points", Long.toString(history.cancelledPoints()),
            "restored_points", Long.toString(history.restoredPoints())));
    return history;
  }

  /** この受注が現に帰属している会員のコード。帰属していなければ null。 */
  private String activeMemberCode(Long storeId, String orderId) {
    return orderAttributionRepository
        .findByStoreIdAndOrderIdOrderByIdDesc(storeId, orderId)
        .stream()
        .filter(row -> row.getStatus() == OrderAttributionStatus.ACTIVE)
        .map(OrderAttribution::getMemberCode)
        .findFirst()
        .orElse(null);
  }
}
