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
