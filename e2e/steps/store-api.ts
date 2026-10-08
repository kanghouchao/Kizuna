import {
  expect,
  type APIRequestContext,
  type APIResponse,
  type Page,
} from "@playwright/test";
import { PLATFORM_URL } from "../base-url";

// store1 は seed 済み（store_id=1）。store API は Host に加えて
// X-Role / X-Store-ID ヘッダで店舗文脈を確定する。
export const STORE1_ID = "1";
export const STORE_HEADERS = {
  "X-Role": "store",
  "X-Store-ID": STORE1_ID,
};
export const ADMIN_EMAIL = "tanaka.hanako@kizuna.test";
export const ADMIN_PASSWORD = "pass";

/**
 * 店長ロール（STORE_MANAGER・store1/store2 双方に授権された v0.5.0 シード）の平台ユーザーで
 * ログインし JWT を返す。返却トークンは STORE_HEADERS（X-Role/X-Store-ID）と併用することで
 * /store/** に店舗文脈を確立できる（STORE_BRIDGE_ROLES ブリッジ）。/platform/login は CSRF 免除。
 */
export async function loginAsStoreAdmin(
  request: APIRequestContext,
): Promise<string> {
  const res = await request.post("/api/platform/login", {
    data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
  });
  if (!res.ok()) {
    throw new Error(
      `platform login failed: ${res.status()} ${await res.text()}`,
    );
  }
  const body = await res.json();
  return body.token as string;
}

/**
 * 統一ログイン UI から店長（ADMIN_EMAIL・2 店舗授権）で入り、業務画面へ着地する共通手順。
 * 着地先は /store/entry がメニューから解決するため画面を固定せず、着地 URL から storeId を
 * 読み取って返す（seed id をハードコードしない）。授権店舗は id 昇順で先頭が Sample。
 */
export async function loginViaUiAndEnterStore(page: Page): Promise<string> {
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel("メールアドレス", { exact: true }).fill(ADMIN_EMAIL);
  await page.getByLabel("パスワード", { exact: true }).fill(ADMIN_PASSWORD);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  // 選択画面は無い。入口が授権店舗の先頭（Sample）とメニュー先頭の業務画面を自動解決する。
  await expect(page).toHaveURL(/\/store\/\d+\//, { timeout: 15000 });
  return new URL(page.url()).pathname.match(/\/store\/(\d+)/)?.[1] ?? "";
}

/**
 * template_key を変更する（PUT /store/config, hasAuthority('PERM_STORE_PROFILE_MANAGE')）。
 * backend は Jackson SNAKE_CASE 設定のため JSON キーは template_key。
 * Bearer トークン付きリクエストは CSRF 免除。
 */
export async function setTemplateKey(
  request: APIRequestContext,
  token: string,
  templateKey: string,
): Promise<void> {
  const res = await request.put("/api/store/config", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { template_key: templateKey },
  });
  if (!res.ok()) {
    throw new Error(
      `update template_key failed: ${res.status()} ${await res.text()}`,
    );
  }
}

/** 公開設定から現在の template_key を取得する（GET /store/config/public）。 */
export async function getPublicTemplateKey(
  request: APIRequestContext,
): Promise<string> {
  const res = await request.get("/api/store/config/public", {
    headers: STORE_HEADERS,
  });
  if (!res.ok()) {
    throw new Error(
      `get public config failed: ${res.status()} ${await res.text()}`,
    );
  }
  const body = await res.json();
  return body.template_key as string;
}

/** 管理画面向けの店舗設定を取得する（GET /store/config, hasAuthority('PERM_STORE_PROFILE_MANAGE')）。 */
export async function getStoreConfig(
  request: APIRequestContext,
  token: string,
): Promise<Record<string, unknown>> {
  const res = await request.get("/api/store/config", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(
      `get store config failed: ${res.status()} ${await res.text()}`,
    );
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
  customTexts: Record<string, string>,
): Promise<void> {
  const res = await request.put("/api/store/config", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { custom_texts: customTexts },
  });
  if (!res.ok()) {
    throw new Error(
      `update custom_texts failed: ${res.status()} ${await res.text()}`,
    );
  }
}

