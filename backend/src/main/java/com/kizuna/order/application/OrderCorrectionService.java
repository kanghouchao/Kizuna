package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderCorrectionRequest;
import com.kizuna.order.api.dto.OrderCorrectionResponse;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCorrection;
import com.kizuna.order.domain.OrderCorrectionCommand;
import com.kizuna.order.domain.OrderCorrectionRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 完了した受注の内容を権限付きで訂正する門（ADR 0013 が予告した誤完了の救済）。
 *
 * <p>ポイント台帳へ<b>依存を持たない</b>。完了時の自動付与は「完了時点の合計に基づく時点事実」であり、訂正で合計が変わっても 追随しない（帰属 ADR 0009
 * と同族）。台帳を読みも書きもしないことがその決定の構造的な証跡である。
 *
 * <p>付与の差額は算出も提示もしない。手当ては別機構（手動調整）が担い、その調整は受注にも帰属記録にも結び付かないため、 門は「前回の助言が実行されたか」を知る手立てを持たない —
 * 可執行の額として返すと、二度目の訂正が一度目の手当てを 勘定に入れないまま次の額を勧める。要否と額の判断は台帳側の画面に委ねる。
 */
@Service
@RequiredArgsConstructor
public class OrderCorrectionService {

  private final OrderRepository orderRepository;
  private final OrderCorrectionRepository orderCorrectionRepository;
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

    return new OrderCorrectionResponse(previousTotalFee, order.getTotalFee());
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
