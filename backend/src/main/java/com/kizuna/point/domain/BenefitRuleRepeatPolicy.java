package com.kizuna.point.domain;

/** 特典規則の重複可否。同一会員が同じ規則で<b>別の</b>発火事象により再び受益できるかを表す。 */
public enum BenefitRuleRepeatPolicy {
  /** 一人一回限り。 */
  ONCE_PER_MEMBER,
  /** 一日一回。日はグループ時間帯（{@code app.timezone}）の暦日で切る。 */
  ONCE_PER_DAY,
  /** 毎回。 */
  EVERY_TIME
}
