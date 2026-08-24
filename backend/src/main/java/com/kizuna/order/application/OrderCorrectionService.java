package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderCorrectionRequest;
import com.kizuna.order.api.dto.OrderCorrectionResponse;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.order.domain.OrderCorrection;
import com.kizuna.order.domain.OrderCorrectionCommand;
import com.kizuna.order.domain.OrderCorrectionRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 完了した受注の内容を権限付きで訂正する門（ADR 0013 が予告した誤完了の救済）。
 *
 * <p>ポイント台帳へは一切書かない。完了時の自動付与は「完了時点の合計に基づく時点事実」であり、訂正で合計が変わっても 追随しない（帰属 ADR 0009 と同族）。{@code
 * PointLedgerService} を<b>読み</b>だけに使うことが、その決定の構造的な証跡である — 記帳の呼出をここへ足すと、注釈だけが零波及を主張する状態になる。
 *
 * <p>差額の手当は既存機構（手動調整・誤帰属の訂正）が担う。訂正者と調整者はどちらも店長なので権限の断層は無い。
 */
@Service
@RequiredArgsConstructor
public class OrderCorrectionService {

  private final OrderRepository orderRepository;
  private final OrderCorrectionRepository orderCorrectionRepository;
  private final OrderAttributionRepository orderAttributionRepository;
  private final PointLedgerService pointLedgerService;
  private final PlatformUserRepository platformUserRepository;
  private final OrderMapper orderMapper;

  /**
   * 完了した受注を理由付きで訂正し、訂正前の姿を痕として残す。
   *
   * <p>痕は訂正を当てる<b>前</b>に起こす。管理下の集約はその場で書き換わるため、順序が逆だと訂正後の姿を前値として 記録してしまう（当日実績の訂正と同じ紀律）。撥ねられた要求は
   * トランザクションごと巻き戻るので、痕だけが残ることは無い。
   *
   * <p>同時に届いた 2 つの訂正は双方が COMPLETED を読んで門を通り、受注の {@code @Version} で敗者が 409 に落ちる。 逐次なら 2 回とも成立し、痕が 2
   * 行並んで鎖になる。
   */
  @StoreScoped
  @Transactional
  public OrderCorrectionResponse correct(
      String id, OrderCorrectionRequest request, String actorEmail) {
    Order order =
        orderRepository.findById(id).orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));
    // 版の照合は何も触る前に行う。全量置換なので、開いたまま別の操作者が訂正を済ませていると、送らなかった
    // 項目まで開いた時点の値で押し戻す — 楽観ロックは要求ごとに現物を読み直すため、この照合が無いと
    // 食い違いを検出できないまま、理由と痕を伴う先の訂正が黙って巻き戻る。
    if (!Objects.equals(order.getVersion(), request.getExpectedVersion())) {
      throw new ConflictException("この受注は別の操作者が訂正しました。最新の内容を読み直してからやり直してください");
    }
    int previousTotalFee = order.getTotalFee();

    orderCorrectionRepository.save(
        OrderCorrection.snapshotOf(
            order, request.getReason(), resolveActorId(actorEmail), OffsetDateTime.now()));
    order.correct(
        new OrderCorrectionCommand(
            request.getActualArrivalTime(),
            request.getActualEndTime(),
            request.getCourseName(),
            request.getCourseMinutes(),
            request.getExtensionMinutes(),
            orderMapper.toFeeLineDrafts(request.getFeeLines())));
    orderRepository.save(order);

    int granted = grantedPoints(order);
    int recomputed = recomputedGrantPoints(order);
    return new OrderCorrectionResponse(
        previousTotalFee, order.getTotalFee(), granted, recomputed, recomputed - granted);
  }

  /**
   * 訂正後の内容で完了していれば付与されたであろうポイント。
   *
   * <p>基準は付与と同じ「ポイント利用を除いた総和」で、控除後の請求額ではない（ADR 0018）。負の基準は 0 へ丸める — 割引が請求を上回る会計は付与を生まないのであって、
   * 付与を負に翻すわけではない。
   *
   * <p>完了処理の事前計算（{@code OrderService#completionPreview}）を流用しない。あちらは<b>現在</b>の会員紐づけを見て 非会員には 0
   * を返すため、完了後に紐づけが解除・張り替えされた受注では「訂正で付与が消えた」と誤って読める。 付与規則そのものは会員に依存しないので、ここでは規則だけを引く。
   */
  private int recomputedGrantPoints(Order order) {
    return pointLedgerService.previewGrant(Math.max(0, order.grantBasisAmount()));
  }

  /**
   * この受注が現に帰属している会員へ実際に付与したポイント。訂正では動かない時点事実で、帰属していない受注では 0。
   *
   * <p>受注の {@code auto_grant_points} を読まない。完了時に会員へ届かなかった受注が伝票トークンの申領で事後帰属した 場合、付与は台帳へ入るが受注の列は 0
   * のまま据え置かれる（申領は完了時点の会計を書き換えない）。列を基準にすると その受注の差額が付与の全額ぶん膨らみ、過大な手当てを招く。付与の所在は台帳であり、そこから引く。
   *
   * <p>会員を帰属記録から採るのは、手当ての宛先が帰属記録だからである（ADR 0012）— 顧客に今紐づく会員ではない。
   *
   * <p>既に差し引かれた誤付与は基準から外す。台帳の付与合計は加算行だけを数えるので、同じ会員がこの受注へ二度 帰属した履歴があると、差し引き済みの 1 本ぶんまで手元にあることにしてしまう。
   */
  private int grantedPoints(Order order) {
    List<OrderAttribution> attributions =
        orderAttributionRepository.findByOrderIdOrderByIdDesc(order.getId());
    Long memberId =
        attributions.stream()
            .filter(attribution -> attribution.getStatus() == OrderAttributionStatus.ACTIVE)
            .map(OrderAttribution::getMemberId)
            .findFirst()
            .orElse(null);
    if (memberId == null) {
      return 0;
    }
    long granted =
        pointLedgerService
            .grantedPointsByOrder(memberId, List.of(order.getId()))
            .getOrDefault(order.getId(), 0L);
    // 差し引き済みの分は既に相手の手元に無い。同じ会員がこの受注へ二度帰属した場合（無効化 → 誤付与の
    // 差し引き → 再発行 → 同じ本人の申領）、付与行は 2 本並ぶが実効の付与は 1 本ぶんである。付与合計だけを
    // 基準にすると実効より多く見積もり、手当てを過小に、ときには逆向きに勧める。
    long corrected =
        pointLedgerService.correctedPointsFor(
            attributions.stream()
                .filter(attribution -> memberId.equals(attribution.getMemberId()))
                .map(OrderAttribution::getId)
                .toList());
    // 台帳の合計は long だが、1 受注ぶんの付与は付与規則が int の範囲に閉じている（previewGrant が
    // 上限超過を撥ねる）。超えたなら実装欠陥なので黙って丸めず失敗させる。
    return Math.toIntExact(granted - corrected);
  }

  /**
   * JWT は user-id claim を持たないため、実行者は認証主体の email から解決する。
   *
   * <p>解決できない認証主体は黙って null にせず失敗させる — 痕の実行者 null は「利用者が後から削除された」の形であり、
   * 失効した認証セッションによる操作がそれと区別できなくなる。
   */
  private Long resolveActorId(String actorEmail) {
    return platformUserRepository
        .findByEmail(actorEmail)
        .map(PlatformUser::getId)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
  }
}
