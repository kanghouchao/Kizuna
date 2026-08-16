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
 * 統一ログイン UI から店長（ADMIN_EMAIL・2 店舗授権）で入り、業務画面へ着地する共通手順。
 * 着地先は /store/entry がメニューから解決するため画面を固定せず、着地 URL から storeId を
 * 読み取って返す（seed id をハードコードしない）。授権店舗は id 昇順で先頭が Sample Tenant。
 */
export async function loginViaUiAndEnterStore(page: Page): Promise<string> {
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel('メールアドレス', { exact: true }).fill(ADMIN_EMAIL);
  await page.getByLabel('パスワード', { exact: true }).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  // 選択画面は無い。入口が授権店舗の先頭（Sample Tenant）とメニュー先頭の業務画面を自動解決する。
  await expect(page).toHaveURL(/\/store\/\d+\//, { timeout: 15000 });
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

/** キャスト招待を発行し token を返す（POST /api/store/casts/{id}/invitation, hasAuthority('PERM_CAST_INVITE')）。 */
export async function issueCastInvitation(
  request: APIRequestContext,
  token: string,
  castId: string
): Promise<string> {
  const res = await request.post(`/api/store/casts/${castId}/invitation`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`issue cast invitation failed: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return body.token as string;
}

/**
 * キャスト招待を新規登録で受諾し、CAST 用の平台身分を作成する
 * （POST /api/platform/cast-invitations/acceptance, PermitAll）。
 * トークンはパスではなく本文で送る（パスはアクセスログに残るため）。
 * X-Role/X-Store-ID は不要（/platform 配下は StoreIdInterceptor を通らない）。
 */
export async function acceptCastInvitation(
  request: APIRequestContext,
  invitationToken: string,
  email: string,
  password: string,
  displayName: string
): Promise<void> {
  const res = await request.post('/api/platform/cast-invitations/acceptance', {
    data: { token: invitationToken, email, password, display_name: displayName },
  });
  if (!res.ok()) {
    throw new Error(`accept cast invitation failed: ${res.status()} ${await res.text()}`);
  }
}

/** 出勤希望を承認する（POST /api/store/shift-requests/{id}/approval, hasAuthority('PERM_SHIFT_MANAGE')）。 */
export async function approveShiftRequest(
  request: APIRequestContext,
  token: string,
  id: string
): Promise<void> {
  const res = await request.post(`/api/store/shift-requests/${id}/approval`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`approve shift request failed: ${res.status()} ${await res.text()}`);
  }
}

/** 会員を自助登録し会員コードを返す（POST /api/platform/members, 匿名・CSRF 免除）。 */
export async function registerMember(
  request: APIRequestContext,
  email: string,
  password: string,
  displayName: string
): Promise<string> {
  const res = await request.post('/api/platform/members', {
    data: { email, password, display_name: displayName },
  });
  if (!res.ok()) {
    throw new Error(`register member failed: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return body.member_code as string;
}

/** 顧客を作成し id を返す（POST /api/store/customers, hasAuthority('CUSTOMER_MANAGE')）。 */
export async function createCustomer(
  request: APIRequestContext,
  token: string,
  name: string,
  phoneNumber: string
): Promise<string> {
  const res = await request.post('/api/store/customers', {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { name, phone_number: phoneNumber },
  });
  if (!res.ok()) {
    throw new Error(`create customer failed: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return body.id as string;
}

/** 顧客を削除する（DELETE /api/store/customers/{id}, hasAuthority('CUSTOMER_MANAGE')）。 */
export async function deleteCustomer(
  request: APIRequestContext,
  token: string,
  id: string
): Promise<void> {
  const res = await request.delete(`/api/store/customers/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`delete customer failed: ${res.status()} ${await res.text()}`);
  }
}

/** 会員コードを顧客台帳へ紐づける（POST /api/store/customers/{id}/member-link）。 */
export async function linkMemberToCustomer(
  request: APIRequestContext,
  token: string,
  customerId: string,
  memberCode: string
): Promise<void> {
  const res = await request.post(`/api/store/customers/${customerId}/member-link`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { member_code: memberCode },
  });
  if (!res.ok()) {
    throw new Error(`link member failed: ${res.status()} ${await res.text()}`);
  }
}

/**
 * 予約申請を謝絶する（POST /api/store/orders/{id}/refusal, hasAuthority('ORDER_MANAGE')）。
 * 未確定（CREATED）の申請は削除が拒否されるため、後片付けはこちらで CANCELLED にしてから削除する。
 */
export async function declineOrder(
  request: APIRequestContext,
  token: string,
  id: string
): Promise<void> {
  const res = await request.post(`/api/store/orders/${id}/refusal`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`refuse order failed: ${res.status()} ${await res.text()}`);
  }
}

/**
 * 確定済みの受注を理由付きで取消す（POST /api/store/orders/{id}/cancellation,
 * hasAuthority('ORDER_MANAGE')）。
 *
 * 受注を消す口は無い（ADR 0013 — 誤登録も行を消さず取消として残す）ので、後片付けはこれが終端になる。
 * 行そのものは共有の店舗に残り続けるが、対応が要る群からは外れるので後続のシナリオを妨げない。
 */
export async function cancelOrder(
  request: APIRequestContext,
  token: string,
  id: string,
  reason: string
): Promise<void> {
  const res = await request.post(`/api/store/orders/${id}/cancellation`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { reason },
  });
  if (!res.ok()) {
    throw new Error(`cancel order failed: ${res.status()} ${await res.text()}`);
  }
}
