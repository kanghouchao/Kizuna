import { BenefitRuleRepeatPolicy, BenefitRuleType } from './types';

const TYPE_LABELS: Record<BenefitRuleType, string> = {
  REFERRAL: '紹介',
  LOGIN: 'ログイン',
  VISIT: '来店',
};

const REPEAT_POLICY_LABELS: Record<BenefitRuleRepeatPolicy, string> = {
  ONCE_PER_MEMBER: '一人一回限り',
  EVERY_TIME: '毎回',
};

/** 種別の訳語。目録に無い値はコードのまま出す（サーバが種別を増やした日に空欄へ落とさない）。 */
export function benefitRuleTypeLabel(type: BenefitRuleType | undefined): string {
  return type === undefined ? '' : (TYPE_LABELS[type] ?? type);
}

export function benefitRuleRepeatPolicyLabel(policy: BenefitRuleRepeatPolicy | undefined): string {
  return policy === undefined ? '' : (REPEAT_POLICY_LABELS[policy] ?? policy);
}
