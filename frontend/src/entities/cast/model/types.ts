// キャストの招待状態（四態。cast/domain/CastInvitationStatus.java と対応）
export type CastInvitationStatus = 'NOT_INVITED' | 'INVITED' | 'EXPIRED' | 'LINKED';

// キャスト（Cast）レスポンス
export interface CastResponse {
  id: string;
  name: string;
  status: string;
  photo_url?: string;
  introduction?: string;
  age?: number;
  height?: number;
  bust?: number;
  waist?: number;
  hip?: number;
  display_order?: number;
  invitation_status: CastInvitationStatus;
  custom_fields?: Record<string, string>;
  created_at: string;
  updated_at: string;
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
  id: string;
  key: string;
  label: string;
  display_order: number;
  is_public: boolean;
  created_at: string;
  updated_at: string;
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
  token: string;
  expires_at: string;
}

// 招待照会（公開ランディング）の受諾可否状態
export type CastInvitationViewStatus = 'VALID' | 'EXPIRED' | 'USED';

// 招待照会（公開ランディング）レスポンス
export interface CastInvitationDetailResponse {
  store_name: string;
  cast_name: string;
  status: CastInvitationViewStatus;
  expires_at: string;
}

// 招待の新規登録受諾リクエスト
export interface CastInvitationAcceptRequest {
  email: string;
  password: string;
  display_name: string;
}

// 招待受諾の完了応答
export interface CastAcceptanceResponse {
  store_name: string;
}