/** キャストを作成し id を返す（POST /api/store/casts, hasAuthority('CAST_MANAGE')）。 */
export async function createUnpublishedCast(
  request: APIRequestContext,
  token: string,
  name: string,
  storeId: string = STORE1_ID,
): Promise<string> {
  const response = await request.post("/api/store/casts", {
    headers: { ...STORE_HEADERS, "X-Store-ID": storeId, Authorization: `Bearer ${token}` },
    data: { name },
  });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).id;
}

export async function setCastPublication(
  request: APIRequestContext,
  token: string,
  id: string,
  publicationStatus: "PUBLISHED" | "UNPUBLISHED",
  storeId: string = STORE1_ID,
): Promise<void> {
  const response = await request.patch(`/api/store/casts/${id}/publication`, {
    headers: { ...STORE_HEADERS, "X-Store-ID": storeId, Authorization: `Bearer ${token}` },
    data: { publication_status: publicationStatus },
  });
  expect(response.status(), await response.text()).toBe(200);
}

export async function createCast(
  request: APIRequestContext,
  token: string,
  name: string,
  storeId: string = STORE1_ID,
): Promise<string> {
  const id = await createUnpublishedCast(request, token, name, storeId);
  await setCastPublication(request, token, id, "PUBLISHED", storeId);
  return id;
}

export async function renameCast(request: APIRequestContext, token: string, id: string, name: string): Promise<void> {
  const response = await request.put(`/api/store/casts/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` }, data: { name },
  });
  expect(response.status(), await response.text()).toBe(200);
}

