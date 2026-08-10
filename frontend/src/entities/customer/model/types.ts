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
