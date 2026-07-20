import { cookies } from 'next/headers';

/**
 * サーバーサイド専用のAPIクライアント
 * Next.js Server Components 内で使用することを想定しています。
 *
 * 機能:
 * - バックエンドAPIのベースURLの自動解決
 * - 店舗コンテキスト (X-Store-ID) の自動注入
 * - 共通のエラーハンドリング
 */
export const serverClient = {
  /**
   * GETリクエストを実行します
   * @param path APIパス (例: '/store/casts/public')
   * @param options fetchオプション
   */
  async get<T>(path: string, options?: RequestInit): Promise<T> {
    const url = this.resolveUrl(path);
    const headers = await this.getHeaders(options?.headers);

    const response = await fetch(url, {
      ...options,
      headers,
    });

    if (!response.ok) {
      throw new Error(`APIエラー: ${response.status} ${response.statusText} (${path})`);
    }

    return response.json();
  },

  /**
   * APIの完全なURLを解決します
   */
  resolveUrl(path: string): string {
    const backendUrl =
      process.env.STORE_LOOKUP_API_URL?.replace('/platform/stores/lookup', '') ||
      'http://backend:8080';

    // pathがスラッシュで始まっていない場合の補正
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;
    return `${backendUrl}${normalizedPath}`;
  },

  /**
   * 必要なヘッダーを構築します
   * Cookieから店舗IDを自動的に取得して注入します
   */
  async getHeaders(customHeaders?: HeadersInit): Promise<HeadersInit> {
    const cookieStore = await cookies();
    const storeId = cookieStore.get('x-mw-store-id')?.value;

    const defaultHeaders: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Role': 'store', // ストアフロントからのアクセスは常に store ロール
    };

    if (storeId) {
      defaultHeaders['X-Store-ID'] = storeId;
    }

    return {
      ...defaultHeaders,
      ...customHeaders,
    };
  },
};