export async function resumeCast(request: APIRequestContext, token: string, id: string): Promise<void> {
  const response = await request.post(`/api/store/casts/${id}/resumption`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  expect(response.status(), await response.text()).toBe(200);
}

export async function castStatusHistory(request: APIRequestContext, token: string, id: string): Promise<{ content: { id: string; actor_id: number; new_status: string }[] }> {
  const response = await request.get(`/api/store/casts/${id}/status-histories`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}

/** キャストを削除する（DELETE /api/store/casts/{id}, hasAuthority('CAST_MANAGE')）。 */
export async function deleteCast(
  request: APIRequestContext,
  token: string,
  id: string,
  storeId: string = STORE1_ID,
): Promise<void> {
  const res = await request.delete(`/api/store/casts/${id}`, {
    headers: {
      ...STORE_HEADERS,
      "X-Store-ID": storeId,
      Authorization: `Bearer ${token}`,
    },
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
  params: CreateCastFieldDefinitionParams,
): Promise<string> {
  const res = await request.post("/api/store/casts/fields", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { key: params.key, label: params.label, is_public: params.isPublic },
  });
  if (!res.ok()) {
    throw new Error(
      `create cast field definition failed: ${res.status()} ${await res.text()}`,
    );
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
  id: string,
): Promise<void> {
  const res = await request.delete(`/api/store/casts/fields/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(
      `delete cast field definition failed: ${res.status()} ${await res.text()}`,
    );
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

export async function listShifts(
  request: APIRequestContext,
  token: string,
  from: string,
  to: string,
): Promise<{ id: string }[]> {
  const response = await request.get("/api/store/shifts", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    params: { from, to },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}

/** シフトを作成し id を返す（POST /api/store/shifts, hasAuthority('PERM_SHIFT_MANAGE')）。 */
export async function createShift(
  request: APIRequestContext,
  token: string,
  params: CreateShiftParams,
): Promise<string> {
  const res = await request.post("/api/store/shifts", {
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

/** シフトを削除する（DELETE /api/store/shifts/{id}, hasAuthority('PERM_SHIFT_MANAGE')）。 */
export async function deleteShift(
  request: APIRequestContext,
  token: string,
  id: string,
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
  castId: string,
  storeId: string = STORE1_ID,
): Promise<string> {
  const res = await request.post(`/api/store/casts/${castId}/invitation`, {
    headers: {
      ...STORE_HEADERS,
      "X-Store-ID": storeId,
      Authorization: `Bearer ${token}`,
    },
  });
  if (!res.ok()) {
    throw new Error(
      `issue cast invitation failed: ${res.status()} ${await res.text()}`,
    );
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
  displayName: string,
): Promise<void> {
  const res = await request.post("/api/platform/cast-invitations/acceptance", {
    data: {
      token: invitationToken,
      email,
      password,
      display_name: displayName,
    },
  });
  if (!res.ok()) {
    throw new Error(
      `accept cast invitation failed: ${res.status()} ${await res.text()}`,
    );
  }
}

/** 出勤希望を承認する（POST /api/store/shift-requests/{id}/approval, hasAuthority('PERM_SHIFT_MANAGE')）。 */
export async function approveShiftRequest(
  request: APIRequestContext,
  token: string,
  id: string,
  published?: boolean,
): Promise<{ shift_id: string }> {
  const res = await request.post(`/api/store/shift-requests/${id}/approval`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    ...(published === undefined ? {} : { data: { published } }),
  });
  expect(res.status(), await res.text()).toBe(200);
  return res.json();
}

/** 会員を自助登録し会員コードを返す（POST /api/platform/members, 匿名・CSRF 免除）。 */
export async function registerMember(
  request: APIRequestContext,
  email: string,
  password: string,
  displayName: string,
): Promise<string> {
  const res = await request.post("/api/platform/members", {
    data: { email, password, display_name: displayName },
  });
  if (!res.ok()) {
    throw new Error(
      `register member failed: ${res.status()} ${await res.text()}`,
    );
  }
  const body = await res.json();
  return body.member_code as string;
}

/** 顧客を作成し id を返す（POST /api/store/customers, hasAuthority('CUSTOMER_MANAGE')）。 */
export async function createCustomer(
  request: APIRequestContext,
  token: string,
  name: string,
  contacts: { type: "PHONE" | "EMAIL" | "LINE"; value: string }[] = [],
): Promise<string> {
  const res = await request.post("/api/store/customers", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { name, contacts },
  });
  if (!res.ok()) {
    throw new Error(
      `create customer failed: ${res.status()} ${await res.text()}`,
    );
  }
  const body = await res.json();
  return body.id as string;
}

export async function adjustCustomerPoints(
  request: APIRequestContext,
  token: string,
  customerId: string,
  delta: number,
  reason: string,
  storeId: string = STORE1_ID,
): Promise<void> {
  const response = await request.post(
    `/api/store/customers/${customerId}/point-adjustments`,
    {
      headers: {
        ...STORE_HEADERS,
        "X-Store-ID": storeId,
        Authorization: `Bearer ${token}`,
      },
      data: { delta, reason, idempotency_key: crypto.randomUUID() },
    },
  );
  expect(response.status()).toBe(200);
}

export async function updateCustomer(
  request: APIRequestContext,
  token: string,
  id: string,
  data: {
    name?: string;
    address?: string;
    building_name?: string;
    landmark?: string;
    classification?: string;
    has_pet?: boolean;
    usage_areas?: string;
    ng_type?: string;
    ng_content?: string;
  },
): Promise<void> {
  const response = await request.put(`/api/store/customers/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data,
  });
  if (!response.ok())
    throw new Error(
      `顧客の更新に失敗しました: ${response.status()} ${await response.text()}`,
    );
}

/** 顧客へ非優先の連絡先を追加する。 */
export async function addCustomerContact(
  request: APIRequestContext,
  token: string,
  id: string,
  type: "PHONE" | "EMAIL" | "LINE",
  value: string,
): Promise<string> {
  const response = await request.post(`/api/store/customers/${id}/contacts`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { type, value },
  });
  if (!response.ok())
    throw new Error(
      `連絡先の追加に失敗しました: ${response.status()} ${await response.text()}`,
    );
  return (await response.json()).id as string;
}

export async function preferCustomerContact(
  request: APIRequestContext,
  token: string,
  customerId: string,
  type: "PHONE" | "EMAIL" | "LINE",
  contactId: string | null,
): Promise<void> {
  const response = await request.put(
    `/api/store/customers/${customerId}/contact-preferences/${type}`,
    {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
      data: { contact_id: contactId },
    },
  );
  if (!response.ok())
    throw new Error(
      `優先連絡先の更新に失敗しました: ${response.status()} ${await response.text()}`,
    );
}

/** 顧客を削除する（DELETE /api/store/customers/{id}, hasAuthority('CUSTOMER_MANAGE')）。 */
export async function deleteCustomer(
  request: APIRequestContext,
  token: string,
  id: string,
): Promise<void> {
  const res = await request.delete(`/api/store/customers/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(
      `delete customer failed: ${res.status()} ${await res.text()}`,
    );
  }
}

/** 会員コードを顧客台帳へ紐づける（POST /api/store/customers/{id}/member-link）。 */
export async function linkMemberToCustomer(
  request: APIRequestContext,
  token: string,
  customerId: string,
  memberCode: string,
  operation?: { expected_link_id: string; operation_reason: string },
): Promise<void> {
  const res = await request.post(
    `/api/store/customers/${customerId}/member-link`,
    {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
      data: { member_code: memberCode, ...operation },
    },
  );
  if (!res.ok()) {
    throw new Error(`link member failed: ${res.status()} ${await res.text()}`);
  }
}

/**
 * 予約申請を理由付きで謝絶する（POST /api/store/order-applications/{id}/refusal,
 * hasAuthority('ORDER_MANAGE')）。申請行は終端（DECLINED）で残り続けるが、受付箱からは外れる。
 */
export async function declineApplication(
  request: APIRequestContext,
  token: string,
  id: string,
  reason: string,
): Promise<void> {
  const res = await request.post(
    `/api/store/order-applications/${id}/refusal`,
    {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
      data: { reason },
    },
  );
  if (!res.ok()) {
    throw new Error(
      `refuse application failed: ${res.status()} ${await res.text()}`,
    );
  }
}

export async function getOrder(
  request: APIRequestContext,
  token: string,
  storeId: string,
  id: string,
): Promise<{
  id: string;
  customer_id: string | null;
  contact_snapshot: {
    name: string | null;
    phone_number: string | null;
    email: string | null;
    line_id: string | null;
  };
  business_date: string;
  completed_at?: string;
  version: number;
  total_fee: number;
  accrued_remuneration: number;
  fee_lines: { kind: string; amount: number; system_owned: boolean }[];
}> {
  const response = await request.get(`${PLATFORM_URL}/api/store/orders/${id}`, {
    headers: {
      ...STORE_HEADERS,
      "X-Store-ID": storeId,
      Authorization: `Bearer ${token}`,
    },
  });
  expect(response.ok()).toBeTruthy();
  return response.json();
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
  reason: string,
): Promise<void> {
  const res = await request.post(`/api/store/orders/${id}/cancellation`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { reason },
  });
  if (!res.ok()) {
    throw new Error(`cancel order failed: ${res.status()} ${await res.text()}`);
  }
}

export async function loginPlatformUser(
  request: APIRequestContext,
  email: string,
  password: string,
): Promise<string> {
  const response = await request.post("/api/platform/login", {
    data: { email, password },
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()).token;
}

export async function getAuthorizedStores(
  request: APIRequestContext,
  token: string,
): Promise<{ id: number; name: string }[]> {
  const response = await request.get("/api/platform/stores/me", {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok()).toBeTruthy();
  return response.json();
}

export async function acceptExistingCastInvitation(
  request: APIRequestContext,
  token: string,
  invitation: string,
): Promise<void> {
  const response = await request.post(
    "/api/platform/cast-invitations/acceptance/existing",
    {
      headers: { Authorization: `Bearer ${token}` },
      data: { token: invitation },
    },
  );
  expect(response.ok()).toBeTruthy();
}

export async function withdrawCast(
  request: APIRequestContext,
  token: string,
  castId: string,
  storeId: string = STORE1_ID,
): Promise<void> {
  const response = await request.post(`/api/store/casts/${castId}/withdrawal`, {
    headers: {
      ...STORE_HEADERS,
      "X-Store-ID": storeId,
      Authorization: `Bearer ${token}`,
    },
  });
  expect(response.status(), await response.text()).toBe(200);
}

export async function suspendCast(
  request: APIRequestContext,
  token: string,
  castId: string,
  storeId: string = STORE1_ID,
): Promise<void> {
  const response = await request.post(`/api/store/casts/${castId}/suspension`, {
    headers: { ...STORE_HEADERS, "X-Store-ID": storeId, Authorization: `Bearer ${token}` },
  });
  expect(response.status()).toBe(200);
}

export async function correctOrderExtension(
  request: APIRequestContext,
  token: string,
  orderId: string,
  reason: string,
  storeId: string = STORE1_ID,
): Promise<void> {
  const order = await getOrder(request, token, storeId, orderId);
  const headers = { ...STORE_HEADERS, "X-Store-ID": storeId, Authorization: `Bearer ${token}` };
  const data = {
    expected_version: order.version,
    reason,
    fee_lines: [{ kind: "EXTENSION", name: "追加延長", duration_minutes: 30, amount: 4000, remuneration: 2000 }],
  };
  const preview = await request.post(`/api/store/orders/${orderId}/correction-preview`, { headers, data });
  expect(preview.status(), await preview.text()).toBe(200);
  const response = await request.post(`/api/store/orders/${orderId}/corrections`, {
    headers,
    data: { ...data, confirmation_token: (await preview.json()).confirmation_token },
  });
  expect(response.status(), await response.text()).toBe(201);
}

export async function createSpecialService(
  request: APIRequestContext,
  token: string,
  name: string,
): Promise<string> {
  const response = await request.post("/api/store/services", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: {
      kind: "SPECIAL_SERVICE",
      name,
      charge_type: "PAID",
      price: 2000,
      remuneration: 1500,
    },
  });
  expect(response.status()).toBe(201);
  return (await response.json()).id;
}

export async function reviseSpecialService(
  request: APIRequestContext,
  token: string,
  id: string,
  name: string,
): Promise<void> {
  const response = await request.put(`/api/store/services/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: {
      name,
      charge_type: "FREE",
      price: 0,
      remuneration: 0,
      expected_version: 1,
    },
  });
  expect(response.status()).toBe(200);
}

export async function createCourse(
  request: APIRequestContext,
  token: string,
  name: string,
  storeId: string = STORE1_ID,
): Promise<string> {
  const response = await request.post("/api/store/services", {
    headers: {
      ...STORE_HEADERS,
      "X-Store-ID": storeId,
      Authorization: `Bearer ${token}`,
    },
    data: {
      kind: "COURSE",
      name,
      duration_minutes: 60,
      price: 12000,
      remuneration: 7000,
    },
  });
  expect(response.status()).toBe(201);
  return (await response.json()).id;
}

export async function submitConfirmedStoreRequest(
  request: APIRequestContext,
  token: string,
  previewPath: string,
  path: string,
  data: object,
  method: "post" | "put" = "post",
  storeId: string = STORE1_ID,
): Promise<APIResponse> {
  const headers = {
    ...STORE_HEADERS,
    "X-Store-ID": storeId,
    Authorization: `Bearer ${token}`,
  };
  const preview = await request.post(previewPath, { headers, data });
  expect(preview.status(), await preview.text()).toBe(200);
  const response = await request[method](path, {
    headers,
    data: {
      ...data,
      confirmation_token: (await preview.json()).confirmation_token,
    },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response;
}

export async function getOrderReceptionists(
  request: APIRequestContext,
  token: string,
): Promise<{ id: number }[]> {
  const response = await request.get("/api/store/orders/receptionists", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  expect(response.status()).toBe(200);
  return response.json();
}

export async function createAgreedOrder(
  request: APIRequestContext,
  token: string,
  castId: string,
  courseId: string,
  customerName: string,
  options: { storeId?: string; businessDate?: string } = {},
): Promise<string> {
  const data = {
    cast_id: castId,
    course_id: courseId,
    customer_selection: { mode: "NONE" },
    contact_snapshot: { name: customerName },
    business_date:
      options.businessDate ??
      new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Tokyo",
      }).format(new Date()),
  };
  const result = await submitConfirmedStoreRequest(
    request,
    token,
    "/api/store/orders/preview",
    "/api/store/orders",
    data,
    "post",
    options.storeId,
  );
  expect(result.status()).toBe(201);
  return (await result.json()).id;
}

export async function completeAgreedOrder(
  request: APIRequestContext,
  token: string,
  id: string,
  storeId: string = STORE1_ID,
): Promise<void> {
  const order = await getOrder(request, token, storeId, id);
  const data = { expected_version: order.version, fee_lines: [] };
  await submitConfirmedStoreRequest(
    request,
    token,
    `/api/store/orders/${id}/completion-preview`,
    `/api/store/orders/${id}/completion`,
    data,
    "post",
    storeId,
  );
}

export async function invalidateOrder(
  request: APIRequestContext,
  token: string,
  id: string,
  reason: string,
): Promise<void> {
  const headers = { ...STORE_HEADERS, Authorization: `Bearer ${token}` };
  const order = await getOrder(request, token, STORE1_ID, id);
  const result = await request.post(
    `/api/store/orders/${id}/completion-invalidation`,
    { headers, data: { expected_version: order.version, reason } },
  );
  expect(result.status()).toBe(201);
}

/** 現在の会員関連を照会する。 */
export async function currentCustomerMemberLink(
  request: APIRequestContext,
  token: string,
  customerId: string,
) {
  const response = await request.get(
    `/api/store/customers/${customerId}/member-link`,
    {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    },
  );
  if (!response.ok())
    throw new Error(`関連照会に失敗しました: ${response.status()}`);
  return response.json();
}

/** 既存区間を理由付きで解除し、204 を確認する。 */
export async function releaseCustomerMemberLink(
  request: APIRequestContext,
  token: string,
  customerId: string,
  expectedLinkId: string,
  reason: string,
): Promise<void> {
  const response = await request.post(`/api/store/customers/${customerId}/member-link/releases`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { expected_link_id: expectedLinkId, operation_reason: reason },
  });
  expect(response.status(), await response.text()).toBe(204);
}

/** 会員関連の区間履歴の先頭ページを照会する。 */
export async function customerMemberLinkHistory(
  request: APIRequestContext,
  token: string,
  customerId: string,
) {
  const response = await request.get(
    `/api/store/customers/${customerId}/member-link/history`,
    {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    },
  );
  if (!response.ok())
    throw new Error(`関連履歴の照会に失敗しました: ${response.status()}`);
  return response.json();
}

/** 使い捨てスタックで、既定ロールを変更せず検証用の権限集合を作る。 */
export async function createPermissionRole(request: APIRequestContext, token: string, name: string, permissions: string[]): Promise<number> {
  const response = await request.post('/api/platform/roles', { headers: { Authorization: `Bearer ${token}` }, data: { name, permissions } });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).id;
}

