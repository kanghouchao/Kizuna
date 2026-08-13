package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderAttributionInvalidationRequest;
import com.kizuna.order.api.dto.OrderAttributionResponse;
import com.kizuna.order.api.dto.OrderReceiptTokenResponse;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.order.domain.OrderReceiptToken;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.infrastructure.ReceiptTokenGenerator;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 誤帰属の訂正（店舗コンソール）。帰属記録の無効化と、その受注に対する伝票トークンの再発行を受け持つ。
 *
 * <p>台帳へは<b>一切触らない</b>。無効化はポイントへ自動波及せず、清算は理由の残る手動調整で行う（ADR 0009）。 依存に {@code PointLedgerService}
 * を持たないことがその宣言でもある。
 *
 * <p>帰属記録も伝票トークンも platform 帰属で店舗行分離機構に載らないため、店舗の境界を決めるのは受注だけである。 どの操作も先に {@link
 * OrderRepository#findScopedById} で受注を引き、引けなければそこで終える。
 *
 * <p>完了（{@link OrderService#complete}）が受け持つのは帰属の<b>成立</b>で、こちらは成立済みの記録の訂正にあたる。
 * 台帳への記帳を伴うかどうかで責務が分かれる。
 */
@Service
@RequiredArgsConstructor
public class OrderAttributionService {

  private final OrderRepository orderRepository;
  private final OrderAttributionRepository orderAttributionRepository;
  private final OrderReceiptTokenRepository orderReceiptTokenRepository;
  private final ReceiptTokenGenerator receiptTokenGenerator;
  private final PlatformUserRepository platformUserRepository;

  /** 受注 1 件の帰属の現況。無効化・再発行のどちらを提示するかを店舗側の画面が判じるための読み口。 */
  @StoreScoped
  @Transactional(readOnly = true)
  public OrderAttributionResponse currentAttribution(String orderId) {
    requireScopedOrder(orderId);
    return toResponse(latestAttribution(orderId).orElse(null));
  }

  /**
   * 帰属記録を理由付きで無効化する（誤帰属の訂正）。
   *
   * <p>対象は現に有効な帰属記録 1 件。無効化された受注は「会員へ帰属しない状態」へ戻り、伝票トークンの再発行による 事後帰属の対象に復帰する。
   *
   * <p>誤って付与されたポイントはここでは動かない。無効化で台帳を機械的に巻き戻すと、誤りの上に誤りを重ねる危険が 誤りを残す危険を上回る（ADR 0009）。
   */
  @StoreScoped
  @Transactional
  public OrderAttributionResponse invalidate(
      String orderId, OrderAttributionInvalidationRequest request, String actorEmail) {
    requireScopedOrder(orderId);
    OrderAttribution attribution =
        latestAttribution(orderId)
            .filter(row -> row.getStatus() == OrderAttributionStatus.ACTIVE)
            .orElseThrow(() -> new ServiceException("この受注は会員へ帰属していません"));

    attribution.invalidate(request.getReason(), resolveActorId(actorEmail), OffsetDateTime.now());
    return toResponse(orderAttributionRepository.save(attribution));
  }

  /**
   * 無効化された受注へ伝票トークンを再発行し、生値を返す。正しい本人はこの QR の所持証明で来店を取り戻せる。
   *
   * <p>申領期限は<b>再発行から</b> 90 日で数え直す（ADR 0009 の意図的な例外）。誤帰属の期間が本人の申領窓を食い潰したまま
   * 原期限を残すと、訂正が形骸化するため。期限の起点は {@link OrderReceiptToken#issueFor} が受け取る発行時刻そのものになる。
   *
   * <p>付与予定額は<b>完了時点で確定した額</b>を引き継ぐ。ここで会計金額から計算し直すと、同じ会計が訂正の早い遅い（＝その間の
   * 付与設定の変更）で別のポイントになる。引き継ぎ元は、この受注に伝票トークンの履歴があればその直近の予定額（完了時に非会員として
   * 発行された額）、無ければ完了時に実際に付与された額（会員へ帰属した完了ではトークンが発行されないため、確定額はこちらにしか無い）。
   *
   * <p>申領できるトークンが既にある受注へは発行しない。生きたトークンが 2 本並ぶと、後から申領した側は帰属記録の部分一意違反で 落ち、申領の応答が同形でなくなる（{@code
   * MemberReceiptClaimService} の収束は同一トークンの並行申領しか担わない）。
   * 有効な帰属記録がある間はトークンが生きていることは無いので、無効化の側にトークンを止める処理は要らない。
   */
  @StoreScoped
  @Transactional
  public OrderReceiptTokenResponse reissueReceiptToken(String orderId) {
    Order order = requireScopedOrder(orderId);
    if (order.getStatus() != OrderStatus.COMPLETED) {
      throw new ServiceException("完了していない受注に伝票は発行できません");
    }
    List<OrderAttribution> attributions =
        orderAttributionRepository.findByOrderIdOrderByIdDesc(orderId);
    if (attributions.isEmpty()) {
      throw new ServiceException("この受注は会員へ帰属したことがありません。伝票の再発行は無効化された帰属の訂正にだけ使えます");
    }
    if (attributions.stream().anyMatch(row -> row.getStatus() == OrderAttributionStatus.ACTIVE)) {
      throw new ServiceException("この受注は会員へ帰属しています。先に帰属を無効化してください");
    }

    OffsetDateTime now = OffsetDateTime.now();
    List<OrderReceiptToken> tokens =
        orderReceiptTokenRepository.findByOrderIdOrderByIdDesc(orderId);
    if (tokens.stream().anyMatch(token -> token.isClaimableAt(now))) {
      throw new ServiceException("この受注には申領できる伝票が既にあります");
    }

    int plannedPoints =
        tokens.isEmpty() ? order.getAutoGrantPoints() : tokens.get(0).getPlannedPoints();
    ReceiptTokenGenerator.GeneratedToken generated = receiptTokenGenerator.generate();
    orderReceiptTokenRepository.save(
        OrderReceiptToken.issueFor(orderId, generated.digest(), plannedPoints, now));
    return new OrderReceiptTokenResponse(generated.raw());
  }

  /**
   * 現店舗の受注。帰属記録・伝票トークンはどちらも受注 ID でしか辿れないため、店舗の所有はここでだけ決まる。
   *
   * <p>引けない受注は他店舗のものか存在しないかを区別せず 404 にする（区別すると受注 ID の存在が漏れる）。
   */
  private Order requireScopedOrder(String orderId) {
    return orderRepository
        .findScopedById(orderId)
        .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + orderId));
  }

  /** 直近の帰属記録。無効化で行を消さないため複数行を持ちうるが、有効な行は部分一意索引により高々 1 件で、あればそれが直近になる。 */
  private Optional<OrderAttribution> latestAttribution(String orderId) {
    return orderAttributionRepository.findFirstByOrderIdOrderByIdDesc(orderId);
  }

  /**
   * JWT は user-id claim を持たないため、実行者は認証主体の email から解決する。
   *
   * <p>解決できない認証主体は黙って null にせず失敗させる — 無効化の実行者は訂正の根拠の一部であり、失効した認証セッションによる 操作を実行者不明のまま通すと記録が監査に耐えない。
   */
  private Long resolveActorId(String actorEmail) {
    return platformUserRepository
        .findByEmail(actorEmail)
        .map(PlatformUser::getId)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
  }

  /** 会員側の情報は会員コードだけを写す。表示名・メールはプラットフォーム側プロフィールで、店舗へは渡らない。 */
  private static OrderAttributionResponse toResponse(OrderAttribution attribution) {
    if (attribution == null) {
      return new OrderAttributionResponse(false, null, null, null, null, null);
    }
    return new OrderAttributionResponse(
        attribution.getStatus() == OrderAttributionStatus.ACTIVE,
        attribution.getMemberCode(),
        attribution.getSource().name(),
        attribution.getAttributedAt(),
        attribution.getInvalidatedReason(),
        attribution.getInvalidatedAt());
  }
}
