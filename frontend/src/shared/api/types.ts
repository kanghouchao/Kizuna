// ページネーションと検索用の共通パラメータ
export interface PaginationParams {
  page?: number;
  size?: number;
  sort?: string;
  search?: string;
}

// Spring Data の Page レスポンス（JSON キーは Jackson グローバル SNAKE_CASE）
export interface Page<T> {
  content: T[];
  total_pages: number;
  total_elements: number;
  size: number;
  number: number;
}

// 公開設定。記事と口コミが同じ形で持つ値オブジェクト（日時は ISO 8601 の瞬間）
// 後端は null の項目を応答から省くため、未設定の項目は欠落として届く。
export interface Publication {
  published: boolean;
  publish_from?: string | null;
  publish_until?: string | null;
  display_order?: number | null;
}

// 公開画像の用途。保存先 key の一段になり、バックエンドが同じ許可リストで検証する
export type UploadPurpose =
  'cast-photo' | 'store-logo' | 'store-banner' | 'store-mv' | 'partner-logo';

// ファイルアップロードレスポンス
export interface FileUploadResponse {
  url?: string;
  original_name?: string;
  // Java 側が primitive の long のため、キーは必ず応答に含まれる
  size: number;
}

// トークンを返す端点（ログイン系と /auth/refresh）の応答。リフレッシュトークン自体は HttpOnly cookie で届く。
// Java 側が primitive の long のため、数値のキーは必ず応答に含まれる。
export interface TokenResponse {
  token: string;
  expires_at: number;
  user_id: number;
  // リフレッシュトークンの期限（epoch ミリ秒）。session-kind cookie の期限をこれに揃える。
  refresh_expires_at: number;
}
