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
  const existingTenantId = request.cookies.get('x-mw-tenant-id')?.value;
  if (existingTenantId) {
    const existingTenantName = request.cookies.get('x-mw-tenant-name')?.value || '';
    const existingTemplate = request.cookies.get('x-mw-tenant-template')?.value || 'default';
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
      const templateKey = String((data.template_key ?? 'default') || 'default');
      const tenantId = String(data.tenant_id ?? data.id ?? '');
      const tenantName = String(data.tenant_name ?? data.name ?? '');
      const isValid = Boolean(tenantId || tenantName || data.domain);

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
