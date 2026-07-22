import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { PLATFORM_URL } from '../base-url';

// store1 は seed 済み（store_id=1）。store API は Host に加えて
// X-Role / X-Store-ID ヘッダで店舗文脈を確定する。
export const STORE1_ID = '1';
export const STORE_HEADERS = {
  'X-Role': 'store',
  'X-Store-ID': STORE1_ID,
};
export const ADMIN_EMAIL = 'tanaka.hanako@kizuna.test';
export const ADMIN_PASSWORD = 'pass';

/**
 * 店長ロール（STORE_MANAGER・store1/store2 双方に授権された v0.5.0 シード）の平台ユーザーで
 * ログインし JWT を返す。返却トークンは STORE_HEADERS（X-Role/X-Store-ID）と併用することで
 * /store/** に店舗文脈を確立できる（STORE_BRIDGE_ROLES ブリッジ）。/platform/login は CSRF 免除。
 */
export async function loginAsStoreAdmin(request: APIRequestContext): Promise<string> {
  const res = await request.post('/api/platform/login', {
    data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
  });
  if (!res.ok()) {
    throw new Error(`platform login failed: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return body.token as string;
}

/**
 * 統一ログイン UI から店長（ADMIN_EMAIL・2 店舗授権）で入り、店舗選択画面で Sample Tenant を選んで
 * 業務画面へ入る共通手順（#428 でログイン着地が /store/select に一本化された）。
 * ダッシュボード URL から storeId を読み取って返す（seed id をハードコードしない — #413）。
 */
export async function loginViaUiAndEnterStore(page: Page): Promise<string> {
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel('メールアドレス', { exact: true }).fill(ADMIN_EMAIL);
  await page.getByLabel('パスワード', { exact: true }).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  // 2 店舗授権の店長はログイン後 /store/select に着地するため、店舗を選択して業務画面へ入る。
  await page.getByRole('button', { name: 'Sample Tenant', exact: true }).click();
  await expect(page).toHaveURL(/\/store\/\d+\/dashboard\/?$/, { timeout: 15000 });
  return new URL(page.url()).pathname.match(/\/store\/(\d+)/)?.[1] ?? '';
}

/**
 * template_key を変更する（PUT /store/config, hasAuthority('PERM_STORE_PROFILE_MANAGE')）。
 * backend は Jackson SNAKE_CASE 設定のため JSON キーは template_key。
 * Bearer トークン付きリクエストは CSRF 免除。
 */
export async function setTemplateKey(
  request: APIRequestContext,
  token: string,
  templateKey: string
): Promise<void> {
  const res = await request.put('/api/store/config', {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { template_key: templateKey },
  });
  if (!res.ok()) {
    throw new Error(`update template_key failed: ${res.status()} ${await res.text()}`);
  }
}

/** 公開設定から現在の template_key を取得する（GET /store/config/public）。 */
export async function getPublicTemplateKey(request: APIRequestContext): Promise<string> {
  const res = await request.get('/api/store/config/public', { headers: STORE_HEADERS });
  if (!res.ok()) {
    throw new Error(`get public config failed: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return body.template_key as string;
}

/** 管理画面向けの店舗設定を取得する（GET /store/config, hasAuthority('PERM_STORE_PROFILE_MANAGE')）。 */
export async function getStoreConfig(
  request: APIRequestContext,
  token: string
): Promise<Record<string, unknown>> {
  const res = await request.get('/api/store/config', {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`get store config failed: ${res.status()} ${await res.text()}`);
  }
  return res.json();
}

/**
 * custom_texts のみ更新する（PUT /store/config, hasAuthority('PERM_STORE_PROFILE_MANAGE')）。
 * MapStruct が NullValuePropertyMappingStrategy.IGNORE のため、他フィールドは送らず不変のまま。
 */
export async function setCustomTexts(
  request: APIRequestContext,
  token: string,
  customTexts: Record<string, string>
): Promise<void> {
  const res = await request.put('/api/store/config', {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { custom_texts: customTexts },
  });
  if (!res.ok()) {
    throw new Error(`update custom_texts failed: ${res.status()} ${await res.text()}`);
  }
}

/** キャストを作成し id を返す（POST /api/store/casts, hasAuthority('CAST_MANAGE')）。 */
export async function createCast(
  request: APIRequestContext,
  token: string,
  name: string
): Promise<string> {
  const res = await request.post('/api/store/casts', {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { name },
  });
  if (!res.ok()) {
    throw new Error(`create cast failed: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return body.id as string;
}

/** キャストを削除する（DELETE /api/store/casts/{id}, hasAuthority('CAST_MANAGE')）。 */
export async function deleteCast(
  request: APIRequestContext,
  token: string,
  id: string
): Promise<void> {
  const res = await request.delete(`/api/store/casts/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`delete cast failed: ${res.status()} ${await res.text()}`);
  }
}

/** カスタムフィールド定義作成パラメータ（JSON キーは snake_case で送信する）。 */
export interface CreateCastFieldDefinitionParams {
  key: string;
  label: string;
  isPublic: boolean;
}

/**
 * カスタムフィールド定義を作成し id を返す
 * （POST /api/store/casts/fields, hasAuthority('ROLE_STORE_MANAGER')）。
 */
export async function createCastFieldDefinition(
  request: APIRequestContext,
  token: string,
  params: CreateCastFieldDefinitionParams
): Promise<string> {
  const res = await request.post('/api/store/casts/fields', {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { key: params.key, label: params.label, is_public: params.isPublic },
  });
  if (!res.ok()) {
    throw new Error(`create cast field definition failed: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return body.id as string;
}

/**
 * カスタムフィールド定義を削除する
 * （DELETE /api/store/casts/fields/{id}, hasAuthority('ROLE_STORE_MANAGER')）。
 */
export async function deleteCastFieldDefinition(
  request: APIRequestContext,
  token: string,
  id: string
): Promise<void> {
  const res = await request.delete(`/api/store/casts/fields/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`delete cast field definition failed: ${res.status()} ${await res.text()}`);
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

/** シフトを作成し id を返す（POST /api/store/shifts, hasAuthority('CAST_MANAGE')）。 */
export async function createShift(
  request: APIRequestContext,
  token: string,
  params: CreateShiftParams
): Promise<string> {
  const res = await request.post('/api/store/shifts', {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
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

/** シフトを削除する（DELETE /api/store/shifts/{id}, hasAuthority('CAST_MANAGE')）。 */
export async function deleteShift(
  request: APIRequestContext,
  token: string,
  id: string
): Promise<void> {
  const res = await request.delete(`/api/store/shifts/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`delete shift failed: ${res.status()} ${await res.text()}`);
  }
}
