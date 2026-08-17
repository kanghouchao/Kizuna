// 顧客（Customer）レスポンス
export interface CustomerResponse {
  id?: string;
  name?: string;
  phone_number?: string;
  phone_number2?: string;
  address?: string;
  building_name?: string;
  classification?: string;
  has_pet?: boolean;
  rank?: string;
  line_id?: string;
  usage_areas?: string;
  ng_type?: string;
  ng_content?: string;
  // 会員紐づけの有無。一覧・詳細・作成・更新の応答では常に真偽値が入る
  member_linked?: boolean;
  // 紐づけ済みの会員コード。未紐づけなら欠落
  linked_member_code?: string;
  // 統合済みの旧 ID で取得したか。生きた行を取得した応答では欠落する
  merged?: boolean;
  // 要求した旧 ID。本体は統合先の行なので、id との対で統合の向きが判る
  merged_from_id?: string;
}

/**
 * 顧客一覧の 1 行（GET /store/customers）。目当ての顧客を絞り込んで選ぶのに要る項目だけを持つ。
 *
 * 住所・NG の内容・利用エリアは詳細の読み口が返す。会員コードも持たない — 一覧で要るのは
 * 紐づけの有無だけである。
 */
export interface CustomerSummaryResponse {
  id?: string;
  name?: string;
  phone_number?: string;
  line_id?: string;
  rank?: string;
  classification?: string;
  /** 会員紐づけの有無。一覧の応答では常に真偽値が入る。 */
  member_linked?: boolean;
  ng_type?: string;
}

/**
 * 統合の前に見比べる 1 行（GET /store/customers/duplicates のグループの中身、
 * および GET /store/customers/merge-comparison）。
 * customer/api/dto/CustomerMergeComparisonResponse.java に対応。
 *
 * 一覧の型より項目が多いのは、この読み口の用途が「絞り込んで選ぶ」ではなく「別人を誤って
 * 畳まないための見比べ」だからである。統合済みかどうかの欄は持たない — 見比べる対象は定義上
 * すべて生きた行である。
 */
export interface CustomerMergeComparisonResponse {
  id?: string;
  name?: string;
  phone_number?: string;
  phone_number2?: string;
  address?: string;
  building_name?: string;
  classification?: string;
  has_pet?: boolean;
  rank?: string;
  line_id?: string;
  usage_areas?: string;
  ng_type?: string;
  ng_content?: string;
  /** Java 側が primitive のため、キーは必ず応答に含まれる。 */
  member_linked: boolean;
  /** 状態を問わない受注の件数。Java 側が primitive のため、キーは必ず応答に含まれる。 */
  order_count: number;
}

/**
 * 同じ第一電話番号を持つ生きた顧客のグループ。必ず 2 行以上を含む。
 *
 * customers は上限で切られうるので、件数の表示には必ず total を使う（length を出すと
 * 200 件のグループが 20 件と名乗る）。
 */
export interface CustomerDuplicateGroupResponse {
  phone_number?: string;
  /** その番号を持つ生きた行の総数。Java 側が primitive のため、キーは必ず応答に含まれる。 */
  total: number;
  customers: CustomerMergeComparisonResponse[];
}

/**
 * 実行された顧客統合の応答。customer/api/dto/CustomerMergeResponse.java に対応。
 *
 * 件数は統合が実際に移した数で、統合履歴に残る値と同一である。取り消す端点は無い（ADR 0010）。
 */
export interface CustomerMergeResponse {
  surviving_customer_id: string;
  moved_order_count: number;
  moved_link_count: number;
}

/**
 * 統合履歴 1 件が、開いている顧客から見てどちら側だったか。
 * customer/api/dto/MergeDirection.java に対応。
 *
 * SURVIVING はこの行が存続行として統合を受けた側、MERGED はこの行が被統合となり
 * 墓標になった側。同じ 1 件が、相手の画面では逆の値で現れる。
 */
export type CustomerMergeDirection = 'SURVIVING' | 'MERGED';

/**
 * 統合履歴 1 件（GET /store/customers/{id}/merges）。
 * customer/api/dto/CustomerMergeHistoryResponse.java に対応。
 *
 * 統合に取消は無く、誤統合の修復は「誰が・いつ・どの行をどの行へ・何を移したか」を根拠と
 * する人手作業である（ADR 0010）。相手の行は id と表示名の両方を持つ。
 */
export interface CustomerMergeHistoryResponse {
  id: string;
  direction: CustomerMergeDirection;
  counterpart_customer_id: string;
  /** 相手の行の名前。台帳の name 列は NOT NULL ではないため欠けうる。 */
  counterpart_customer_name?: string;
  /** 実行者の表示名。実行者が削除されると欠ける（行そのものは残る）。 */
  merged_by_name?: string;
  merged_at: string;
  /** Java 側が primitive のため、キーは必ず応答に含まれる。 */
  moved_order_count: number;
  /** Java 側が primitive のため、キーは必ず応答に含まれる。 */
  moved_link_count: number;
}

/** 会員紐づけの関連状態。customer/domain/LinkStatus.java と対応。 */
export type CustomerMemberLinkStatus = 'ACTIVE' | 'RELEASED';

/** 会員紐づけリクエスト。customer/api/dto/CustomerMemberLinkRequest.java に対応。 */
export interface CustomerMemberLinkRequest {
  member_code: string;
}

/** 会員紐づけ応答。customer/api/dto/CustomerMemberLinkResponse.java に対応。 */
export interface CustomerMemberLinkResponse {
  linked: boolean;
  member_code?: string;
  linked_at?: string;
}

/** 会員ポイント残高。customer/api/dto/CustomerPointBalanceResponse.java に対応。 */
export interface CustomerPointBalanceResponse {
  // 顧客が会員に紐づいているか。Java 側が primitive の boolean のため、キーは必ず応答に含まれる
  linked: boolean;
  // 紐づく会員の現在残高。未紐づけの顧客には台帳そのものが無いため欠落する
  balance?: number;
}

/** 会員ポイントの手動調整。customer/api/dto/CustomerPointAdjustmentRequest.java に対応。 */
export interface CustomerPointAdjustmentRequest {
  delta: number;
  reason: string;
  /** 加算するポイントの有効期限。無期限なら省略する（減算に指定するとサーバが撥ねる）。 */
  expires_on?: string;
  /** クライアント生成の冪等キー。応答喪失後の再送を初回と同じ操作として識別する（ADR 0007）。 */
  idempotency_key: string;
}

/** 会員紐づけ履歴 1 件。customer/api/dto/CustomerMemberLinkHistoryResponse.java に対応。 */
export interface CustomerMemberLinkHistoryResponse {
  id?: string;
  member_code?: string;
  status?: CustomerMemberLinkStatus;
  linked_at?: string;
  linked_by_name?: string;
  released_at?: string;
  released_by_name?: string;
}

// 顧客作成リクエスト
export interface CustomerCreateRequest {
  name: string;
  phone_number?: string;
  phone_number2?: string;
  address?: string;
  building_name?: string;
  classification?: string;
  has_pet?: boolean;
  rank?: string;
  line_id?: string;
  usage_areas?: string;
  ng_type?: string;
  ng_content?: string;
}

// 顧客更新リクエスト
export interface CustomerUpdateRequest {
  name?: string;
  phone_number?: string;
  phone_number2?: string;
  address?: string;
  building_name?: string;
  classification?: string;
  has_pet?: boolean;
  rank?: string;
  line_id?: string;
  usage_areas?: string;
  ng_type?: string;
  ng_content?: string;
}
