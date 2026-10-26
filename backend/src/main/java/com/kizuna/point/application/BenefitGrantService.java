package com.kizuna.point.application;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.point.domain.BenefitRule;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.point.domain.BenefitRuleType;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import java.time.Clock;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 特典規則が台帳へ付与を産む経路（来店・ログイン。紹介は紹介関係データを待つ）。
 *
 * <p>来店は帰属が物化する契機（受注完了・事後申領）に同期・同一取引で評価し、失敗すれば呼び元ごと巻き戻る — 黙って落とす退避路は持たない。
 * ログインは認証と別取引で記帳し、失敗の記録は呼び元（auth）が担う。「今日」は業務時間帯の時計で読む。
 */
@Service
@Transactional
@Slf4j
public class BenefitGrantService {

  private final BenefitRuleRepository benefitRuleRepository;
  private final PointEntryRepository pointEntryRepository;
  private final MemberLookupService memberLookupService;
  private final Clock clock;

  // 規則ごとの記帳を独立の取引に包み、同日並行ログインの索引違反を取引の外で受ける。内側で捕まえると
  // 保存を通した代理が取引を rollback-only に印し、commit が UnexpectedRollbackException で落ちる。
  private final TransactionTemplate loginGrant;

  public BenefitGrantService(
      BenefitRuleRepository benefitRuleRepository,
      PointEntryRepository pointEntryRepository,
      MemberLookupService memberLookupService,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.benefitRuleRepository = benefitRuleRepository;
    this.pointEntryRepository = pointEntryRepository;
    this.memberLookupService = memberLookupService;
    this.clock = clock;
    this.loginGrant = new TransactionTemplate(transactionManager);
    this.loginGrant.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * 帰属した受注に適用される規則ごとに特典を記帳する。適用窓は受注の営業日、点数と有効期間は評価時点の規則で判じる。
   * 完了時点では申領者が未定で一回性を判じられないため、規則を凍結しない。規則間の排他はなく、合計は long で返す。
   */
  public long grantVisitBenefits(
      long memberId, String orderId, Long storeId, LocalDate orderDate, Long actorUserId) {
    LocalDate grantedOn = LocalDate.now(clock);
    long granted = 0;
    for (BenefitRule rule : benefitRuleRepository.findByTypeAndEnabledTrue(BenefitRuleType.VISIT)) {
      if (!rule.firesFor(storeId, orderDate) || alreadyBenefited(rule, memberId, orderId, null)) {
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
   * MEMBER 利用者の認証成功 1 回を発火事象として、適用されるログイン規則ごとに特典を記帳する。会員身分を持たない利用者は何もしない。
   *
   * <p>呼び元（認証）の取引には参加しない。規則ごとの判定と記帳はそれぞれ独立の取引で、一意索引以外の失敗はそのまま投げる —
   * 記録して認証を通すのは呼び元の責務で、ここで吞むと台帳の失敗が誰にも見えない。
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void grantLoginBenefits(long userId) {
    MemberLookup member = memberLookupService.findByUserId(userId).orElse(null);
    if (member == null) {
      return;
    }
    LocalDate today = LocalDate.now(clock);
    for (BenefitRule rule : benefitRuleRepository.findByTypeAndEnabledTrue(BenefitRuleType.LOGIN)) {
      if (!rule.firesFor(null, today)) {
        continue;
      }
      try {
        loginGrant.executeWithoutResult(status -> grantLogin(rule, member.memberId(), today));
      } catch (DataIntegrityViolationException ex) {
        if (!IntegrityViolations.violates(ex, DbConstraint.UQ_T_POINT_ENTRIES_LOGIN_BENEFIT_DAY)) {
          throw ex;
        }
        log.debug("同日の並行ログインに先を越されたため記帳しない memberId={} ruleId={}", member.memberId(), rule.getId());
      }
    }
  }

  private void grantLogin(BenefitRule rule, long memberId, LocalDate today) {
    if (alreadyBenefited(rule, memberId, null, today)) {
      return;
    }
    pointEntryRepository.saveAndFlush(
        PointEntry.grantForLogin(
            memberId, rule.getPoints(), rule.grantExpiryOn(today), rule.getId(), today));
  }

  /**
   * その規則の付与を今回は積まないか。一人一回限りなら会員の受益歴、一日一回ならその暦日の受益、毎回なら<b>この発火事象（根拠受注）</b>での受益を見る。
   *
   * <p>毎回でも判じるのは、帰属の無効化と再申領で同じ受注が二度契機になりうるためである。一意索引が同じ組の二度書きを 最終的に撥ねるが、正当な経路が 500 を踏まないよう入口でも判じる。
   */
  private boolean alreadyBenefited(
      BenefitRule rule, long memberId, String orderId, LocalDate grantedOn) {
    return switch (rule.getRepeatPolicy()) {
      case ONCE_PER_MEMBER ->
          pointEntryRepository.existsByBenefitRuleIdAndMemberId(rule.getId(), memberId);
      case ONCE_PER_DAY ->
          pointEntryRepository.existsByBenefitRuleIdAndMemberIdAndGrantedOn(
              rule.getId(), memberId, grantedOn);
      case EVERY_TIME ->
          pointEntryRepository.existsByBenefitRuleIdAndMemberIdAndOrderId(
              rule.getId(), memberId, orderId);
    };
  }
}
