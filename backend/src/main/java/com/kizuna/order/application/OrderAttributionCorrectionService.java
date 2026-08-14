package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderAttributionCorrectionRequest;
import com.kizuna.order.api.dto.OrderAttributionCorrectionResponse;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 誤帰属で付いたポイントを、帰属記録が指す会員から差し引く（ADR 0012）。訂正の二段目にあたる。
 *
 * <p>一段目の無効化（{@link OrderAttributionService}）とは意図して別のサービスにしてある。あちらが {@code PointLedgerService}
 * を依存に持たないことが「無効化はポイント台帳へ自動波及しない」（ADR 0009）の構造的な証跡であり、 台帳へ触る責務をあちらへ足すとその証跡が失われ、注釈だけが決定を主張する状態になる。
 *
 * <p>宛先は<b>帰属記録が持つ会員</b>で、顧客に現在紐づく会員ではない。両者は、伝票の申領で成立した帰属（店舗台帳に 顧客行も関連も作らない）・関連の解除・関連の別会員への張り替え、の 3
 * 経路で食い違う。顧客経路の調整口はこの 3 経路のいずれでも誤った相手に当たるか、そもそも到達できない。
 *
 * <p>店舗の境界を決めるのは受注だけである。帰属記録も台帳も platform 帰属で店舗行分離機構に載らないため、どの操作も 先に受注を引き、引けなければそこで終える。
 */
@Service
@RequiredArgsConstructor
public class OrderAttributionCorrectionService {

  private final OrderRepository orderRepository;
  private final OrderAttributionRepository orderAttributionRepository;
  private final PointLedgerService pointLedgerService;
  private final PlatformUserRepository platformUserRepository;
  private final StoreContext storeContext;

  /** 訂正の進み具合。画面が差し引く既定値（付与の全額）と引き残しを示すための読み口。 */
  @StoreScoped
  @Transactional(readOnly = true)
  public OrderAttributionCorrectionResponse status(String orderId, Long attributionId) {
    requireScopedOrder(orderId);
    List<OrderAttribution> attributions =
        orderAttributionRepository.findByOrderIdOrderByIdDesc(orderId);
    OrderAttribution attribution =
        requireInvalidatedAttribution(attributions, orderId, attributionId);
    Long memberId = attribution.getMemberId();
    if (memberId == null) {
      return new OrderAttributionCorrectionResponse(0, 0);
    }
    // 書き込み経路と同じ門を通す。撥ねられる要求の材料を「引ける額」として画面に描くと、既定値を
    // そのまま送った操作者が理由の分からない失敗を受け取る。
    requireNoActiveAttributionFor(attributions, memberId);
    return new OrderAttributionCorrectionResponse(
        grantedPointsFor(memberId, orderId),
        pointLedgerService.correctedPointsFor(attributionIdsOf(attributions, memberId)));
  }

  /**
   * この受注に残っている訂正（誤って付与されたまま差し引かれていない分）。無ければ空。
   *
   * <p>帰属の現況の応答に添えるためにある。訂正を後回しにしたあと正しい本人が申領を済ませると、受注の直近の記録は
   * その人の有効な帰属へ入れ替わり、古い無効化記録は現況の読み口からは見えなくなる。画面が古い記録を名指せる 経路をここでだけ与える —
   * 無ければ「後で行う」は、誤付与が相手の台帳に残ったまま二度と辿り着けない行き止まりになる。
   *
   * <p>返すのは<b>1 件</b>だけで、区間の履歴一覧にはしない（{@link com.kizuna.order.api.dto.OrderAttributionResponse}
   * と同じ紀律）。差し引きが済むごとに次の 1 件が現れるので、複数の会員に誤付与が残っていても順に片付く。
   *
   * <p>選ぶのは<b>新しい順に条件を当てて最初に通った記録</b>であって、先に直近の 1 件を選んでから条件を当てるのではない。
   * 直近の無効化記録が引き切り済み（あるいはその会員が現に帰属していて書き込み経路が撥ねる状態）でも、その先に 差し引きの残る古い記録がありうる —
   * 先に選ぶ形では、その古い記録が画面から永久に名指せなくなる。
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public Optional<PendingCorrection> findPendingCorrection(String orderId) {
    requireScopedOrder(orderId);
    List<OrderAttribution> attributions =
        orderAttributionRepository.findByOrderIdOrderByIdDesc(orderId);
    return attributions.stream()
        .filter(row -> row.getStatus() == OrderAttributionStatus.INVALIDATED)
        .filter(row -> row.getMemberId() != null)
        .filter(row -> !hasActiveAttributionFor(attributions, row.getMemberId()))
        .filter(row -> outstandingPointsFor(attributions, row) > 0)
        .findFirst()
        .map(row -> new PendingCorrection(row.getId(), row.getMemberCode()));
  }

  /** 訂正の残っている帰属記録の名指し。画面はこの ID をそのまま差し引きの宛先に渡す。 */
  public record PendingCorrection(Long attributionId, String memberCode) {}

