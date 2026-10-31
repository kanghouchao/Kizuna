import {
  expect,
  type APIRequestContext,
  type APIResponse,
  type Locator,
  type Page,
} from "@playwright/test";
import { APP_URL } from "../base-url";

// store1 は initial-setup プロジェクトが最初に登録する店舗（store_id=1）。店舗を要する API は
// 店舗を X-Store-ID ヘッダだけから受け取る。
export const STORE1_ID = "1";
export const STORE_HEADERS = { "X-Store-ID": STORE1_ID };
export const ADMIN_EMAIL = "tanaka.hanako@kizuna.test";
export const ADMIN_PASSWORD = "pass1234";

/**
 * 店長（store1/store2 双方に授権。initial-setup プロジェクトが作成する）でログインし
 * JWT を返す。返却トークンは STORE_HEADERS（X-Store-ID）と併用することで
 * 店舗を要する API に店舗文脈を確立できる。/auth/login は CSRF 免除。
 */
export async function loginAsStoreAdmin(
  request: APIRequestContext,
): Promise<string> {
  const res = await request.post("/api/auth/login", {
    data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
  });
  if (!res.ok()) {
    throw new Error(
      `login failed: ${res.status()} ${await res.text()}`,
    );
  }
  const body = await res.json();
  return body.token as string;
}

/**
 * 統一ログイン UI から店長（ADMIN_EMAIL・2 店舗授権）で入り、ダッシュボードへの着地を待つ共通手順。
 * 店舗業務 URL は店舗 id を持たず、現在店舗は授権店舗の先頭（Sample = STORE1_ID）が自動選択されるため、
 * UI と API（STORE_HEADERS）の店舗文脈を揃える id として STORE1_ID を返す。
 */
export async function loginViaUiAndEnterStore(page: Page): Promise<string> {
  await page.goto(`${APP_URL}/auth/login`);
  await page.getByLabel("メールアドレス", { exact: true }).fill(ADMIN_EMAIL);
  await page.getByLabel("パスワード", { exact: true }).fill(ADMIN_PASSWORD);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard\/?$/, { timeout: 15000 });
  return STORE1_ID;
}

const sidebar = (page: Page): Locator => page.locator("aside");

// レールのボタン（グループと各店舗）。文脈が 1 つしか無い利用者にはレールが無く、0 件になる。
const railButtons = (page: Page): Locator =>
  sidebar(page).locator("button[aria-pressed]");

/**
 * メニューとレールが出揃うまで待つ。ダッシュボードは文脈に関係なく最上段に出るので、
 * パネルが描かれた（授権店舗の解決後）ことの目印にできる。
 */
async function waitSidebarReady(page: Page): Promise<void> {
  await expect(
    sidebar(page).locator('a[href="/admin/dashboard"]'),
  ).toBeVisible({ timeout: 15000 });
}

/** レールで文脈（"グループ" または店名）を選び、選択表示になるまで待つ。 */
export async function selectRailContext(
  page: Page,
  label: string,
): Promise<void> {
  await waitSidebarReady(page);
  const button = sidebar(page).getByRole("button", {
    name: label,
    exact: true,
  });
  await button.click();
  await expect(button).toHaveAttribute("aria-pressed", "true");
}

/** レールで現在店舗を変える。同じ画面が再マウントされるので、選択表示で完了を待つ。 */
export const switchStoreOnRail = selectRailContext;

/**
 * 指定のリンクを含む文脈を選んで、そのリンクの Locator を返す。
 * 表示中のパネル、グループ、各店舗の順に探す（店舗の切替は現在店舗を変えるので最後に回す）。
 */
export async function revealSidebarLink(
  page: Page,
  link: (aside: Locator) => Locator,
): Promise<Locator> {
  await waitSidebarReady(page);
  const aside = sidebar(page);
  if ((await link(aside).count()) > 0) return link(aside);
  const buttons = railButtons(page);
  const count = await buttons.count();
  for (let i = 0; i < count; i++) {
    await buttons.nth(i).click();
    if ((await link(aside).count()) > 0) return link(aside);
  }
  return link(aside);
}

