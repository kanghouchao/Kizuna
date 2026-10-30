import { NextRequest } from 'next/server';
import { backendUrl } from '../backend-url';

export interface StoreData {
  templateKey: string;
  storeId: string;
  storeName: string;
}

/**
 * Host の役割。'unknown' はどの店舗ドメインにも一致しないホストで、公開サイトの各ページは
 * role が 'store' でなければ 404 を返す（空の店舗サイトを描くと「キャストがいない」と区別できない）。
 */
export type HostRole = 'app' | 'store' | 'unknown';

/**
 * アプリドメイン。サーバ実行時の env が唯一の根拠で、role 判定と、proxy がクライアントへ渡す
 * cookie の両方がここを読む（既定値が分かれると cookie だけ別のドメインを運ぶため 1 箇所に置く）。
 */
export const APP_DOMAIN = process.env.APP_DOMAIN || 'kizuna.test';

const ADMIN_DOMAINS = new Set([APP_DOMAIN]);

export async function resolveStore(request: NextRequest): Promise<{
  role: HostRole;
  storeData?: StoreData;
}> {
  const rawHost =
    request.headers.get('x-forwarded-host') ||
    request.headers.get('host') ||
    request.nextUrl.hostname;
  const hostname = rawHost.split(',')[0].trim().split(':')[0].toLowerCase();

  if (ADMIN_DOMAINS.has(hostname)) {
    return { role: 'app' };
  }

  // Cookie の店舗情報を優先使用（重複クエリを回避）
  // ただし、Cookie に保存されたドメインと現在のホスト名が一致し、
  // かつ template cookie が存在する場合のみ信頼する。
  // template cookie が無い場合は黙って 'default' 扱いにせず、
  // バックエンド再解決に落として正しい template_key を取得し直す。
  const existingStoreId = request.cookies.get('x-mw-store-id')?.value;
  const existingDomain = request.cookies.get('x-mw-store-domain')?.value;
  const existingTemplate = request.cookies.get('x-mw-store-template')?.value;

  if (existingStoreId && existingDomain === hostname && existingTemplate) {
    const existingStoreName = request.cookies.get('x-mw-store-name')?.value || '';
    return {
      role: 'store',
      storeData: {
        templateKey: existingTemplate,
        storeId: existingStoreId,
        storeName: existingStoreName,
      },
    };
  }

  // Cookie に店舗情報がない場合、バックエンド API を呼び出す（バックエンドにキャッシュあり）
  const url = `${backendUrl()}/stores/lookup?domain=${encodeURIComponent(hostname)}`;
  const res = await fetch(url);

  // 未登録と判定するのは lookup の 404 だけ。障害（5xx・通信失敗）まで未登録に倒すと、
  // 実在する店舗のサイトが障害中に 404 を返すため、それ以外の失敗は例外として上げる。
  if (res.status === 404) {
    return { role: 'unknown' };
  }
  if (!res.ok) {
    throw new Error(`店舗の解決に失敗しました: ${res.status}`);
  }

  const data = await res.json();
  const storeId = String(data.id);
  const storeName = String(data.name ?? '');

  // lookup 応答（StoreVO）は template_key を返さないため、含まれていれば採用し、
  // 無ければ店舗側の /config/public を追加で取得する（キャッシュ無しで鮮度が高い）。
  const templateKey = data.template_key
    ? String(data.template_key)
    : await fetchTemplateKey(storeId);

  return {
    role: 'store',
    storeData: { templateKey, storeId, storeName },
  };
}

// 店舗側の StoreProfile から template_key を取得する。
// 認証トークン不要・キャッシュ無しで、X-Store-ID ヘッダのみで動作する公開エンドポイント。
// 取得失敗・欠落時は 'default' を返し、決して throw しない。
async function fetchTemplateKey(storeId: string): Promise<string> {
  try {
    const res = await fetch(`${backendUrl()}/config/public`, {
      headers: {
        'X-Store-ID': storeId,
      },
    });
    const config = await res.json().catch(() => null);
    if (config && typeof config === 'object' && config.template_key) {
      return String(config.template_key);
    }
  } catch (error) {
    console.error('🚨 店舗設定の取得に失敗:', error);
  }

  return 'default';
}
