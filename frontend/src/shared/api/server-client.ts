import { backendUrl } from '../lib/backend-url';

/**
 * サーバーサイド専用のAPIクライアント
 * Next.js Server Components 内で使用することを想定しています。
 *
 * 機能:
 * - バックエンドAPIのベースURLの自動解決
 * - 共通のエラーハンドリング
 */
export const serverClient = {
  async get<T>(path: string, options?: RequestInit): Promise<T> {
    const url = this.resolveUrl(path);
    const headers = this.getHeaders(options?.headers);

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
    // pathがスラッシュで始まっていない場合の補正
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;
    return `${backendUrl()}${normalizedPath}`;
  },

  getHeaders(customHeaders?: HeadersInit): HeadersInit {
    const defaultHeaders: Record<string, string> = {
      'Content-Type': 'application/json',
    };

    return {
      ...defaultHeaders,
      ...customHeaders,
    };
  },
};
