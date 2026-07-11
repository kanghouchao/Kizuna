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

/** 管理画面向けの店舗設定を取得する（GET /tenant/config, hasAuthority('TENANT_CONFIG')）。 */
export async function getStoreConfig(
  request: APIRequestContext,
  token: string
): Promise<Record<string, unknown>> {
  const res = await request.get('/api/tenant/config', {
    headers: { ...TENANT_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`get store config failed: ${res.status()} ${await res.text()}`);
  }
  return res.json();
}

/**
 * custom_texts のみ更新する（PUT /tenant/config, hasAuthority('TENANT_CONFIG')）。
 * MapStruct が NullValuePropertyMappingStrategy.IGNORE のため、他フィールドは送らず不変のまま。
 */
export async function setCustomTexts(
  request: APIRequestContext,
  token: string,
  customTexts: Record<string, string>
): Promise<void> {
  const res = await request.put('/api/tenant/config', {
    headers: { ...TENANT_HEADERS, Authorization: `Bearer ${token}` },
    data: { custom_texts: customTexts },
  });
  if (!res.ok()) {
    throw new Error(`update custom_texts failed: ${res.status()} ${await res.text()}`);
  }
}

/** キャストを作成し id を返す（POST /api/tenant/casts, hasAuthority('CAST_MANAGE')）。 */
export async function createCast(
  request: APIRequestContext,
  token: string,
  name: string
): Promise<string> {
  const res = await request.post('/api/tenant/casts', {
    headers: { ...TENANT_HEADERS, Authorization: `Bearer ${token}` },
    data: { name },
  });
  if (!res.ok()) {
    throw new Error(`create cast failed: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return body.id as string;
}

/** キャストを削除する（DELETE /api/tenant/casts/{id}, hasAuthority('CAST_MANAGE')）。 */
export async function deleteCast(
  request: APIRequestContext,
  token: string,
  id: string
): Promise<void> {
  const res = await request.delete(`/api/tenant/casts/${id}`, {
    headers: { ...TENANT_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`delete cast failed: ${res.status()} ${await res.text()}`);
  }
}

/** シフト作成パラメータ（JSON キーは snake_case で送信する）。 */
export interface CreateShiftParams {
  castId: string;
  workDate: string;
  startTime: string;
  endTime: string;
  status: string;
}

/** シフトを作成し id を返す（POST /api/tenant/shifts, hasAuthority('CAST_MANAGE')）。 */
export async function createShift(
  request: APIRequestContext,
  token: string,
  params: CreateShiftParams
): Promise<string> {
  const res = await request.post('/api/tenant/shifts', {
    headers: { ...TENANT_HEADERS, Authorization: `Bearer ${token}` },
    data: {
      cast_id: params.castId,
      work_date: params.workDate,
      start_time: params.startTime,
      end_time: params.endTime,
      status: params.status,
    },
  });
  if (!res.ok()) {
    throw new Error(`create shift failed: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return body.id as string;
}

/** シフトを削除する（DELETE /api/tenant/shifts/{id}, hasAuthority('CAST_MANAGE')）。 */
export async function deleteShift(
  request: APIRequestContext,
  token: string,
  id: string
): Promise<void> {
  const res = await request.delete(`/api/tenant/shifts/${id}`, {
    headers: { ...TENANT_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`delete shift failed: ${res.status()} ${await res.text()}`);
  }
}
