// 特典規則の条件種別（バックエンド point/domain/BenefitRuleType.java と対応）。
// 紹介は紹介者・被紹介者の二値、ログイン・来店は固定点数一値で付与量を表す。
export type BenefitRuleType = 'REFERRAL' | 'LOGIN' | 'VISIT';

// 重複可否（同一会員が同じ規則で別の発火事象により再び受益できるか）。
export type BenefitRuleRepeatPolicy = 'ONCE_PER_MEMBER' | 'EVERY_TIME';

// 発火側の絞り込み種別。授権の店舗集合と同じ字面だが、意味は「どの店舗の事象を拾うか」。
export type BenefitRuleStoreScopeType = 'ALL_STORES' | 'SPECIFIC_STORES';

// 一覧 1 件の要約。適用店舗は件数だけで、店舗 ID の列挙は詳細が持つ。
export interface BenefitRuleSummaryResponse {
  id?: number;
  name?: string;
  type?: BenefitRuleType;
  store_scope_type?: BenefitRuleStoreScopeType;
  // store_count / enabled は Java 側が primitive のため、キーは必ず応答に含まれる。
  store_count: number;
  effective_from?: string;
  effective_until?: string;
  grant_validity_days?: number;
  repeat_policy?: BenefitRuleRepeatPolicy;
  points?: number;
  referrer_points?: number;
  referred_points?: number;
  enabled: boolean;
}

// 詳細。編集フォームが要る店舗 ID の列挙と楽観ロック用 version を持つ。
export interface BenefitRuleResponse extends BenefitRuleSummaryResponse {
  store_ids?: number[];
  version?: number;
}

// 新規作成。種別はここでしか送れない（更新の要求型には存在しない）。
export interface BenefitRuleCreateRequest {
  name: string;
  type: BenefitRuleType;
  store_scope_type: BenefitRuleStoreScopeType;
  store_ids?: number[];
  effective_from?: string | null;
  effective_until?: string | null;
  grant_validity_days?: number | null;
  repeat_policy: BenefitRuleRepeatPolicy;
  points?: number | null;
  referrer_points?: number | null;
  referred_points?: number | null;
}

// 更新（種別以外の全量置換）。type を持たせると未知項目として 400 になる。
// version は取得した詳細のものをそのまま往復する（不一致は 409）。
export type BenefitRuleUpdateRequest = Omit<BenefitRuleCreateRequest, 'type'> & {
  version: number;
};
