package com.kizuna.member.application;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRank;
import com.kizuna.member.domain.MemberRankHistory;
import com.kizuna.member.domain.MemberRankHistoryRepository;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.settings.application.MemberRankSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.NotFoundException;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員ランクの昇格判定。
 *
 * <p>伝播は既定の REQUIRED。判定は付与の記帳と同じトランザクションで成立するか、記帳ごと巻き戻るかのどちらかであるべきで、 イベントによる非同期化はしていない。
 *
 * <p>判定の材料（跨店舗の来店回数・付与の純額）は order と point が持ち、この層は受け取るだけである — 材料を自分で引くと member → order の依存が生まれ、既にある
 * order → member と環になる。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MemberRankService {

  private final MemberRepository memberRepository;
  private final MemberRankHistoryRepository memberRankHistoryRepository;
  private final SystemConfigService systemConfigService;

  /**
   * 付与の記帳と同期してランクを見直し、上位の条件を満たしていれば昇格させる。
   *
   * <p>条件は OR — 完了受注の回数か付与の純額のどちらか一方の達成で足りる（高頻度客と高額客の両方を拾う）。 純額は取消仕訳の控除後なので減りうるが、ランクは戻らない（棘輪） —
   * 現在より上位でなければ何も書かない。
   *
   * @param completedVisitCount 会員へ帰属した完了受注の回数（跨店舗合計）
   * @param netGrantedPoints 受注付与の累計純額（取消仕訳の控除後）
   */
  public void syncOnGrant(
      long memberId, long completedVisitCount, long netGrantedPoints, long triggeringEntryId) {
    Member member =
        memberRepository
            .findByIdForUpdate(memberId)
            .orElseThrow(() -> new NotFoundException("会員が見つかりません"));
    MemberRank current = member.getRank();
    MemberRank reached = highestReached(completedVisitCount, netGrantedPoints);
    if (!reached.isAbove(current)) {
      return;
    }
    member.promoteTo(reached);
    memberRankHistoryRepository.save(
        MemberRankHistory.promoted(
            memberId, current, reached, triggeringEntryId, OffsetDateTime.now()));
  }

  /** 指標が届いている最上位のランク。閾値は都度読むので、設定の変更は次回の判定から効く。 */
  private MemberRank highestReached(long completedVisitCount, long netGrantedPoints) {
    MemberRankSettings settings = systemConfigService.memberRankSettings();
    if (settings.gold().isReachedBy(completedVisitCount, netGrantedPoints)) {
      return MemberRank.GOLD;
    }
    if (settings.silver().isReachedBy(completedVisitCount, netGrantedPoints)) {
      return MemberRank.SILVER;
    }
    return MemberRank.BRONZE;
  }
}