  /**
   * 名指された帰属記録が持つ会員から、誤って付与された分を差し引く。
   *
   * <p>累計は付与額を超えられない。残高不足で全額を引けなかった後に会員が他店で貯めた分から引き継ぐ経路が正当である 以上、上限が無ければ際限なく引ける。判定は同じ冪等キーの行を除いて数える
   * — 数えると、応答を取り逃した正当な 再送が自分自身の初回記帳によって超過と判じられる。
   *
   * <p>上限の両辺は<b>同じ作用域</b>で数える。付与は受注と会員で数えるので、訂正済みも名指された記録 1 件ではなく、 同じ受注に対するその会員の帰属記録すべてを数える —
   * 同じ会員が同じ受注に二度帰属しうる（無効化 → 再発行 → 本人が 申領し直す）ため、記録ごとに数えると片方の訂正枠がもう片方の付与まで飲み込む。同じ理由で、その会員が現に有効な
   * 帰属を持つ受注では訂正を受け付けない — その付与は正当であり、記録ごとの付与額を持たない台帳では正当な分と 誤った分を切り分けられない。
   *
   * <p>残高が足りなければ台帳側が全額を撥ねる（部分的な引き当ては作らない）。例外の文言が現在残高を載せているので、
   * 操作者は引ける額へ入れ直せる。引き切れなかった差額の扱いは未裁定である（ADR 0009 / 0012）。
   */
  @StoreScoped
  @Transactional
  public OrderAttributionCorrectionResponse correct(
      String orderId, OrderAttributionCorrectionRequest request, String actorEmail) {
    // 上限を数えてから記帳し終えるまで、同じ受注に対する並行の訂正と直列化する。押さえないと双方が同じ
    // 「まだ引かれていない」を観測し、合計が付与額を超えて通る。
    orderRepository
        .findScopedByIdForUpdate(orderId)
        .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + orderId));
    List<OrderAttribution> attributions =
        orderAttributionRepository.findByOrderIdOrderByIdDesc(orderId);
    OrderAttribution attribution =
        requireInvalidatedAttribution(attributions, orderId, request.getAttributionId());
    long memberId = requireMemberId(attribution);
    // 再送の判定は現況を材料にする検証より先に置く（ADR 0007 が台帳の内側で採っている順序を、台帳の
    // 外側のこの門にも当てる）。初回の commit のあとに会員が再発行された伝票を申領し直すと、応答を
    // 取り逃しただけの正当な再送が「今は通せない要求」として撥ねられ、台帳が冪等キーで収束させる機会が
    // 失われる。上限の判定は同じキーの行を除いて数えるので、初回と同じ結論に落ちる。
    if (!pointLedgerService.isIdempotencyKeyCommitted(request.getIdempotencyKey())) {
      requireNoActiveAttributionFor(attributions, memberId);
    }

    long granted = grantedPointsFor(memberId, orderId);
    long already =
        pointLedgerService.correctedPointsFor(
            attributionIdsOf(attributions, memberId), request.getIdempotencyKey());
    if (already + request.getPoints() > granted) {
      throw new ServiceException(
          "差し引ける上限を超えています（この受注の付与は " + granted + " ポイント、うち訂正済みは " + already + " ポイントです）");
    }

    pointLedgerService.correctForAttribution(
        memberId,
        storeContext.getStoreId(),
        -request.getPoints(),
        request.getReason(),
        resolveActorId(actorEmail),
        request.getIdempotencyKey(),
        attribution.getId());
    return new OrderAttributionCorrectionResponse(granted, already + request.getPoints());
  }

  /**
   * 同時再送で冪等キーの一意制約に敗れた要求の再送処理。敗者の書き込みトランザクションは制約違反の時点で作廃されている
   * ため、呼出側（controller）はトランザクションの外から本メソッドを呼び、新しい読み取り専用トランザクションで初回の 記帳を読み直す（ADR 0007）。
   *
   * <p>検証は書き込み経路と同じ順序で通すが、受注行はロックしない — 読み取り専用トランザクションでは行ロックが取れず、 勝者は既に commit
   * 済みで直列化する相手もいない。上限の判定も、その会員が有効な帰属を持たないことの確認も行わない。初回が通った 以上どちらの判定も済んでおり、また会員が勝者の commit
   * のあとに申領し直していれば、後から現況で判じると正当な再送が 失敗する。台帳側の再送判定（内容一致なら何も書かずに戻る／不一致は 409）に落ちることで、 逐次再送と同一の応答になる。
   */
  @StoreScoped
  @Transactional(readOnly = true)
  public OrderAttributionCorrectionResponse replayCorrect(
      String orderId, OrderAttributionCorrectionRequest request, String actorEmail) {
    requireScopedOrder(orderId);
    List<OrderAttribution> attributions =
        orderAttributionRepository.findByOrderIdOrderByIdDesc(orderId);
    OrderAttribution attribution =
        requireInvalidatedAttribution(attributions, orderId, request.getAttributionId());
    long memberId = requireMemberId(attribution);
    long granted = grantedPointsFor(memberId, orderId);
    long already =
        pointLedgerService.correctedPointsFor(
            attributionIdsOf(attributions, memberId), request.getIdempotencyKey());

    pointLedgerService.correctForAttribution(
        memberId,
        storeContext.getStoreId(),
        -request.getPoints(),
        request.getReason(),
        resolveActorId(actorEmail),
        request.getIdempotencyKey(),
        attribution.getId());
    return new OrderAttributionCorrectionResponse(granted, already + request.getPoints());
  }

  /**
   * 現店舗の受注。帰属記録も台帳も受注 ID からしか辿れないため、店舗の所有はここでだけ決まる。
   *
   * <p>引けない受注は他店舗のものか存在しないかを区別せず 404 にする（区別すると受注 ID の存在が漏れる）。
   */
  private void requireScopedOrder(String orderId) {
    if (orderRepository.findScopedById(orderId).isEmpty()) {
      throw new NotFoundException("注文が見つかりません: " + orderId);
    }
  }

  /**
   * 名指された帰属記録。当該受注のものであることと、無効化済みであることを確かめる。
   *
   * <p><b>受注の現況では判じない。</b>無効化のあと正しい本人が申領を済ませていれば受注には有効な帰属記録が並んで存在
   * するが、古い無効化記録は依然として正当な訂正の根拠である（誤って付与された分はまだその会員の台帳にある）。
   *
   * <p>候補は受注 ID から引いた記録に限る。帰属記録は platform 帰属で storeFilter が働かないため、id を直に引く形では
   * 同一店舗の<b>別の受注</b>に属する記録にも当たり、受注との対応を後から確かめる責務が呼出側に残る。受注から引けば その対応は構造として成り立つ。
   */
  private static OrderAttribution requireInvalidatedAttribution(
      List<OrderAttribution> attributions, String orderId, Long attributionId) {
    OrderAttribution attribution =
        attributions.stream()
            .filter(row -> row.getId().equals(attributionId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("帰属記録が見つかりません: " + attributionId));
    if (attribution.getStatus() != OrderAttributionStatus.INVALIDATED) {
      throw new ServiceException("有効な帰属のポイントは差し引けません。先に帰属を無効化してください");
    }
    return attribution;
  }

  /**
   * その会員が現に有効な帰属を持っていないこと。持っていれば差し引きを受け付けない。
   *
   * <p>同じ会員が同じ受注に二度帰属しうる（誤って無効化された本人が、再発行された伝票を申領し直す）。台帳の付与行は
   * どちらの帰属で付いたのかを持たないため、この状態では正当な付与と誤った付与を切り分けられない。名指された古い記録の
   * 訂正枠が新しい正当な付与まで飲み込み、既定値のまま送るだけで正当な分が消える — 撥ねるほうが安全側である。
   */
  private static void requireNoActiveAttributionFor(
      List<OrderAttribution> attributions, long memberId) {
    if (hasActiveAttributionFor(attributions, memberId)) {
      throw new ServiceException("この会員はこの来店に現に帰属しているため、ポイントを差し引けません");
    }
  }

  private static boolean hasActiveAttributionFor(
      List<OrderAttribution> attributions, Long memberId) {
    return attributions.stream()
        .anyMatch(
            row ->
                row.getStatus() == OrderAttributionStatus.ACTIVE
                    && Objects.equals(row.getMemberId(), memberId));
  }

  /** その会員がこの受注に対して持つ帰属記録すべて。訂正済みの合計を数える作用域になる。 */
  private static List<Long> attributionIdsOf(List<OrderAttribution> attributions, long memberId) {
    return attributions.stream()
        .filter(row -> Objects.equals(row.getMemberId(), memberId))
        .map(OrderAttribution::getId)
        .toList();
  }

  /** 名指された記録の会員に、この受注でまだ差し引かれずに残っている付与。 */
  private long outstandingPointsFor(
      List<OrderAttribution> attributions, OrderAttribution attribution) {
    long memberId = attribution.getMemberId();
    return grantedPointsFor(memberId, attribution.getOrderId())
        - pointLedgerService.correctedPointsFor(attributionIdsOf(attributions, memberId));
  }

  /** 帰属先の会員。会員が削除されていれば参照は欠落しており、積み先そのものが無い。 */
  private static long requireMemberId(OrderAttribution attribution) {
    Long memberId = attribution.getMemberId();
    if (memberId == null) {
      throw new ServiceException("この帰属の会員は既に削除されているため、ポイントを差し引けません");
    }
    return memberId;
  }

  /** その受注がその会員へ与えた付与の合計。付与が無ければ 0 で、上限が 0 になるので訂正そのものが通らない。 */
  private long grantedPointsFor(long memberId, String orderId) {
    return pointLedgerService
        .grantedPointsByOrder(memberId, List.of(orderId))
        .getOrDefault(orderId, 0L);
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