/** 検証専用スタッフを作り、既存アカウントの授権に影響を与えない。 */
export async function createPlatformStaffFixture(request: APIRequestContext, token: string, email: string, password: string, roleIds: number[], storeIds?: number[]): Promise<number> {
  const response = await request.post('/api/platform/staff', { headers: { Authorization: `Bearer ${token}` }, data: { email, password, display_name: '検証担当', role_ids: roleIds, store_scope_type: storeIds ? 'SPECIFIC_STORES' : 'ALL_STORES', store_ids: storeIds ?? [] } });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).id;
}

export async function createStoreStaffFixture(request: APIRequestContext, token: string, email: string, password: string, roleIds: number[], storeIds: number[]): Promise<number> {
  const response = await request.post('/api/store/staff-members', { headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` }, data: { email, password, display_name: '検証担当', role_ids: roleIds, store_scope_type: 'SPECIFIC_STORES', store_ids: storeIds } });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).id;
}

/** 現行同意文面で業務連絡を許可したゲスト申請を隔離テスト用に作る。 */
export async function createConsentingGuestApplication(
  request: APIRequestContext,
  email: string,
): Promise<string> {
  const consent = await request.get('/api/store/order-applications/public/contact-consent', { headers: STORE_HEADERS });
  expect(consent.status()).toBe(200);
  const response = await request.post('/api/store/order-applications/public', {
    headers: STORE_HEADERS,
    data: {
      business_date: new Date(Date.now() + 86400000).toISOString().slice(0, 10),
      pax: 1,
      contact_snapshot: { name: '通知検証ゲスト', email },
      contact_consent: { version: (await consent.json()).version, business_allowed: true, marketing_allowed: false },
    },
  });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).id;
}

export async function updateCustomerContact(
  request: APIRequestContext,
  token: string,
  customerId: string,
  contactId: string,
  type: "PHONE" | "EMAIL" | "LINE",
  value: string,
): Promise<void> {
  const response = await request.put(
    `/api/store/customers/${customerId}/contacts/${contactId}`,
    {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
      data: { type, value },
    },
  );
  expect(response.status()).toBe(200);
}

export async function changeCustomerContactPermission(
  request: APIRequestContext,
  token: string,
  customerId: string,
  contactId: string,
  purpose: "BUSINESS" | "MARKETING",
  status: "UNKNOWN" | "ALLOWED" | "DENIED",
  source: string,
  reason: string,
): Promise<void> {
  const response = await request.put(
    `/api/store/customers/${customerId}/contacts/${contactId}/permissions/${purpose}`,
    {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
      data: { status, source, reason },
    },
  );
  expect(response.status()).toBe(200);
}

export async function deleteCustomerContact(
  request: APIRequestContext,
  token: string,
  customerId: string,
  contactId: string,
): Promise<void> {
  const response = await request.delete(
    `/api/store/customers/${customerId}/contacts/${contactId}`,
    { headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` } },
  );
  expect(response.status()).toBe(204);
}

