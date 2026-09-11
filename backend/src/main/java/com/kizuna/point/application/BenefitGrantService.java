package com.kizuna.point.application;

import com.kizuna.point.domain.BenefitRule;
import com.kizuna.point.domain.BenefitRuleRepeatPolicy;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.point.domain.BenefitRuleType;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.config.AppProperties;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 特典規則が台帳へ付与を産む経路。
 *
 * <p>伝播は既定の REQUIRED。検知は同期・同一トランザクションで、受注条件の特典は<b>帰属が物化する契機</b>（受注完了、および 伝票トークンの事後申領）に評価する —
 * 会員ランクの判定と同じ契機の鏡像である。特典の付与が失敗すれば呼び元の完了・申領ごと巻き戻る。 付与を黙って落とす退避路は持たない —
 * 落ちた付与は台帳に痕跡を残さず、後から取り戻す手掛かりが無い。
 *
 * <p>投産しているのは<b>来店</b>だけ。紹介は紹介関係データを、ログインは現行粒度の業務確認を待つ。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BenefitGrantService {

  private final BenefitRuleRepository benefitRuleRepository;
  private final PointEntryRepository pointEntryRepository;
  private final AppProperties appProperties;

  /**
   * 帰属した受注に適用される規則ごとに特典を記帳する。適用窓は受注の営業日、点数と有効期間は評価時点の規則で判じる。
   * 完了時点では申領者が未定で一回性を判じられないため、規則を凍結しない。規則間の排他はなく、合計は long で返す。
   */
  public long grantVisitBenefits(
      long memberId, String orderId, Long storeId, LocalDate orderDate, Long actorUserId) {
    LocalDate grantedOn = LocalDate.now(ZoneId.of(appProperties.getTimezone()));
    long granted = 0;
    for (BenefitRule rule : benefitRuleRepository.findByTypeAndEnabledTrue(BenefitRuleType.VISIT)) {
      if (!rule.firesFor(storeId, orderDate) || alreadyBenefited(rule, memberId, orderId)) {
        continue;
      }
      pointEntryRepository.save(
          PointEntry.grantForBenefit(
              memberId,
              orderId,
              storeId,
              rule.getPoints(),
              rule.grantExpiryOn(grantedOn),
              rule.getId(),
              actorUserId));
      granted += rule.getPoints();
    }
    return granted;
  }

  /**
   * その規則の付与を今回は積まないか。一人一回限りなら会員が既に受益しているか、毎回なら<b>この発火事象で</b>既に受益しているかを見る。
   *
   * <p>後者が要るのは、帰属の無効化と再申領で同じ受注が二度契機になりうるためである。一意索引が同じ組の二度書きを 最終的に撥ねるが、正当な経路が 500 を踏まないよう入口でも判じる。
   */
  private boolean alreadyBenefited(BenefitRule rule, long memberId, String orderId) {
    if (rule.getRepeatPolicy() == BenefitRuleRepeatPolicy.ONCE_PER_MEMBER) {
      return pointEntryRepository.existsByBenefitRuleIdAndMemberId(rule.getId(), memberId);
    }
    return pointEntryRepository.existsByBenefitRuleIdAndMemberIdAndOrderId(
        rule.getId(), memberId, orderId);
  }
}
