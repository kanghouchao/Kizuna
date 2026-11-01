package com.kizuna.order.application;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.order.domain.MemberVisitView;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.application.ActorIdentityService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員ポータルからの来店履歴の読み取りユースケース。
 *
 * <p>来店として見えるのは有効な帰属記録を持つ受注だけで、関連（Customer–Member Link）の区間は読み直さない（ADR 0009）。したがって関連を解除しても過去の来店は
 * 見え続け、関連成立以前・無帰属・未完了・謝絶／取下げの受注は現れない。本人が申請した予約の追跡は {@link MemberOrderApplicationService}
 * が別の読み口として並置する。
 *
 * <p>帰属記録は店舗で分割されない（台帳と同じ グループ帰属）。本人の会員 ID の一致を問い合わせ自体に載せることが唯一の隔離境界である。
 *
 * <p>返すのは表示用の projection だけで、書き込みの口は持たない。会計金額・利用ポイント・顧客台帳の内部項目は projection の段階で落ちる。
 */
@Service
@RequiredArgsConstructor
public class MemberVisitService {

  private final ActorIdentityService actorIdentityService;
  private final MemberLookupService memberLookupService;
  private final OrderAttributionRepository orderAttributionRepository;
  private final PointLedgerService pointLedgerService;

  /** 本人の来店履歴（跨店集約）。 */
  @Transactional(readOnly = true)
  public Page<MemberVisitSummary> list(String email, Pageable pageable) {
    Long memberId = resolveMemberId(email);
    Page<MemberVisitView> page =
        orderAttributionRepository.findMemberVisitViews(memberId, pageable);
    // 獲得ポイントは台帳にしか無いので、返す行の分だけをまとめて引く（行ごとに引くと問い合わせが線形に増える）。
    // 本人の一致は台帳側の問い合わせにも載せる — 誤帰属を無効化しても付与行は残るため、受注 ID だけで
    // 足すと、その受注を申領した本人の来店に前の会員の付与が現れる。
    Map<String, Long> grantedPoints =
        pointLedgerService.grantedPointsByOrder(
            memberId, page.getContent().stream().map(MemberVisitView::getOrderId).toList());
    return page.map(view -> toSummary(view, grantedPoints));
  }

  private Long resolveMemberId(String email) {
    Long userId = actorIdentityService.requireUserId(email);
    return memberLookupService
        .findByUserId(userId)
        .map(MemberLookup::memberId)
        .orElseThrow(() -> new StaleSessionException("会員情報が存在しません"));
  }

  /** 付与行の無い受注は台帳に現れないので 0 とみなす — 0 円完了でも帰属記録は生まれ、来店としては見える。 */
  private MemberVisitSummary toSummary(MemberVisitView view, Map<String, Long> grantedPoints) {
    return new MemberVisitSummary(
        view.getVisitedOn(),
        view.getStoreName(),
        view.getPax(),
        view.getCastName(),
        grantedPoints.getOrDefault(view.getOrderId(), 0L));
  }
}
