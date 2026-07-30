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

// ファイルアップロードレスポンス
export interface FileUploadResponse {
  url?: string;
  original_name?: string;
  // Java 側が primitive の long のため、キーは必ず応答に含まれる
  size: number;
}
