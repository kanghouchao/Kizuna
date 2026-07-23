import { NextRequest } from 'next/server';

export interface StoreData {
  isValid: boolean;
  templateKey: string;
  storeId: string;
  storeName: string;
}

const ADMIN_DOMAINS = new Set([process.env.APP_DOMAIN || 'kizuna.test']);

export async function resolveStore(request: NextRequest): Promise<{
  role: 'platform' | 'store';
  storeData?: StoreData;
}> {
  const rawHost =
    request.headers.get('x-forwarded-host') ||
    request.headers.get('host') ||
    request.nextUrl.hostname;
  const hostname = rawHost.split(',')[0].trim().split(':')[0].toLowerCase();

  if (ADMIN_DOMAINS.has(hostname)) {
    return { role: 'platform' };
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
        isValid: true,
        templateKey: existingTemplate,
        storeId: existingStoreId,
        storeName: existingStoreName,
      },
    };
  }

  // Cookie に店舗情報がない場合、バックエンド API を呼び出す（バックエンドにキャッシュあり）
  const validationApiUrl =
    process.env.STORE_LOOKUP_API_URL || 'http://backend:8080/platform/stores/lookup';
  const url = validationApiUrl + `?domain=${encodeURIComponent(hostname)}`;

  try {
    const res = await fetch(url);
    const data = await res.json().catch(() => null);

    if (data && typeof data === 'object') {
      const storeId = String(data.id ?? '');
      const storeName = String(data.name ?? '');
      const isValid = Boolean(storeId || storeName || data.domain);

      // プラットフォーム応答（StoreVO）は template_key を返さないため、含まれていれば採用し、
      // 無ければ店舗側の /store/config/public を追撃取得する（キャッシュ無しで鮮度が高い）。
      let templateKey = 'default';
      if (data.template_key) {
        templateKey = String(data.template_key);
      } else if (isValid && storeId) {
        templateKey = await fetchTemplateKey(storeId);
      }

      return {
        role: 'store',
        storeData: {
          isValid,
          templateKey,
          storeId,
          storeName,
        },
      };
    }
  } catch (error) {
    console.error('🚨 店舗解決に失敗:', error);
  }

  return {
    role: 'store',
    storeData: {
      isValid: false,
      templateKey: 'default',
      storeId: '',
      storeName: '',
    },
  };
}

// 店舗側の StoreProfile から template_key を取得する。
// 認証トークン不要・キャッシュ無しで、X-Role/X-Store-ID ヘッダのみで動作する公開エンドポイント。
// 取得失敗・欠落時は 'default' に回落し、決して throw しない。
async function fetchTemplateKey(storeId: string): Promise<string> {
  const configApiUrl =
    process.env.STORE_CONFIG_API_URL || 'http://backend:8080/store/config/public';

  try {
    const res = await fetch(configApiUrl, {
      headers: {
        'X-Role': 'store',
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
