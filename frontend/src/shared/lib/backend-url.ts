/**
 * サーバ側（Server Component）からバックエンドへ直接届く基底 URL。env はホストまでを持ち、
 * 端点のパスはコードが持つ（パスを改めても各環境の .env を書き換えずに済む）。
 */
export function backendUrl(): string {
  return process.env.BACKEND_URL || 'http://backend:8080';
}
