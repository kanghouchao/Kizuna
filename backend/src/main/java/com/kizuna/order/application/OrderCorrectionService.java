package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderCorrectionRequest;
import com.kizuna.order.api.dto.OrderCorrectionResponse;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderPreviewResponse;
import com.kizuna.order.api.dto.OrderSpecialServiceResponse;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCorrection;
import com.kizuna.order.domain.OrderCorrectionCommand;
import com.kizuna.order.domain.OrderCorrectionRepository;
import com.kizuna.order.domain.OrderCorrectionSnapshot;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.user.application.ActorIdentityService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 完了した受注の提供事実だけを訂正し、ポイント台帳は読み書きしない。 完了時の付与は独立した事実として保持する。手動調整との実行対応を追えないため、
 * 付与の差額を実行指示として返さず、手当ての要否と額は台帳側の判断に委ねる。
 */
@Service
@RequiredArgsConstructor
public class OrderCorrectionService {

  private final EntityManager entityManager;
  private final OrderRepository orderRepository;
  private final OrderCorrectionRepository orderCorrectionRepository;
  private final ActorIdentityService actorIdentityService;
  private final OrderMapper orderMapper;
  private final OrderCalculation calculation;
  private final OrderSpecialServices specialServices;

  /** 訂正前の快照と更新を同じトランザクションに保存する。受注行をロックしてから要求の版を照合し、 同時訂正による上書きを防ぐ。快照は管理下の集約を書き換える前に作成する。 */
  @StoreScoped
  @Transactional
  public OrderCorrectionResponse correct(
      String id, OrderCorrectionRequest request, String actorEmail) {
    specialServices.lock();
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
    var calculated =
        calculation.calculate(
            order,
            course,
            request.getFeeLines(),
            specialServices.historical(order, request.getSpecialServiceRevisionIds()),
            true);
    calculation.verify(
        request.getConfirmationToken(),
        calculation.preview("CORRECT", id, request, calculated, null));
    long beforeVersion = order.getVersion();
    var before = OrderCorrectionSnapshot.of(order);
    // 子明細だけの同額訂正でも、親の受注版を進めて履歴順と競合検出を確定する。
    entityManager.lock(order, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    var previousSpecials = order.getSpecialServices();
    order.correctServices(
        calculated.getSpecialServices(),
        new OrderCorrectionCommand(
            request.getActualArrivalTime(),
            request.getActualEndTime(),
            course,
            calculated.editableFeeLines()));
    orderRepository.saveAndFlush(order);
    // 列の日時精度と生成済み明細 ID を反映し、次の訂正の前値と同じ姿を記録する。
    entityManager.refresh(order);
    var correction =
        orderCorrectionRepository.save(
            OrderCorrection.recorded(
                order,
                beforeVersion,
                before,
                request.getReason(),
                actorIdentityService.requireUserId(actorEmail),
                OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS)));

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
        orderMapper.toFeeLineResponses(order.getFeeLines()),
        previousSpecials.stream()
            .map(s -> new OrderSpecialServiceResponse(s, false, null))
            .toList(),
        specialServices.describe(order),
        order.getId(),
        order.getStoreId(),
        correction.getBusinessDate(),
        correction.getCompletedAt(),
        correction.getCorrectedAt(),
        correction.getCorrectedBy(),
        correction.getReason(),
        correction.getBeforeVersion(),
        correction.getAfterVersion(),
        before.accruedRemuneration(),
        order.getAccruedRemuneration());
  }

  @StoreScoped
  @Transactional
  public OrderPreviewResponse preview(String id, OrderCorrectionRequest request) {
    specialServices.lock();
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
        calculation.calculate(
            order,
            course,
            request.getFeeLines(),
            specialServices.historical(order, request.getSpecialServiceRevisionIds()),
            false),
        null);
  }
}
