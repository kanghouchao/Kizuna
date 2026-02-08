import { cookies } from 'next/headers';

/**
 * サーバーサイド専用のAPIクライアント
 * Next.js Server Components 内で使用することを想定しています。
 *
 * 機能:
 * - バックエンドAPIのベースURLの自動解決
 * - テナントコンテキスト (X-Tenant-ID) の自動注入
 * - 共通のエラーハンドリング
 */
export const serverClient = {
  /**
   * GETリクエストを実行します
   * @param path APIパス (例: '/tenant/casts/public')
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
      throw new Error(`API Error: ${response.status} ${response.statusText} (${path})`);
    }

    return response.json();
  },

  /**
   * APIの完全なURLを解決します
   */
  resolveUrl(path: string): string {
    const backendUrl =
      process.env.TENANT_VALIDATION_API_URL?.replace('/central/tenant', '') ||
      'http://backend:8080';

    // pathがスラッシュで始まっていない場合の補正
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;
    return `${backendUrl}${normalizedPath}`;
  },

  /**
   * 必要なヘッダーを構築します
   * CookieからテナントIDを自動的に取得して注入します
   */
  async getHeaders(customHeaders?: HeadersInit): Promise<HeadersInit> {
    const cookieStore = await cookies();
    const tenantId = cookieStore.get('x-mw-tenant-id')?.value;

    const defaultHeaders: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Role': 'tenant', // ストアフロントからのアクセスは常に tenant ロール
    };

    if (tenantId) {
      defaultHeaders['X-Tenant-ID'] = tenantId;
    }

    return {
      ...defaultHeaders,
      ...customHeaders,
    };
  },
};
