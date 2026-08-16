// キャストの招待状態（四態。cast/domain/CastInvitationStatus.java と対応）
export type CastInvitationStatus = 'NOT_INVITED' | 'INVITED' | 'EXPIRED' | 'LINKED';

// キャスト（Cast）レスポンス
export interface CastResponse {
  id?: string;
  name?: string;
  status?: string;
  photo_url?: string;
  introduction?: string;
  age?: number;
  height?: number;
  bust?: number;
  waist?: number;
  hip?: number;
  display_order?: number;
  // 作成・更新の応答では招待状態を載せないため、一覧・詳細以外では欠落する
  invitation_status?: CastInvitationStatus;
  custom_fields?: Record<string, string>;
  created_at?: string;
  updated_at?: string;
}

/**
 * キャスト一覧の 1 行（GET /store/casts）。名簿として見分け、招待の進み具合を判断するのに
 * 要る項目だけを持つ。紹介文・カスタム項目・作成更新時刻は詳細の読み口が返す。
 */
export interface CastSummaryResponse {
  id?: string;
  name?: string;
  status?: string;
  photo_url?: string;
  age?: number;
  bust?: number;
  waist?: number;
  hip?: number;
  display_order?: number;
  invitation_status?: CastInvitationStatus;
}

// 公開カスタムフィールド1件（表示順どおりに整形済み）
export interface CastCustomFieldView {
  key?: string;
  label?: string;
  value?: string;
}

// 公開キャスト詳細レスポンス（GET /store/casts/public）。管理用 CastResponse と異なり
// invitation_status を持たず、custom_fields は公開・生存・値ありの定義のみを表示順に整形した配列。
export interface CastPublicResponse {
  id?: string;
  name?: string;
  status?: string;
  photo_url?: string;
  introduction?: string;
  age?: number;
  height?: number;
  bust?: number;
  waist?: number;
  hip?: number;
  display_order?: number;
  custom_fields?: CastCustomFieldView[];
  created_at?: string;
  updated_at?: string;
}

// キャスト作成リクエスト
export interface CastCreateRequest {
  name: string;
  status?: string;
  photo_url?: string;
  introduction?: string;
  age?: number;
  height?: number;
  bust?: number;
  waist?: number;
  hip?: number;
  display_order?: number;
}

// キャスト更新リクエスト
export interface CastUpdateRequest {
  name?: string;
  status?: string;
  photo_url?: string;
  introduction?: string;
  age?: number;
  height?: number;
  bust?: number;
  waist?: number;
  hip?: number;
  display_order?: number;
  // 省略時は既存値を変更しない。指定時は全置換（マージしない）。
  custom_fields?: Record<string, string>;
}

// カスタムフィールド定義レスポンス
export interface CastFieldDefinitionResponse {
  id?: string;
  key?: string;
  label?: string;
  display_order?: number;
  is_public?: boolean;
  created_at?: string;
  updated_at?: string;
}

// カスタムフィールド定義作成リクエスト（key は不変のため作成時のみ指定）
export interface CastFieldDefinitionCreateRequest {
  key: string;
  label: string;
  is_public?: boolean;
}

// カスタムフィールド定義更新リクエスト（key は含まない）
export interface CastFieldDefinitionUpdateRequest {
  label?: string;
  display_order?: number;
  is_public?: boolean;
}

// キャスト招待発行レスポンス
export interface CastInvitationIssueResponse {
  token?: string;
  expires_at?: string;
}

// 招待照会（公開ランディング）の受諾可否状態
export type CastInvitationViewStatus = 'VALID' | 'EXPIRED' | 'USED';

// 招待照会（公開ランディング）レスポンス
export interface CastInvitationDetailResponse {
  store_name?: string;
  cast_name?: string;
  status?: CastInvitationViewStatus;
  expires_at?: string;
}

// 招待の新規登録受諾リクエスト
export interface CastInvitationAcceptRequest {
  email: string;
  password: string;
  display_name: string;
}

// 招待受諾の完了応答
export interface CastAcceptanceResponse {
  store_name?: string;
}
