package com.kizuna.point.domain;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitRuleRepository extends JpaRepository<BenefitRule, Long> {

  /** 一覧は新しい規則から並べる。offset ページングの全順序のため、一意な副キー（id）を必ず添える。 */
  Page<BenefitRule> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

  /**
   * 停用されていない指定種別の規則。
   *
   * <p>適用期間と適用店舗まで問い合わせで絞らないのは、その判定が集約の不変条件の一部だからである（{@link
   * BenefitRule#firesFor}）。問い合わせ側に写すと、同じ規則が単体テストの届かない JPQL の中で二度表現される。 生きた規則は施策の数で、種別ごとに数件を超えない。
   */
  List<BenefitRule> findByTypeAndEnabledTrue(BenefitRuleType type);
}
