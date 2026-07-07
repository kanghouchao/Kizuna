import type { APIRequestContext } from '@playwright/test';

// store1 は seed 済み（tenant_id=1）。tenant API は Host に加えて
// X-Role / X-Tenant-ID ヘッダでテナントコンテキストを確定する。
export const STORE1_TENANT_ID = '1';
export const TENANT_HEADERS = {
  'X-Role': 'tenant',
  'X-Tenant-ID': STORE1_TENANT_ID,
};
export const ADMIN_EMAIL = 'admin@store1.kizuna.com';
export const ADMIN_PASSWORD = 'pass';

/** テナント管理者でログインし JWT を返す（/tenant/login は CSRF 免除）。 */
export async function loginAsTenantAdmin(request: APIRequestContext): Promise<string> {
  const res = await request.post('/api/tenant/login', {
    headers: TENANT_HEADERS,
    data: { username: ADMIN_EMAIL, password: ADMIN_PASSWORD },
  });
  if (!res.ok()) {
    throw new Error(`tenant login failed: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return body.token as string;
}

/**
 * template_key を変更する（PUT /tenant/config, hasAuthority('TENANT_CONFIG')）。
 * backend は Jackson SNAKE_CASE 設定のため JSON キーは template_key。
 * Bearer トークン付きリクエストは CSRF 免除。
 */
export async function setTemplateKey(
  request: APIRequestContext,
  token: string,
  templateKey: string
): Promise<void> {
  const res = await request.put('/api/tenant/config', {
    headers: { ...TENANT_HEADERS, Authorization: `Bearer ${token}` },
    data: { template_key: templateKey },
  });
  if (!res.ok()) {
    throw new Error(`update template_key failed: ${res.status()} ${await res.text()}`);
  }
}

/** 公開設定から現在の template_key を取得する（GET /tenant/config/public）。 */
export async function getPublicTemplateKey(request: APIRequestContext): Promise<string> {
  const res = await request.get('/api/tenant/config/public', { headers: TENANT_HEADERS });
  if (!res.ok()) {
    throw new Error(`get public config failed: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return body.template_key as string;
}
