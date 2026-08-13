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
import com.kizuna.shared.exception.ConflictException;
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
 * <p>台帳へは<b>一切触らない</b>。無効化はポイントへ自動波及せず、台帳側の訂正は別段の人手操作が受け持つ（ADR 0009 / 0012）。 依存に {@code
 * PointLedgerService} を持たないことがその宣言であり、{@link OrderAttributionCorrectionService} を別のサービスに
 * 分けている理由でもある。
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
   *
   * <p>対象は受注から導かず、要求が名指した記録と現に有効な記録の一致を確かめる。画面を開いたまま別の操作者が訂正を一巡 （無効化 → 再発行 →
   * 正しい本人の申領）させると、受注から導く実装では古い理由が<b>新しく成立した正しい帰属</b>へ当たり、 取り戻したばかりの来店を消したうえに誤った監査記録を残す。ずれていれば書かずに
   * 409 で差し戻す。
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
    if (!attribution.getId().equals(request.getAttributionId())) {
      throw new ConflictException("この受注の帰属は別の操作で変わりました。最新の状態を確認してからやり直してください");
    }

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
   * <p>先行する未申領のトークンは<b>すべて失効させてから</b>発行する。生きたトークンが 2 本並ぶと、後から申領した側は帰属記録の
   * 部分一意違反で落ち、申領の応答が同形でなくなる（{@code MemberReceiptClaimService} の収束は同一トークンの並行申領しか担わない）。 発行を撥ねるのではなく前の
   * 1 本を殺す形にしているのは、発行の応答を取り逃した店舗（生値は二度と手に入らない）が 訂正をやり直せる必要があるためで、渡した QR を回収する手段も店舗にはこれしか無い。
   *
   * <p>期限切れの行も {@code ISSUED} のまま残るので、失効の対象は「申領できる行」ではなく<b>未申領の行すべて</b>にする — 索引が強制できるのは「ISSUED は高々
   * 1 本」であり、期限を条件に含む不変量は静的な索引では表現できない。
   *
   * <p>有効な帰属記録がある間はトークンが生きていることは無いので、無効化の側にトークンを止める処理は要らない。
   */
  @StoreScoped
  @Transactional
  public OrderReceiptTokenResponse reissueReceiptToken(String orderId) {
    // 受注を押さえて並行する再発行と直列化する。トークンが 1 本も無い受注（完了時に会員へ帰属した受注）は
    // 押さえる行が下に無いため、ここだけが直列化点になる。
    Order order =
        orderRepository
            .findScopedByIdForUpdate(orderId)
            .orElseThrow(() -> new NotFoundException("注文が見つかりません: " + orderId));
    if (order.getStatus() != OrderStatus.COMPLETED) {
      throw new ServiceException("完了していない受注に伝票は発行できません");
    }
    // トークン行を先に押さえてから帰属記録を読む。順序を入れ替えると、在途の申領が commit する前に読んだ
    // 「帰属していない」という観測のまま発行してしまい、有効な帰属と申領できる伝票が並ぶ。
    List<OrderReceiptToken> tokens = orderReceiptTokenRepository.findByOrderIdForUpdate(orderId);
    List<OrderAttribution> attributions =
        orderAttributionRepository.findByOrderIdOrderByIdDesc(orderId);
    if (attributions.isEmpty()) {
      throw new ServiceException("この受注は会員へ帰属したことがありません。伝票の再発行は無効化された帰属の訂正にだけ使えます");
    }
    if (attributions.stream().anyMatch(row -> row.getStatus() == OrderAttributionStatus.ACTIVE)) {
      throw new ServiceException("この受注は会員へ帰属しています。先に帰属を無効化してください");
    }

    // 失効を先に flush する。Hibernate は INSERT を UPDATE より先に流すため、まとめて save すると
    // 新しい行が「まだ倒れていない旧行」と部分一意索引の上で衝突する。
    tokens.stream()
        .filter(OrderReceiptToken::isRevocable)
        .forEach(
            token -> {
              token.revoke();
              orderReceiptTokenRepository.saveAndFlush(token);
            });

    int plannedPoints =
        tokens.isEmpty() ? order.getAutoGrantPoints() : tokens.get(0).getPlannedPoints();
    ReceiptTokenGenerator.GeneratedToken generated = receiptTokenGenerator.generate();
    orderReceiptTokenRepository.save(
        OrderReceiptToken.issueFor(
            orderId, generated.digest(), plannedPoints, OffsetDateTime.now()));
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
      return new OrderAttributionResponse(null, false, null, null, null, null, null);
    }
    return new OrderAttributionResponse(
        attribution.getId(),
        attribution.getStatus() == OrderAttributionStatus.ACTIVE,
        attribution.getMemberCode(),
        attribution.getSource().name(),
        attribution.getAttributedAt(),
        attribution.getInvalidatedReason(),
        attribution.getInvalidatedAt());
  }
}
