import { NextRequest } from 'next/server';

export interface TenantData {
  isValid: boolean;
  templateKey: string;
  tenantId: string;
  tenantName: string;
}

const ADMIN_DOMAINS = new Set([process.env.APP_DOMAIN || 'kizuna.test']);

export async function resolveTenant(request: NextRequest): Promise<{
  role: 'central' | 'tenant';
  tenantData?: TenantData;
}> {
  const rawHost =
    request.headers.get('x-forwarded-host') ||
    request.headers.get('host') ||
    request.nextUrl.hostname;
  const hostname = rawHost.split(',')[0].trim().split(':')[0].toLowerCase();

  if (ADMIN_DOMAINS.has(hostname)) {
    return { role: 'central' };
  }

  // Cookie のテナント情報を優先使用（重複クエリを回避）
  // ただし、Cookie に保存されたドメインと現在のホスト名が一致し、
  // かつ template cookie が存在する場合のみ信頼する。
  // template cookie が無い場合は黙って 'default' 扱いにせず、
  // バックエンド再解決に落として正しい template_key を取得し直す。
  const existingTenantId = request.cookies.get('x-mw-tenant-id')?.value;
  const existingDomain = request.cookies.get('x-mw-tenant-domain')?.value;
  const existingTemplate = request.cookies.get('x-mw-tenant-template')?.value;

  if (existingTenantId && existingDomain === hostname && existingTemplate) {
    const existingTenantName = request.cookies.get('x-mw-tenant-name')?.value || '';
    return {
      role: 'tenant',
      tenantData: {
        isValid: true,
        templateKey: existingTemplate,
        tenantId: existingTenantId,
        tenantName: existingTenantName,
      },
    };
  }

  // Cookie にテナント情報がない場合、バックエンド API を呼び出す（バックエンドにキャッシュあり）
  const validationApiUrl =
    process.env.TENANT_VALIDATION_API_URL || 'http://backend:8080/central/tenant';
  const url = validationApiUrl + `?domain=${encodeURIComponent(hostname)}`;

  try {
    const res = await fetch(url);
    const data = await res.json().catch(() => null);

    if (data && typeof data === 'object') {
      const tenantId = String(data.tenant_id ?? data.id ?? '');
      const tenantName = String(data.tenant_name ?? data.name ?? '');
      const isValid = Boolean(tenantId || tenantName || data.domain);

      // 中央応答（TenantVO）は template_key を返さないため、含まれていれば従来どおり採用し、
      // 無ければテナント側の /tenant/config/public を追撃取得する（キャッシュ無しで鮮度が高い）。
      let templateKey = 'default';
      if (data.template_key) {
        templateKey = String(data.template_key);
      } else if (isValid && tenantId) {
        templateKey = await fetchTemplateKey(tenantId);
      }

      return {
        role: 'tenant',
        tenantData: {
          isValid,
          templateKey,
          tenantId,
          tenantName,
        },
      };
    }
  } catch (error) {
    console.error('🚨 テナント解決に失敗:', error);
  }

  return {
    role: 'tenant',
    tenantData: {
      isValid: false,
      templateKey: 'default',
      tenantId: '',
      tenantName: '',
    },
  };
}

// テナント側の StoreProfile から template_key を取得する。
// 認証トークン不要・キャッシュ無しで、X-Role/X-Tenant-ID ヘッダのみで動作する公開エンドポイント。
// 取得失敗・欠落時は 'default' に回落し、決して throw しない。
async function fetchTemplateKey(tenantId: string): Promise<string> {
  const configApiUrl =
    process.env.TENANT_CONFIG_API_URL || 'http://backend:8080/tenant/config/public';

  try {
    const res = await fetch(configApiUrl, {
      headers: {
        'X-Role': 'tenant',
        'X-Tenant-ID': tenantId,
      },
    });
    const config = await res.json().catch(() => null);
    if (config && typeof config === 'object' && config.template_key) {
      return String(config.template_key);
    }
  } catch (error) {
    console.error('🚨 テナント設定の取得に失敗:', error);
  }

  return 'default';
}