/** どの文脈のパネルにも、そのリンクが無いことを断言する。 */
export async function expectSidebarLinkAbsentEverywhere(
  page: Page,
  link: (aside: Locator) => Locator,
): Promise<void> {
  await waitSidebarReady(page);
  const aside = sidebar(page);
  await expect(link(aside)).toHaveCount(0);
  const buttons = railButtons(page);
  const count = await buttons.count();
  for (let i = 0; i < count; i++) {
    await buttons.nth(i).click();
    await expect(link(aside)).toHaveCount(0);
  }
}

/**
 * template_key を変更する（PUT /config, hasAuthority('PERM_STORE_PROFILE_MANAGE')）。
 * backend は Jackson SNAKE_CASE 設定のため JSON キーは template_key。
 * Bearer トークン付きリクエストは CSRF 免除。
 */
export async function setTemplateKey(
  request: APIRequestContext,
  token: string,
  templateKey: string,
): Promise<void> {
  const res = await request.put("/api/config", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { template_key: templateKey },
  });
  if (!res.ok()) {
    throw new Error(
      `update template_key failed: ${res.status()} ${await res.text()}`,
    );
  }
}

/** 公開設定から現在の template_key を取得する（GET /config/public）。 */
export async function getPublicTemplateKey(
  request: APIRequestContext,
): Promise<string> {
  const res = await request.get("/api/config/public", {
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

/** 管理画面向けの店舗設定を取得する（GET /config, hasAuthority('PERM_STORE_PROFILE_MANAGE')）。 */
export async function getStoreConfig(
  request: APIRequestContext,
  token: string,
): Promise<Record<string, unknown>> {
  const res = await request.get("/api/config", {
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
 * custom_texts のみ更新する（PUT /config, hasAuthority('PERM_STORE_PROFILE_MANAGE')）。
 * MapStruct が NullValuePropertyMappingStrategy.IGNORE のため、他フィールドは送らず不変のまま。
 */
export async function setCustomTexts(
  request: APIRequestContext,
  token: string,
  customTexts: Record<string, string>,
): Promise<void> {
  const res = await request.put("/api/config", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { custom_texts: customTexts },
  });
  if (!res.ok()) {
    throw new Error(
      `update custom_texts failed: ${res.status()} ${await res.text()}`,
    );
  }
}

/** 日本時間の当日（YYYY-MM-DD）。キャスト作成画面の入店日の既定値と同じ暦日。 */
export function todayInTokyo(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Tokyo" }).format(
    new Date(),
  );
}

/** キャストを作成し id を返す（POST /api/casts, hasAuthority('CAST_MANAGE')）。 */
export async function createUnpublishedCast(
  request: APIRequestContext,
  token: string,
  name: string,
  storeId: string = STORE1_ID,
): Promise<string> {
  const response = await request.post("/api/casts", {
    headers: { ...STORE_HEADERS, "X-Store-ID": storeId, Authorization: `Bearer ${token}` },
    data: { name, started_on: todayInTokyo() },
  });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).id;
}

/**
 * 応募受付 → 面接記録 → 面接済 → 採用確定 → 在籍登録を API で行い、在籍 ID を返す
 * （RECRUITMENT_VIEW / MANAGE / DECIDE と CAST_MANAGE を要する）。
 */
export async function createEnrolledHire(
  request: APIRequestContext,
  token: string,
  hire: { sourceType: "DIRECT" | "MEDIA" | "REFERRAL" | "SCOUT"; sourceMedia: string | null; startedOn: string },
  storeId: string = STORE1_ID,
): Promise<string> {
  const headers = { ...STORE_HEADERS, "X-Store-ID": storeId, Authorization: `Bearer ${token}` };
  const introduced = hire.sourceType === "REFERRAL" || hire.sourceType === "SCOUT";
  let response = await request.post("/api/applicants", {
    headers,
    data: {
      name: "採用効果の検証応募者",
      channel: "PHONE",
      source_type: hire.sourceType,
      source_media: hire.sourceMedia,
      referrer: introduced ? "検証紹介者" : null,
    },
  });
  expect(response.status(), await response.text()).toBe(201);
  let detail = await response.json();
  const path = `/api/applicants/${detail.id}`;
  response = await request.put(`${path}/interview`, {
    headers,
    data: { version: detail.version, interview_at: "2026-10-01T10:30:00+09:00", interviewer: "面接担当者", checklist: {} },
  });
  expect(response.status(), await response.text()).toBe(200);
  detail = await response.json();
  for (const status of ["SCREENING", "INTERVIEWED"]) {
    response = await request.post(`${path}/transitions`, {
      headers,
      data: { version: detail.version, status, reason: "選考を進める" },
    });
    expect(response.status(), await response.text()).toBe(200);
    detail = await response.json();
  }
  response = await request.post(`${path}/decision`, {
    headers,
    data: { version: detail.version, status: "HIRED", reason: "採用効果の検証" },
  });
  expect(response.status(), await response.text()).toBe(200);
  detail = await response.json();
  response = await request.post(`${path}/enrollment`, {
    headers,
    data: { version: detail.version, name: "採用効果の検証在籍", started_on: hire.startedOn },
  });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).enrollment_id;
}

export async function setCastPublication(
  request: APIRequestContext,
  token: string,
  id: string,
  publicationStatus: "PUBLISHED" | "UNPUBLISHED",
  storeId: string = STORE1_ID,
): Promise<void> {
  const response = await request.patch(`/api/casts/${id}/publication`, {
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
  const response = await request.put(`/api/casts/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` }, data: { name },
  });
  expect(response.status(), await response.text()).toBe(200);
}

export async function resumeCast(request: APIRequestContext, token: string, id: string): Promise<void> {
  const response = await request.post(`/api/casts/${id}/resumption`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  expect(response.status(), await response.text()).toBe(200);
}

export async function castStatusHistory(request: APIRequestContext, token: string, id: string): Promise<{ content: { id: string; actor_id: number; new_status: string }[] }> {
  const response = await request.get(`/api/casts/${id}/status-histories`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}

/** キャストを削除する（DELETE /api/casts/{id}, hasAuthority('CAST_MANAGE')）。 */
export async function deleteCast(
  request: APIRequestContext,
  token: string,
  id: string,
  storeId: string = STORE1_ID,
): Promise<void> {
  const res = await request.delete(`/api/casts/${id}`, {
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

/** 定義の変更は CAST_FIELD_DEF_MANAGE を要する。 */
export async function createCastFieldDefinition(
  request: APIRequestContext,
  token: string,
  params: CreateCastFieldDefinitionParams,
): Promise<string> {
  const res = await request.post("/api/casts/fields", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { key: params.key, label: params.label, is_public: params.isPublic },
  });
  expect(res.status(), await res.text()).toBe(201);
  const body = await res.json();
  return body.id as string;
}

export async function deleteCastFieldDefinition(
  request: APIRequestContext,
  token: string,
  id: string,
): Promise<void> {
  const res = await request.delete(`/api/casts/fields/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  expect(res.status(), await res.text()).toBe(204);
}

export type CastFieldDefinition = {
  id: string;
  key: string;
  label: string;
  is_public: boolean;
  display_order: number;
};

export async function listCastFieldDefinitions(
  request: APIRequestContext,
  token: string,
  storeId: string = STORE1_ID,
): Promise<CastFieldDefinition[]> {
  const response = await request.get("/api/casts/fields", {
    headers: {
      ...STORE_HEADERS,
      "X-Store-ID": storeId,
      Authorization: `Bearer ${token}`,
    },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}

export async function updateCastFieldDefinition(request: APIRequestContext, token: string, id: string, data: { label?: string; display_order?: number; is_public?: boolean }): Promise<CastFieldDefinition> {
  const response = await request.put(`/api/casts/fields/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` }, data,
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}

export async function setCastCustomFields(request: APIRequestContext, token: string, id: string, values: Record<string, string>): Promise<void> {
  const response = await request.put(`/api/casts/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` }, data: { custom_fields: values },
  });
  expect(response.status(), await response.text()).toBe(200);
}

export async function getCastCustomFields(request: APIRequestContext, token: string, id: string): Promise<Record<string, string>> {
  const response = await request.get(`/api/casts/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()).custom_fields;
}

export async function castFieldSnapshots(request: APIRequestContext, token: string, id: string): Promise<{content: {id: string; actor_id: number; custom_fields: Record<string, string>}[]}> {
  const response = await request.get(`/api/casts/${id}/snapshots`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
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
  const response = await request.get("/api/shifts", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    params: { from, to },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}

/** シフトを作成し id を返す（POST /api/shifts, hasAuthority('PERM_SHIFT_MANAGE')）。 */
export async function createShift(
  request: APIRequestContext,
  token: string,
  params: CreateShiftParams,
): Promise<string> {
  const res = await request.post("/api/shifts", {
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

/** シフトを削除する（DELETE /api/shifts/{id}, hasAuthority('PERM_SHIFT_MANAGE')）。 */
export async function deleteShift(
  request: APIRequestContext,
  token: string,
  id: string,
): Promise<void> {
  const res = await request.delete(`/api/shifts/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`delete shift failed: ${res.status()} ${await res.text()}`);
  }
}

/** キャスト招待を発行し token を返す（POST /api/casts/{id}/invitation, hasAuthority('PERM_CAST_INVITE')）。 */
export async function issueCastInvitation(
  request: APIRequestContext,
  token: string,
  castId: string,
  storeId: string = STORE1_ID,
): Promise<string> {
  const res = await request.post(`/api/casts/${castId}/invitation`, {
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
 * キャスト招待を新規登録で受諾し、CAST 用のグループ共通アカウントを作成する
 * （POST /api/cast-invitations/acceptance, PermitAll）。
 * トークンはパスではなく本文で送る（パスはアクセスログに残るため）。
 * X-Store-ID は付けない（店舗を X-Store-ID から受け取るのは @CurrentStore を宣言する handler だけで、この端点は宣言しない）。
 */
export async function acceptCastInvitation(
  request: APIRequestContext,
  invitationToken: string,
  email: string,
  password: string,
  displayName: string,
): Promise<void> {
  const res = await request.post("/api/cast-invitations/acceptance", {
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

/** 出勤希望を承認する（POST /api/shift-requests/{id}/approval, hasAuthority('PERM_SHIFT_MANAGE')）。 */
export async function approveShiftRequest(
  request: APIRequestContext,
  token: string,
  id: string,
  published?: boolean,
): Promise<{ shift_id: string }> {
  const res = await request.post(`/api/shift-requests/${id}/approval`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    ...(published === undefined ? {} : { data: { published } }),
  });
  expect(res.status(), await res.text()).toBe(200);
  return res.json();
}

/** 会員を自助登録し会員コードを返す（POST /api/members, 匿名・CSRF 免除）。 */
export async function registerMember(
  request: APIRequestContext,
  email: string,
  password: string,
  displayName: string,
): Promise<string> {
  const res = await request.post("/api/members", {
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

/** 電子メールで会員を引き、会員 ID を返す（GET /api/members?search=, MEMBER_VIEW）。 */
export async function memberIdOf(
  request: APIRequestContext,
  token: string,
  email: string,
) {
  const response = await request.get("/api/members", {
    headers: { Authorization: `Bearer ${token}` },
    params: { search: email },
  });
  expect(response.status()).toBe(200);
  const rows = (await response.json()).content as { id: number }[];
  expect(rows).toHaveLength(1);
  return String(rows[0].id);
}

/** 顧客を作成し id を返す（POST /api/customers, hasAuthority('CUSTOMER_MANAGE')）。 */
export async function createCustomer(
  request: APIRequestContext,
  token: string,
  name: string,
  contacts: { type: "PHONE" | "EMAIL" | "LINE"; value: string }[] = [],
): Promise<string> {
  const res = await request.post("/api/customers", {
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
    `/api/customers/${customerId}/point-adjustments`,
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
  const response = await request.put(`/api/customers/${id}`, {
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
  const response = await request.post(`/api/customers/${id}/contacts`, {
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
    `/api/customers/${customerId}/contact-preferences/${type}`,
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

/** 顧客を削除する（DELETE /api/customers/{id}, hasAuthority('CUSTOMER_MANAGE')）。 */
export async function deleteCustomer(
  request: APIRequestContext,
  token: string,
  id: string,
): Promise<void> {
  const res = await request.delete(`/api/customers/${id}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(
      `delete customer failed: ${res.status()} ${await res.text()}`,
    );
  }
}

/** 会員コードを顧客台帳へ紐づける（POST /api/customers/{id}/member-link）。 */
export async function linkMemberToCustomer(
  request: APIRequestContext,
  token: string,
  customerId: string,
  memberCode: string,
  operation?: { expected_link_id: string; operation_reason: string },
): Promise<void> {
  const res = await request.post(
    `/api/customers/${customerId}/member-link`,
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
 * 予約申請を理由付きで謝絶する（POST /api/order-applications/{id}/refusal,
 * hasAuthority('ORDER_MANAGE')）。申請行は終端（DECLINED）で残り続けるが、受付箱からは外れる。
 */
export async function declineApplication(
  request: APIRequestContext,
  token: string,
  id: string,
  reason: string,
): Promise<void> {
  const res = await request.post(
    `/api/order-applications/${id}/refusal`,
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
  const response = await request.get(`${APP_URL}/api/orders/${id}`, {
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
 * 確定済みの受注を理由付きで取消す（POST /api/orders/{id}/cancellation,
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
  storeId: string = STORE1_ID,
): Promise<void> {
  const res = await request.post(`/api/orders/${id}/cancellation`, {
    headers: { ...STORE_HEADERS, "X-Store-ID": storeId, Authorization: `Bearer ${token}` },
    data: { reason },
  });
  if (!res.ok()) {
    throw new Error(`cancel order failed: ${res.status()} ${await res.text()}`);
  }
}

export async function loginUser(
  request: APIRequestContext,
  email: string,
  password: string,
): Promise<string> {
  const response = await request.post("/api/auth/login", {
    data: { email, password },
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()).token;
}

export async function getAuthorizedStores(
  request: APIRequestContext,
  token: string,
): Promise<{ id: number; name: string }[]> {
  const response = await request.get("/api/stores/me", {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok()).toBeTruthy();
  return response.json();
}

/** 既存の CAST アカウントの資格情報を添えて招待を受諾する（匿名の一回の要求で、トークンは発行されない）。 */
export async function acceptExistingCastInvitation(
  request: APIRequestContext,
  invitation: string,
  email: string,
  password: string,
): Promise<void> {
  const response = await request.post(
    "/api/cast-invitations/acceptance/existing",
    { data: { token: invitation, email, password } },
  );
  expect(response.ok()).toBeTruthy();
}

export async function withdrawCast(
  request: APIRequestContext,
  token: string,
  castId: string,
  storeId: string = STORE1_ID,
): Promise<void> {
  const response = await request.post(`/api/casts/${castId}/withdrawal`, {
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
  const response = await request.post(`/api/casts/${castId}/suspension`, {
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
  const preview = await request.post(`/api/orders/${orderId}/correction-preview`, { headers, data });
  expect(preview.status(), await preview.text()).toBe(200);
  const response = await request.post(`/api/orders/${orderId}/corrections`, {
    headers,
    data: { ...data, confirmation_token: (await preview.json()).confirmation_token },
  });
  expect(response.status(), await response.text()).toBe(201);
}

export async function createSpecialService(
  request: APIRequestContext,
  token: string,
  name: string,
  storeId: string = STORE1_ID,
): Promise<string> {
  const response = await request.post("/api/services", {
    headers: {
      ...STORE_HEADERS,
      "X-Store-ID": storeId,
      Authorization: `Bearer ${token}`,
    },
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
  const response = await request.put(`/api/services/${id}`, {
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
  const response = await request.post("/api/services", {
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
  const response = await request.get("/api/orders/receptionists", {
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
    "/api/orders/preview",
    "/api/orders",
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
    `/api/orders/${id}/completion-preview`,
    `/api/orders/${id}/completion`,
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
  storeId: string = STORE1_ID,
): Promise<void> {
  const headers = { ...STORE_HEADERS, "X-Store-ID": storeId, Authorization: `Bearer ${token}` };
  const order = await getOrder(request, token, storeId, id);
  const result = await request.post(
    `/api/orders/${id}/completion-invalidation`,
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
    `/api/customers/${customerId}/member-link`,
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
  const response = await request.post(`/api/customers/${customerId}/member-link/releases`, {
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
    `/api/customers/${customerId}/member-link/history`,
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
  const response = await request.post('/api/roles', { headers: { Authorization: `Bearer ${token}` }, data: { name, permissions } });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).id;
}

/** 検証専用スタッフを作り、既存アカウントの授権に影響を与えない。 */
export async function createStaffFixture(request: APIRequestContext, token: string, email: string, password: string, roleIds: number[], storeIds?: number[]): Promise<number> {
  const response = await request.post('/api/staff', { headers: { Authorization: `Bearer ${token}` }, data: { email, password, display_name: '検証担当', role_ids: roleIds, store_scope_type: storeIds ? 'SPECIFIC_STORES' : 'ALL_STORES', store_ids: storeIds ?? [] } });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).id;
}

export async function createStoreStaffFixture(request: APIRequestContext, token: string, email: string, password: string, roleIds: number[], storeIds: number[], storeId: string = STORE1_ID): Promise<number> {
  const response = await request.post('/api/staff', { headers: { Authorization: `Bearer ${token}` }, data: { email, password, display_name: '検証担当', role_ids: roleIds, store_scope_type: 'SPECIFIC_STORES', store_ids: storeIds } });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).id;
}

/** 表示名を指定し、個別店舗に授権した従業員を作る（POST /staff, PERM_STAFF_MANAGE）。 */
export async function createStaffAccount(request: APIRequestContext, token: string, staff: { email: string; password: string; displayName: string; roleIds: number[]; storeIds: number[] }): Promise<number> {
  const response = await request.post('/api/staff', { headers: { Authorization: `Bearer ${token}` }, data: { email: staff.email, password: staff.password, display_name: staff.displayName, role_ids: staff.roleIds, store_scope_type: 'SPECIFIC_STORES', store_ids: staff.storeIds } });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).id;
}

/** 店舗を登録する（POST /stores, PERM_STORE_MANAGE）。 */
export async function createStore(request: APIRequestContext, token: string, store: { name: string; short_name: string; domain: string }): Promise<void> {
  const response = await request.post('/api/stores', { headers: { Authorization: `Bearer ${token}` }, data: store });
  expect(response.status(), await response.text()).toBe(201);
}

/** ロール名から id を引く（GET /roles, PERM_ROLE_MANAGE）。 */
export async function findRoleIdByName(request: APIRequestContext, token: string, name: string): Promise<number> {
  const response = await request.get('/api/roles', { headers: { Authorization: `Bearer ${token}` } });
  expect(response.status()).toBe(200);
  const role = ((await response.json()) as { id: number; name: string }[]).find((row) => row.name === name);
  expect(role, `ロール ${name} がありません`).toBeDefined();
  return role!.id;
}

/** 店舗ドメインから id を引く（GET /stores, PERM_STORE_MANAGE）。検索は部分一致のため、ドメインの完全一致で選び直す。 */
export async function findStoreIdByDomain(request: APIRequestContext, token: string, domain: string): Promise<string> {
  const response = await request.get('/api/stores', { headers: { Authorization: `Bearer ${token}` }, params: { search: domain } });
  expect(response.status()).toBe(200);
  const store = ((await response.json()).content as { id: string; domain: string }[]).find((row) => row.domain === domain);
  expect(store, `店舗 ${domain} がありません`).toBeDefined();
  return store!.id;
}

/** 現行同意文面で業務連絡を許可したゲスト申請を隔離テスト用に作る。 */
export async function createConsentingGuestApplication(
  request: APIRequestContext,
  email: string,
): Promise<string> {
  const consent = await request.get('/api/order-applications/public/contact-consent', { headers: STORE_HEADERS });
  expect(consent.status()).toBe(200);
  const response = await request.post('/api/order-applications/public', {
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
    `/api/customers/${customerId}/contacts/${contactId}`,
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
    `/api/customers/${customerId}/contacts/${contactId}/permissions/${purpose}`,
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
    `/api/customers/${customerId}/contacts/${contactId}`,
    { headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` } },
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
  const response = await request.get("/api/audit-events", {
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
  const response = await request.get(`/api/audit-events/${id}`, {
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
    `/api/customers/${survivingId}/merge-preview`,
    { headers, data: { merged_customer_id: mergedId } },
  );
  expect(preview.status()).toBe(200);
  const selected = await preview.json();
  const response = await request.post(
    `/api/customers/${survivingId}/merges`,
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
  const res = await request.post("/api/me/shift-requests", {
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
  const res = await request.post("/api/me/shift-requests/changes", {
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
  const res = await request.post(`/api/shift-requests/${id}/rejection`, {
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
  const res = await request.put(`/api/shifts/${id}`, {
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
  const res = await request.put(`/api/shifts/${id}/publication`, {
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
  const res = await request.post("/api/attendances", {
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
  const res = await request.put(`/api/attendances/${id}`, {
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
  const res = await request.post(`/api/attendances/${id}/cancellation`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { reason },
  });
  expect(res.status(), await res.text()).toBe(204);
}

export function getSelfDailyRemunerations(
  request: APIRequestContext,
  options: Parameters<APIRequestContext["get"]>[1],
): Promise<APIResponse> {
  return request.get("/api/me/daily-remunerations", options);
}

export async function deleteService(
  request: APIRequestContext,
  token: string,
  id: string,
  expectedVersion: number,
  storeId: string = STORE1_ID,
): Promise<void> {
  const response = await request.delete(`/api/services/${id}`, {
    headers: { ...STORE_HEADERS, "X-Store-ID": storeId, Authorization: `Bearer ${token}` },
    params: { expected_version: expectedVersion },
  });
  expect(response.status(), await response.text()).toBe(204);
}

export async function createBonusAward(
  request: APIRequestContext,
  token: string,
  personId: number,
  awardDate: string,
  amount: number,
  reason: string,
  storeId: string = STORE1_ID,
  requestId: string = crypto.randomUUID(),
): Promise<{ id: string; version: number }> {
  const response = await request.post('/api/bonus-awards', {
    headers: { 'X-Store-ID': storeId, Authorization: `Bearer ${token}` },
    data: { person_id: personId, award_date: awardDate, amount, reason, request_id: requestId },
  });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).bonus;
}

export async function cancelBonusAward(
  request: APIRequestContext,
  token: string,
  id: string,
  expectedVersion: number,
  reason: string,
  storeId: string = STORE1_ID,
  requestId: string = crypto.randomUUID(),
): Promise<void> {
  const response = await request.post(`/api/bonus-awards/${id}/cancellation`, {
    headers: { 'X-Store-ID': storeId, Authorization: `Bearer ${token}` },
    data: { expected_version: expectedVersion, reason, request_id: requestId },
  });
  expect(response.status(), await response.text()).toBe(200);
}

export type AdvertisingCostValues = {
  category: 'SALES' | 'RECRUITMENT';
  media_name: string;
  agency_name: string | null;
  plan_name: string | null;
  inquiry_count: number | null;
  amount: number;
};

export async function createAdvertisingCost(
  request: APIRequestContext, token: string, month: string, values: AdvertisingCostValues,
  storeId = STORE1_ID,
): Promise<{ id: string; version: number }> {
  const response = await request.post('/api/advertising-costs', {
    headers: { 'X-Store-ID': storeId, Authorization: `Bearer ${token}` },
    data: { ...values, month, request_id: crypto.randomUUID() },
  });
  expect(response.status(), await response.text()).toBe(201);
  return response.json();
}

export async function updateAdvertisingCost(
  request: APIRequestContext, token: string, cost: { id: string; version: number },
  values: AdvertisingCostValues, reason: string, storeId = STORE1_ID,
): Promise<{ id: string; version: number }> {
  const response = await request.put(`/api/advertising-costs/${cost.id}`, {
    headers: { 'X-Store-ID': storeId, Authorization: `Bearer ${token}` },
    data: { ...values, version: cost.version, reason, request_id: crypto.randomUUID() },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}

export async function deleteAdvertisingCost(
  request: APIRequestContext, token: string, cost: { id: string; version: number },
  reason: string, storeId = STORE1_ID,
): Promise<void> {
  const response = await request.delete(`/api/advertising-costs/${cost.id}`, {
    headers: { 'X-Store-ID': storeId, Authorization: `Bearer ${token}` },
    data: { version: cost.version, reason, request_id: crypto.randomUUID() },
  });
  expect(response.status(), await response.text()).toBe(204);
}

export async function getRemunerationCandidates(
  request: APIRequestContext, token: string, search: string, storeId = STORE1_ID,
): Promise<{ content: { person_id: number }[] }> {
  const response = await request.get('/api/monthly-remunerations/casts', {
    headers: { 'X-Store-ID': storeId, Authorization: `Bearer ${token}` },
    params: { search },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}

export async function createRemunerationGuarantee(
  request: APIRequestContext, token: string,
  data: { person_id: number; effective_from: string; state: 'ACTIVE'; daily_amount: number; reason: string; expected_version: number },
  storeId = STORE1_ID,
): Promise<void> {
  const response = await request.post('/api/remuneration-guarantees', {
    headers: { 'X-Store-ID': storeId, Authorization: `Bearer ${token}` },
    data: { ...data, request_id: crypto.randomUUID() },
  });
  expect(response.status(), await response.text()).toBe(201);
}

export type ServiceSettingChange = {
  name: string;
  duration_minutes?: number;
  charge_type?: "FREE" | "PAID";
  price: number;
  remuneration: number;
  expected_version: number;
};

export async function getServiceSetting(
  request: APIRequestContext,
  token: string,
  id: string,
  storeId: string = STORE1_ID,
): Promise<{ id: string; version: number; deleted: boolean }> {
  const response = await request.get(`/api/services/${id}`, {
    headers: {
      ...STORE_HEADERS,
      "X-Store-ID": storeId,
      Authorization: `Bearer ${token}`,
    },
  });
  expect(response.status()).toBe(200);
  return response.json();
}

export async function updateServiceSetting(
  request: APIRequestContext,
  token: string,
  id: string,
  data: ServiceSettingChange,
  storeId: string = STORE1_ID,
): Promise<{ id: string; version: number }> {
  const response = await request.put(`/api/services/${id}`, {
    headers: {
      ...STORE_HEADERS,
      "X-Store-ID": storeId,
      Authorization: `Bearer ${token}`,
    },
    data,
  });
  expect(response.status()).toBe(200);
  return response.json();
}

export async function deleteServiceSetting(
  request: APIRequestContext,
  token: string,
  id: string,
  expectedVersion: number,
  storeId: string = STORE1_ID,
): Promise<void> {
  const response = await request.delete(`/api/services/${id}`, {
    headers: {
      ...STORE_HEADERS,
      "X-Store-ID": storeId,
      Authorization: `Bearer ${token}`,
    },
    params: { expected_version: expectedVersion },
  });
  expect(response.status()).toBe(204);
}

export async function decideOwnServiceCondition(
  request: APIRequestContext,
  token: string,
  id: string,
  termsVersion: number,
  consentVersion: number,
  decision: "ACCEPTED" | "REJECTED",
  storeId = STORE1_ID,
): Promise<{
  consent_version: number;
  terms_version: number;
  consent_status: string;
}> {
  const response = await request.put(
    `/api/me/service-conditions/${id}/consent`,
    {
      headers: { Authorization: `Bearer ${token}` },
      params: { store_id: storeId },
      data: {
        terms_version: termsVersion,
        consent_version: consentVersion,
        decision,
      },
    },
  );
  expect(response.status()).toBe(200);
  return response.json();
}

export async function getMe(
  request: APIRequestContext,
  token: string,
): Promise<{ email: string; user_type: string }> {
  const response = await request.get("/api/me", {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.status()).toBe(200);
  return response.json();
}

export async function listServiceRevisions(
  request: APIRequestContext,
  token: string,
  id: string,
  storeId: string = STORE1_ID,
): Promise<{ content: { id: string; version: number }[] }> {
  const response = await request.get(`/api/services/${id}/revisions`, {
    headers: {
      ...STORE_HEADERS,
      "X-Store-ID": storeId,
      Authorization: `Bearer ${token}`,
    },
  });
  expect(response.status()).toBe(200);
  return response.json();
}

export async function findCastUserId(
  request: APIRequestContext,
  token: string,
  displayName: string,
): Promise<number> {
  const headers = { Authorization: `Bearer ${token}` };
  const found = await request.get("/api/cast-persons", {
    headers,
    params: { search: displayName },
  });
  expect(found.status()).toBe(200);
  const rows = (await found.json()).content as {
    id: number;
    display_name: string;
  }[];
  const matching = rows.filter((row) => row.display_name === displayName);
  expect(matching).toHaveLength(1);
  const detail = await request.get(`/api/cast-persons/${matching[0].id}`, {
    headers,
  });
  expect(detail.status()).toBe(200);
  return (await detail.json()).user_id;
}

/** 1×1 の PNG。保存と取得で同じバイト列が返ることを比べる基準にする。 */
export const SAMPLE_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==",
  "base64",
);

/** 店舗 1 の領域へ SAMPLE_PNG を店舗ロゴとしてアップロードする（POST /api/files）。 */
export function uploadStoreImage(
  request: APIRequestContext,
  token: string,
): Promise<APIResponse> {
  return request.post("/api/files", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    multipart: {
      purpose: "store-logo",
      file: { name: "storage-probe.png", mimeType: "image/png", buffer: SAMPLE_PNG },
    },
  });
}
