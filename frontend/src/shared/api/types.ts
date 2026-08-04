// ページネーションと検索用の共通パラメータ
export interface PaginationParams {
  page?: number;
  size?: number;
  sort?: string;
  search?: string;
}

// カーソルページングの問い合わせパラメータ。位置は「何件目か」ではなく前回応答の next_cursor で指す
export interface CursorParams {
  cursor?: string;
  size?: number;
}

// カーソルページングのレスポンス。総件数は持たず、続きの有無だけを伝える
export interface CursorPage<T> {
  content: T[];
  // 続きが無いときは応答から項目ごと省かれる（サーバ側の null 非出力方針）
  next_cursor?: string;
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
