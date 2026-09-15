package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderCorrectionRequest;
import com.kizuna.order.api.dto.OrderCorrectionResponse;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderPreviewResponse;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCorrection;
import com.kizuna.order.domain.OrderCorrectionCommand;
import com.kizuna.order.domain.OrderCorrectionRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.user.application.ActorIdentityService;
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
  private final ActorIdentityService actorIdentityService;
  private final OrderMapper orderMapper;
  private final OrderCalculation calculation;

  /** 訂正前の快照と更新を同じトランザクションに保存する。受注行をロックしてから要求の版を照合し、 同時訂正による上書きを防ぐ。快照は管理下の集約を書き換える前に作成する。 */
  @StoreScoped
  @Transactional
  public OrderCorrectionResponse correct(
      String id, OrderCorrectionRequest request, String actorEmail) {
    Order order =
        orderRepository
            .findScopedByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + id));
    // 版の照合は何も触る前に行う。全量置換なので、開いたまま別の操作者が訂正を済ませていると、送らなかった
    // 項目まで開いた時点の値で押し戻す — 楽観ロックは要求ごとに現物を読み直すため、この照合が無いと
    // 食い違いを検出できないまま、理由と痕を伴う先の訂正が黙って巻き戻る。
    if (!Objects.equals(order.getVersion(), request.getExpectedVersion())) {
      throw new OrderConfirmationConflict(
          "expected_version", "この受注は別の操作者が訂正しました。最新の内容を読み直してからやり直してください");
    }
    int previousTotalFee = order.getTotalFee();
    int previousRemuneration = order.getTotalRemuneration();
    int previousDuration = order.getTotalDurationMinutes();
    var previousLines = orderMapper.toFeeLineResponses(order.getFeeLines());

    var previousCourse = order.getCourse();
    var course =
        request.getCourseRevisionId() == null
            ? previousCourse
            : calculation.historical(request.getCourseRevisionId());
    var calculated = calculation.calculate(order, course, request.getFeeLines(), true);
    calculation.verify(
        request.getConfirmationToken(),
        calculation.preview("CORRECT", id, request, calculated, null));
    var correction =
        orderCorrectionRepository.save(
            OrderCorrection.snapshotOf(
                order,
                request.getReason(),
                actorIdentityService.requireUserId(actorEmail),
                OffsetDateTime.now()));
    order.correct(
        new OrderCorrectionCommand(
            request.getActualArrivalTime(),
            request.getActualEndTime(),
            course,
            calculated.editableFeeLines()));
    orderRepository.saveAndFlush(order);

    return new OrderCorrectionResponse(
        correction.getId(),
        previousTotalFee,
        order.getTotalFee(),
        previousCourse,
        order.getCourse(),
        previousRemuneration,
        order.getTotalRemuneration(),
        previousDuration,
        order.getTotalDurationMinutes(),
        previousLines,
        orderMapper.toFeeLineResponses(order.getFeeLines()));
  }

  @StoreScoped
  @Transactional
  public OrderPreviewResponse preview(String id, OrderCorrectionRequest request) {
    calculation.requirePreviewInput(request.getConfirmationToken());
    var order =
        orderRepository
            .findScopedByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("受注が見つかりません"));
    calculation.requireVersion(order, request.getExpectedVersion());
    if (order.getStatus() != OrderStatus.COMPLETED) throw new ServiceException("完了した受注だけが訂正できます");
    var course =
        request.getCourseRevisionId() == null
            ? order.getCourse()
            : calculation.historical(request.getCourseRevisionId());
    return calculation.preview(
        "CORRECT",
        id,
        request,
        calculation.calculate(order, course, request.getFeeLines(), false),
        null);
  }
}
