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
   * 会員へ帰属した受注 1 件に対する来店特典の記帳。適用のある規則ごとに 1 行を積む（規則間の排他は無い）。
   *
   * <p><b>呼出側が会員行を押さえていること</b>を前提にする。「一人一回限り」の判定は受益歴を読んでから積む check-then-act
   * なので、同じ会員への並行する契機が直列化されていないと双方が判定を通り抜ける。呼出の 2 経路はどちらも会員へ外部キーを張る書き込みの前に会員行のロックを取っており（{@code
   * MemberRankSync#beforeMemberWrites}）、この記帳はその内側で回る。
   *
   * @param orderDate 根拠受注の営業日。適用期間の窓はこの日で判じる（記帳した日ではない）
   * @param actorUserId 契機を起こした主体。完了なら操作した従業員、事後申領なら申領した会員本人
   */
  public void grantVisitBenefits(
      long memberId, String orderId, Long storeId, LocalDate orderDate, Long actorUserId) {
    LocalDate grantedOn = LocalDate.now(ZoneId.of(appProperties.getTimezone()));
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
    }
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
