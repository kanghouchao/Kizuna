package com.kizuna.point.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitRuleRepository extends JpaRepository<BenefitRule, Long> {

  /** 一覧は新しい規則から並べる。offset ページングの全順序のため、一意な副キー（id）を必ず添える。 */
  Page<BenefitRule> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
