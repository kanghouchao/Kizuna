import { BenefitRuleRepeatPolicy, BenefitRuleType } from './types';

// 訳語表は表示順そのままで持つ。選択肢はここから導くので、字面も並びも一箇所にしかない。
const TYPE_LABELS: Record<BenefitRuleType, string> = {
  VISIT: '来店',
  REFERRAL: '紹介',
  LOGIN: 'ログイン',
};

const REPEAT_POLICY_LABELS: Record<BenefitRuleRepeatPolicy, string> = {
  EVERY_TIME: '毎回',
  ONCE_PER_DAY: '一日一回',
  ONCE_PER_MEMBER: '一人一回限り',
};

// 種別が取れる重複可否（サーバの集約が同じ組合せを 400 で拒む）。先頭がその種別の既定値。
// 紹介は毎回のみ（一回性は被紹介者の初回受注が担う）。ログインは毎回を取れない（認証の反復で
// 無限に受益できる）。一日一回を取れるのはログインだけ。
const REPEAT_POLICIES_BY_TYPE: Record<BenefitRuleType, BenefitRuleRepeatPolicy[]> = {
  VISIT: ['EVERY_TIME', 'ONCE_PER_MEMBER'],
  REFERRAL: ['EVERY_TIME'],
  LOGIN: ['ONCE_PER_DAY', 'ONCE_PER_MEMBER'],
};

export interface BenefitRuleOption<T> {
  value: T;
  label: string;
}

function toOptions<T extends string>(labels: Record<T, string>): BenefitRuleOption<T>[] {
  return (Object.keys(labels) as T[]).map(value => ({ value, label: labels[value] }));
}

/** 種別の選択肢（表示順は訳語表の並び）。 */
export const BENEFIT_RULE_TYPE_OPTIONS = toOptions(TYPE_LABELS);

/** 種別が取れる重複可否の選択肢。先頭がその種別の既定値。 */
export function benefitRuleRepeatPolicyOptionsFor(
  type: BenefitRuleType
): BenefitRuleOption<BenefitRuleRepeatPolicy>[] {
  return REPEAT_POLICIES_BY_TYPE[type].map(value => ({
    value,
    label: REPEAT_POLICY_LABELS[value],
  }));
}

/** 種別の訳語。目録に無い値はコードのまま出す（サーバが種別を増やした日に空欄へ落とさない）。 */
export function benefitRuleTypeLabel(type: BenefitRuleType | undefined): string {
  return type === undefined ? '' : (TYPE_LABELS[type] ?? type);
}

export function benefitRuleRepeatPolicyLabel(policy: BenefitRuleRepeatPolicy | undefined): string {
  return policy === undefined ? '' : (REPEAT_POLICY_LABELS[policy] ?? policy);
}
