package com.kizuna.order.application;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.order.api.dto.MemberReceiptClaimResponse;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderReceiptToken;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.infrastructure.ReceiptTokenGenerator;
import com.kizuna.point.application.BenefitGrantService;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員ポータルからの伝票トークンの申領（事後帰属）ユースケース。
 *
 * <p>効果は<b>帰属記録（根拠 RECEIPT_TOKEN）と発行時に確定した固定額の記帳</b>に閉じる。店舗台帳（顧客行・行級の関連）へは 一切波及しない —
 * 申領が証明するのは「この伝票の来店は自分のものだ」という受注 1 件の事実だけで、その店舗と会員の 継続的な関係ではない（ADR 0008）。完了済み受注の会計（{@code
 * auto_grant_points} 等）も書き換えない。完了時に付与が 無かったことは当時の事実であり、この申領で得たポイントは台帳が持つ。
 *
 * <p>申領できないトークンは理由を区別せず<b>同形のエラー</b>で返す。不在・期限切れ・使用済み・巻き戻し済みを撃ち分けると、応答の違いから
 * 受注の存在と完了状態を辿れてしまう。並行申領の敗者も同じ形へ落ちる（{@link OrderReceiptTokenRepository#findByTokenDigest} の行ロック）。
 *
 * <p>会員は店舗を授権されないため店舗文脈（{@code @StoreScoped}）を確立できず storeFilter も働かない。トークンのダイジェスト一致が
 * 申領を成立させる唯一の材料であり、受注は店舗を跨いで引く（トークンは platform 帰属で、店舗で絞ると照合そのものが成立しない）。
 */
@Service
@RequiredArgsConstructor
public class MemberReceiptClaimService {

  /** 申領できないトークンへ返す唯一の文言。不在・期限切れ・使用済み・巻き戻し済み・並行申領の敗者がすべてこの応答になる。 */
  private static final String UNCLAIMABLE_MESSAGE =
      "この伝票は申領できません。QR の有効期限（90 日）と、既に取り込み済みでないかをご確認ください";

  private final OrderReceiptTokenRepository orderReceiptTokenRepository;
  private final OrderAttributionRepository orderAttributionRepository;
  private final OrderRepository orderRepository;
  private final ReceiptTokenGenerator receiptTokenGenerator;
  private final PointLedgerService pointLedgerService;
  private final BenefitGrantService benefitGrantService;
  private final PlatformUserRepository platformUserRepository;
  private final MemberLookupService memberLookupService;
  private final MemberRankSync memberRankSync;

  /**
   * 伝票トークンを申領し、その来店を本人の記録として確定する。
   *
   * @param email 認証主体（申領する会員本人）
   * @param rawToken QR が運ぶトークンの生値。保存された鍵付きダイジェストとの一致だけで照合する
   * @return 記帳したポイント（付与予定額 0 の伝票では 0 で、台帳に行は書かない）
   */
  @StoreScopeExempt(reason = "トークンの鍵付きダイジェスト一致だけが申領を成立させる材料で、受注は店舗を跨いで引く（店舗で絞ると照合が成立しない）")
  @Transactional
  public MemberReceiptClaimResponse claim(String email, String rawToken) {
    Long platformUserId = resolvePlatformUserId(email);
    MemberLookup member = resolveMember(platformUserId);

    OrderReceiptToken token =
        orderReceiptTokenRepository
            .findByTokenDigest(receiptTokenGenerator.digest(rawToken))
            .orElseThrow(() -> new NotFoundException(UNCLAIMABLE_MESSAGE));
    // 時刻は行ロックを取った後に読む。並行申領の待ち合わせはロックの解放まで伸びるため、要求開始時の
    // 時刻で判じると、待っている間に期限を越えた伝票を通しうる。帰属時刻も「成立した瞬間」であるべきで、
    // 「要求が届いた瞬間」ではない。
    OffsetDateTime now = OffsetDateTime.now();
    if (!token.isClaimableAt(now)) {
      throw new NotFoundException(UNCLAIMABLE_MESSAGE);
    }
    // 巻き戻し済みの受注は申領できない。判じるのは操作記録であって台帳の仕訳の有無ではない — 付与予定額は
    // 完了時点で固定され再発行でも計算し直されないため、仕訳ゼロの受注でも申領は原額を積み直せる。
    if (pointLedgerService.isRolledBack(token.getOrderId())) {
      throw new NotFoundException(UNCLAIMABLE_MESSAGE);
    }
    // 発生店舗は台帳の仕訳が要る（残高の作用域ではなく帰属情報）。トークンは受注へ FK CASCADE で
    // 結ばれているので、引けない受注は実装欠陥だが、その場合も利用者へは同形のエラーで返す。
    Order order =
        orderRepository
            .findById(token.getOrderId())
            .orElseThrow(() -> new NotFoundException(UNCLAIMABLE_MESSAGE));

    token.claim(now);
    orderReceiptTokenRepository.save(token);
    orderAttributionRepository.save(
        OrderAttribution.onReceiptClaim(
            token.getOrderId(), member.memberId(), member.memberCode(), now));
    // 実行者は申領した本人。台帳では実行者 null が「機構が起こした仕訳」の形であり、人手の操作と混ぜない。
    Long grantEntryId =
        pointLedgerService.grantPlannedForOrder(
            member.memberId(),
            token.getOrderId(),
            order.getStoreId(),
            token.getPlannedPoints(),
            platformUserId);
    // 来店特典の窓は根拠受注の営業日で判じる（申領した日ではない）。申領は最大 90 日遅れるが、遅れているのは
    // 申領という手続きであって発火した事実ではない。適用期間が閉じた後でも窓内の受注には付与が起こりうる。
    benefitGrantService.grantVisitBenefits(
        member.memberId(),
        token.getOrderId(),
        order.getStoreId(),
        order.getBusinessDate(),
        platformUserId);
    // 事後申領も帰属の成立と同時に付与を記帳するため、完了経路と同じくここで昇格を判定する。
    memberRankSync.afterGrant(member.memberId(), grantEntryId);
    return new MemberReceiptClaimResponse(token.getPlannedPoints());
  }

  private Long resolvePlatformUserId(String email) {
    return platformUserRepository
        .findByEmail(email)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }

  private MemberLookup resolveMember(Long platformUserId) {
    return memberLookupService
        .findByPlatformUserId(platformUserId)
        .orElseThrow(() -> new StaleSessionException("会員情報が存在しません"));
  }
}
