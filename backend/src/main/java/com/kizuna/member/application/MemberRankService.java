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
 * <p>伝播は既定の REQUIRED。判定は帰属の記録と同じトランザクションで成立するか、記録ごと巻き戻るかのどちらかであるべきで、 イベントによる非同期化はしていない。
 *
 * <p>判定の材料は order と point が持つため {@link MemberRankMetrics} 越しに読む — 自分で引くと member → order
 * の依存が生まれ、既にある order → member と環になる。読む時点をこちら側が決める理由は同 interface に記す。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MemberRankService {

  private final MemberRepository memberRepository;
  private final MemberRankHistoryRepository memberRankHistoryRepository;
  private final SystemConfigService systemConfigService;

  /**
   * 帰属の成立と同期してランクを見直し、上位の条件を満たしていれば昇格させる。
   *
   * <p>条件は OR — 完了受注の回数か付与の純額のどちらか一方の達成で足りる（高頻度客と高額客の両方を拾う）。 純額は取消仕訳の控除後なので減りうるが、ランクは戻らない（棘輪） —
   * 現在より上位でなければ何も書かない。
   *
   * @param triggeringAttributionId 今回成立した帰属記録。履歴が指す契機になる
   * @param triggeringEntryId 同時に記帳された付与仕訳。付与が 0 なら null
   */
  public void syncOnAttribution(
      long memberId,
      MemberRankMetrics metrics,
      long triggeringAttributionId,
      Long triggeringEntryId) {
    Member member =
        memberRepository
            .findByIdForUpdate(memberId)
            .orElseThrow(() -> new NotFoundException("会員が見つかりません"));
    MemberRank current = member.getRank();
    // 指標はロックを取った後に読む。先に読むと、閾値を跨ぐ 2 件の付与が並行したとき双方が同じ古い値を
    // 観測し、どちらも昇格させないまま来店が取り残される（ロックは commit まで持つので、待った側の
    // 読み直しは先行の書き込みを必ず見る）。
    MemberRank reached =
        highestReached(metrics.completedVisitCount(memberId), metrics.netGrantedPoints(memberId));
    if (!reached.isAbove(current)) {
      return;
    }
    member.promoteTo(reached);
    memberRankHistoryRepository.save(
        MemberRankHistory.promoted(
            memberId,
            current,
            reached,
            triggeringAttributionId,
            triggeringEntryId,
            OffsetDateTime.now()));
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
