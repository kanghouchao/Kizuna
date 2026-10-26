/** 会員登録リクエスト（POST /members）。member/api/dto/MemberRegistrationRequest.java に対応。 */
export interface MemberRegisterRequest {
  email: string;
  password: string;
  display_name: string;
}

/** 会員登録応答。member/api/dto/MemberRegistrationResponse.java に対応。 */
export interface MemberRegisterResponse {
  member_code?: string;
}

/** 会員ポータルホーム応答。member/api/dto/MemberHomeResponse.java に対応。 */
export interface MemberHomeResponse {
  member_code?: string;
  display_name?: string;
}

/** 本人による退会の要求（POST /me/member/withdrawal）。memberwithdrawal/api/dto/MemberWithdrawalRequest.java に対応。 */
export interface MemberWithdrawalRequest {
  reason: string;
}

/** 会員検索の状態条件（GET /members の status）。既定は有効。 */
export type MemberStatusFilter = 'ACTIVE' | 'WITHDRAWN' | 'ALL';

/** 会員ランク。member/domain/MemberRank.java に対応。 */
export type MemberRank = 'BRONZE' | 'SILVER' | 'GOLD';

export const MEMBER_RANK_LABELS: Record<MemberRank, string> = {
  BRONZE: 'ブロンズ',
  SILVER: 'シルバー',
  GOLD: 'ゴールド',
};

/** 職員が見る会員一覧 1 行。memberconsole/api/dto/MemberSummaryResponse.java に対応。 */
export interface AdminMemberSummary {
  /** Java 側が primitive の long のため、キーは必ず応答に含まれる。 */
  id: number;
  member_code?: string;
  display_name?: string;
  email?: string;
  rank?: MemberRank;
  registered_at?: string;
  /** 退会済みだけが持つ。 */
  withdrawn_at?: string;
}

/** 退会の事実。memberconsole/api/dto/MemberDetailResponse.Withdrawal に対応。 */
export interface AdminMemberWithdrawal {
  withdrawn_at?: string;
  reason?: string;
  /** Java 側が primitive の boolean のため、キーは必ず応答に含まれる。 */
  by_self: boolean;
  /** 操作者の表示名。操作者の行が消えていれば応答から消える。 */
  withdrawn_by_name?: string;
}

export interface AdminMemberExpiryBalance {
  /** 期限なしは応答から消える。 */
  expires_on?: string;
  points: number;
}

export interface AdminMemberRankTransition {
  previous_rank?: MemberRank;
  new_rank?: MemberRank;
  promoted_at?: string;
}

/** 閲覧者の店舗集合内の関連 1 区間。 */
export interface AdminMemberLink {
  id?: string;
  store_id: number;
  store_name?: string;
  customer_id?: string;
  customer_name?: string;
  status?: 'ACTIVE' | 'RELEASED';
  reason?: 'MEMBER_CODE' | 'MEMBER_REQUEST' | 'MIGRATION';
  linked_at?: string;
  released_at?: string;
  release_reason?: string;
}

/** 職員が見る会員の詳細。memberconsole/api/dto/MemberDetailResponse.java に対応。 */
export interface AdminMemberDetail {
  id: number;
  member_code?: string;
  display_name?: string;
  email?: string;
  line_linked: boolean;
  rank?: MemberRank;
  registered_at?: string;
  /** 未退会なら応答から消える。 */
  withdrawal?: AdminMemberWithdrawal;
  balance: number;
  balances: AdminMemberExpiryBalance[];
  rank_history: AdminMemberRankTransition[];
  links: AdminMemberLink[];
  other_store_link_count: number;
}

/** 職員が見る明細 1 行。memberconsole/api/dto/MemberLedgerEntryResponse.java に対応。 */
export interface AdminMemberLedgerEntry {
  occurred_at?: string;
  entry_type?: string;
  amount: number;
  expires_on?: string;
  store_name?: string;
  reason?: string;
  actor_name?: string;
}

/** 職員による代行退会の要求（POST /members/{id}/withdrawal）。 */
export interface StaffWithdrawalRequest {
  reason: string;
}
