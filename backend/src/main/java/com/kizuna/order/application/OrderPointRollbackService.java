package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderPointRollbackPreviewResponse;
import com.kizuna.order.api.dto.OrderPointRollbackRequest;
import com.kizuna.order.api.dto.OrderPointRollbackResponse;
import com.kizuna.order.api.dto.OrderReceiptTokenResponse;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.point.application.PointLedgerService.PointRollbackResult;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受注 1 件を宛先にポイントの授受を打ち消す明示操作（巻き戻し）。
 *
 * <p>受注取消（CONFIRMED → CANCELLED）へは配線しない。付与も利用も完了と事後申領の契機でしか記帳されないため、 取消できる受注に打ち消す仕訳は構造上存在しない。
 * 完了後訂正の門とも繋がない — 門は台帳を読みも書きもしないまま（ADR 0019）で、画面の導線だけがここを指す。
 *
 * <p>店舗の境界を決めるのは受注だけである。台帳も操作記録も platform 帰属で店舗行分離機構に載らないため、 先に受注を引き、引けなければそこで終える。
 */
@Service
@RequiredArgsConstructor
public class OrderPointRollbackService {

  private final OrderRepository orderRepository;
  private final OrderAttributionService orderAttributionService;
  private final OrderAttributionRepository orderAttributionRepository;
  private final OrderReceiptTokenRepository orderReceiptTokenRepository;
  private final PointLedgerService pointLedgerService;
  private final PlatformUserRepository platformUserRepository;

  /**
   * 巻き戻し済みでないことを確かめてから伝票を再発行する。
   *
   * <p>判定と発行を<b>受注行を押さえたままの 1 つの取引</b>で行う。判定だけ別の取引で先に済ませると、その隙間へ
   * 巻き戻しが割り込んだとき、古い「まだ巻き戻されていない」を根拠に、申領が必ず拒む QR を発行してしまう。 巻き戻しも同じ受注行を押さえるので、先に取れたほうが結果を決める。
   *
   * <p>判定をここへ置くのは、再発行のサービスが台帳へ依存を持たないこと自体が「無効化は台帳へ波及しない」（ADR
   * 0009）の構造的な証跡だからである。あちらへ台帳の読みを足すとその証跡が失われる。
   */
  @StoreScoped
  @Transactional
  public OrderReceiptTokenResponse reissueReceiptTokenUnlessRolledBack(String orderId) {
    orderRepository
        .findScopedByIdForUpdate(orderId)
        .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + orderId));
    if (pointLedgerService.isRolledBack(orderId)) {
      throw new ServiceException("ポイントを巻き戻した受注には伝票を再発行できません");
    }
    return orderAttributionService.reissueReceiptToken(orderId);
  }

  /** 実行前の下見。画面はこれで「何がいくら動くか」を示し、済んでいる受注では実行の導線を出さない。 */
  @StoreScoped
  @Transactional(readOnly = true)
  public OrderPointRollbackPreviewResponse preview(String orderId) {
    requireScopedOrder(orderId);
    PointLedgerService.PointRollbackPreview preview =
        pointLedgerService.previewRollbackForOrder(orderId);
    return new OrderPointRollbackPreviewResponse(
        preview.alreadyRolledBack(),
        activeMemberCode(orderId),
        preview.cancellablePoints(),
        preview.reversibleUsedPoints());
  }

  /**
   * 巻き戻しを実行する。
   *
   * <p>対象は<b>完了した受注だけ</b>。確定済みの受注を許すと、記録だけが先に書かれたあとの完了が付与を積み、 その付与を打ち消す手立てが残らない（二度目の巻き戻しは 409
   * で撥ねられる）。取消済みの受注には打ち消す 授受がそもそも無い。
   *
   * <p>押さえる順は受注行 → 伝票トークン行 → 会員の加算ロット（ADR 0016 の向き）。トークン行を押さえるのは事後申領と
   * 直列化するためで、押さえないと申領は操作記録を見ず、巻き戻しは申領の付与を見ないまま双方が成立し、 打ち消せない付与が残る。
   */
  @StoreScoped
  @Transactional
  public OrderPointRollbackResponse rollback(
      String orderId, OrderPointRollbackRequest request, String actorEmail) {
    Order order =
        orderRepository
            .findScopedByIdForUpdate(orderId)
            .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + orderId));
    if (order.getStatus() != OrderStatus.COMPLETED) {
      throw new ServiceException("完了した受注だけがポイントを巻き戻せます");
    }
    orderReceiptTokenRepository.findByOrderIdForUpdate(orderId);
    PointRollbackResult result =
        pointLedgerService.rollbackForOrder(
            orderId, request.getReason(), resolveActorId(actorEmail));
    return new OrderPointRollbackResponse(result.cancelledPoints(), result.restoredPoints());
  }

  /**
   * 現店舗の受注。台帳も操作記録も受注 ID からしか辿れないため、店舗の所有はここでだけ決まる。
   *
   * <p>引けない受注は他店舗のものか存在しないかを区別せず 404 にする（区別すると受注 ID の存在が漏れる）。
   */
  private void requireScopedOrder(String orderId) {
    if (orderRepository.findScopedById(orderId).isEmpty()) {
      throw new NotFoundException("注文が見つかりません: " + orderId);
    }
  }

  /** この受注が現に帰属している会員のコード。帰属していなければ null。 */
  private String activeMemberCode(String orderId) {
    return orderAttributionRepository.findByOrderIdOrderByIdDesc(orderId).stream()
        .filter(row -> row.getStatus() == OrderAttributionStatus.ACTIVE)
        .map(OrderAttribution::getMemberCode)
        .findFirst()
        .orElse(null);
  }

  /**
   * JWT は user-id claim を持たないため、実行者は認証主体の email から解決する。
   *
   * <p>解決できない認証主体は黙って null にせず失敗させる — 追記型の台帳では実行者 null が「機構が起こした仕訳」の形で
   * あり、失効した認証セッションによる人手の操作がそれと区別できなくなる。
   */
  private Long resolveActorId(String actorEmail) {
    return platformUserRepository
        .findByEmail(actorEmail)
        .map(PlatformUser::getId)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
  }
}
