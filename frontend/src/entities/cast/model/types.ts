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
}

// キャスト招待発行レスポンス
export interface CastInvitationIssueResponse {
  token: string;
  expires_at: string;
}