export async function activateEmergencyElevation(
  request: APIRequestContext,
  token: string,
  storeId: string,
  reason: string,
  password: string,
): Promise<{ id: number; token: string }> {
  const response = await request.post("/api/platform/emergency-elevations", {
    headers: { Authorization: `Bearer ${token}` },
    data: { store_id: Number(storeId), reason, password },
  });
  expect(response.status()).toBe(201);
  return response.json();
}

export async function revokeEmergencyElevation(
  request: APIRequestContext,
  token: string,
  id: number,
): Promise<void> {
  const response = await request.post(
    `/api/platform/emergency-elevations/${id}/revocation`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(response.status()).toBe(204);
}

export type AuditEventSummary = {
  id: number;
  actor_id: number;
  actor_type: string;
  store_id: number | null;
  target_id: string;
  target_type: string;
  result: string;
  source_type?: string;
  source_id?: string;
};

export async function listAuditEvents(
  request: APIRequestContext,
  token: string,
  action: string,
): Promise<{ content: AuditEventSummary[]; next_cursor?: string | null }> {
  const response = await request.get("/api/platform/audit-events", {
    headers: { Authorization: `Bearer ${token}` },
    params: { action, size: 100 },
  });
  expect(response.status()).toBe(200);
  return response.json();
}

export async function getAuditEvent(
  request: APIRequestContext,
  token: string,
  id: number,
): Promise<{
  event: AuditEventSummary;
  before_values: Record<string, string>;
  after_values: Record<string, string>;
}> {
  const response = await request.get(`/api/platform/audit-events/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.status()).toBe(200);
  return response.json();
}

export async function mergeCustomers(
  request: APIRequestContext,
  token: string,
  survivingId: string,
  mergedId: string,
  reason: string,
): Promise<void> {
  const headers = { ...STORE_HEADERS, Authorization: `Bearer ${token}` };
  const preview = await request.post(
    `/api/store/customers/${survivingId}/merge-preview`,
    { headers, data: { merged_customer_id: mergedId } },
  );
  expect(preview.status()).toBe(200);
  const selected = await preview.json();
  const response = await request.post(
    `/api/store/customers/${survivingId}/merges`,
    {
      headers,
      data: {
        merged_customer_id: mergedId,
        preview_token: selected.preview_token,
        profile: selected.profile,
        preferred_contacts: selected.preferred_contacts,
        warnings_acknowledged: true,
        operation_reason: reason,
      },
    },
  );
  expect(response.status()).toBe(200);
}

export async function submitCastShiftRequest(
  request: APIRequestContext,
  token: string,
  data: {
    store_id: number;
    work_date: string;
    start_time: string;
    end_time: string;
    note?: string;
  },
): Promise<{ id: string }> {
  const res = await request.post("/api/platform/me/shift-requests", {
    headers: { Authorization: `Bearer ${token}` },
    data,
  });
  expect(res.status(), await res.text()).toBe(201);
  return res.json();
}

export async function submitCastShiftChangeRequest(
  request: APIRequestContext,
  token: string,
  data: {
    shift_id: string;
    work_date: string;
    start_time: string;
    end_time: string;
    note?: string;
  },
): Promise<{ id: string }> {
  const res = await request.post("/api/platform/me/shift-requests/changes", {
    headers: { Authorization: `Bearer ${token}` },
    data,
  });
  expect(res.status(), await res.text()).toBe(201);
  return res.json();
}

export async function declineShiftRequest(
  request: APIRequestContext,
  token: string,
  id: string,
): Promise<void> {
  const res = await request.post(`/api/store/shift-requests/${id}/rejection`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  expect(res.status(), await res.text()).toBe(200);
}

export async function updateShift(
  request: APIRequestContext,
  token: string,
  id: string,
  data: {
    cast_id?: string;
    work_date?: string;
    start_time?: string;
    end_time?: string;
    status?: string;
  },
): Promise<void> {
  const res = await request.put(`/api/store/shifts/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data,
  });
  expect(res.status(), await res.text()).toBe(200);
}

export async function changeShiftPublication(
  request: APIRequestContext,
  token: string,
  id: string,
  published: boolean,
): Promise<void> {
  const res = await request.put(`/api/store/shifts/${id}/publication`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { published },
  });
  expect(res.status(), await res.text()).toBe(200);
}

export async function recordAttendance(
  request: APIRequestContext,
  token: string,
  data: {
    cast_id: string;
    shift_id?: string;
    actual_start_at: string;
    actual_end_at?: string | null;
    waiting_place?: string | null;
  },
): Promise<{ id: string }> {
  const res = await request.post("/api/store/attendances", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data,
  });
  expect(res.status(), await res.text()).toBe(201);
  return res.json();
}

export async function correctAttendance(
  request: APIRequestContext,
  token: string,
  id: string,
  data: {
    business_date: string;
    actual_start_at: string;
    actual_end_at?: string | null;
    waiting_place?: string | null;
  },
): Promise<void> {
  const res = await request.put(`/api/store/attendances/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data,
  });
  expect(res.status(), await res.text()).toBe(200);
}

export async function cancelAttendance(
  request: APIRequestContext,
  token: string,
  id: string,
  reason: string,
): Promise<void> {
  const res = await request.post(`/api/store/attendances/${id}/cancellation`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { reason },
  });
  expect(res.status(), await res.text()).toBe(204);
}

export function getSelfDailyRemunerations(
  request: APIRequestContext,
  options: Parameters<APIRequestContext["get"]>[1],
): Promise<APIResponse> {
  return request.get("/api/platform/me/daily-remunerations", options);
}
