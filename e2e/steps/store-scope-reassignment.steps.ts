import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import {
  STORE1_ID,
  STORE_HEADERS,
  acceptCastInvitation,
  createCast,
  createPermissionRole,
  createPlatformStaffFixture,
  deleteCast,
  getAuthorizedStores,
  issueCastInvitation,
  loginAsStoreAdmin,
  loginPlatformUser,
  registerMember,
} from './store-api';

const { Given, When, Then, After } = createBdd();

const headers = (token: string) => ({ Authorization: `Bearer ${token}` });
const storeHeaders = (token: string, storeId: string) => ({
  ...STORE_HEADERS,
  'X-Store-ID': storeId,
  Authorization: `Bearer ${token}`,
});

let admin = '';
let staffId = 0;
let staffToken = '';
let otherStoreId = '';
let castToken = '';
let memberToken = '';
let createdCastId = '';
let storeAdminToken = '';

/** 店舗 2 の id は seed を直書きせず、全店舗授権の管理者の授権店舗一覧から店舗 1 以外として読む。 */
async function resolveOtherStore(request: Parameters<typeof getAuthorizedStores>[0]): Promise<string> {
  const stores = await getAuthorizedStores(request, admin);
  const other = stores.find(store => String(store.id) !== STORE1_ID);
  expect(other, '店舗 1 以外の店舗が seed に無い').toBeDefined();
  return String(other!.id);
}

async function updateStaffStores(request: Parameters<typeof getAuthorizedStores>[0], storeIds: number[]): Promise<void> {
  const current = await request.get(`/api/platform/staff/${staffId}`, { headers: headers(admin) });
  expect(current.status(), await current.text()).toBe(200);
  const body = await current.json();
  const updated = await request.put(`/api/platform/staff/${staffId}`, {
    headers: headers(admin),
    data: {
      role_ids: body.roles.map((role: { id: number }) => role.id),
      store_scope_type: 'SPECIFIC_STORES',
      store_ids: storeIds,
      version: body.version,
    },
  });
  expect(updated.status(), await updated.text()).toBe(200);
}

async function listOrdersStatus(request: Parameters<typeof getAuthorizedStores>[0], token: string, storeId: string): Promise<number> {
  const response = await request.get('/api/store/orders', {
    headers: storeHeaders(token, storeId),
    params: { page: 0, size: 1 },
  });
  return response.status();
}

Given('店舗1だけを担当する検証スタッフがログイン済みである', async ({ request }) => {
  admin = await loginPlatformUser(request, 'admin@kizuna.test', 'pass');
  otherStoreId = await resolveOtherStore(request);
  const suffix = Date.now().toString();
  // 管理者管理の対象は HQ 側ロール保持者なので、平台権限と店舗コンソール権限を併せ持つ混成ロールにする
  // （hybrid-console-access と同じ形。ORDER_MANAGE が storeBridge を立て、店舗の受注一覧を読める）。
  const role = await createPermissionRole(request, admin, '作用域検証-' + suffix, ['PLATFORM_MENU_VIEW', 'ORDER_MANAGE', 'STORE_MENU_VIEW']);
  const email = 'scope-' + suffix + '@kizuna.test';
  const password = 'Scope-' + suffix + '-fixture';
  staffId = await createPlatformStaffFixture(request, admin, email, password, [role], [Number(STORE1_ID)]);
  staffToken = await loginPlatformUser(request, email, password);
});

Then('検証スタッフは店舗1の受注一覧を読めて店舗2は拒否される', async ({ request }) => {
  expect(await listOrdersStatus(request, staffToken, STORE1_ID)).toBe(200);
  expect(await listOrdersStatus(request, staffToken, otherStoreId)).toBe(403);
});

Then('検証スタッフの me 応答とトークンに店舗集合が載っていない', async ({ request }) => {
  const me = await request.get('/api/platform/me', { headers: headers(staffToken) });
  expect(me.status()).toBe(200);
  const body = await me.json();
  expect(body.user_type).toBe('PLATFORM_USER');
  expect(body).not.toHaveProperty('store_scope_type');
  expect(body).not.toHaveProperty('store_ids');
  const claims = JSON.parse(Buffer.from(staffToken.split('.')[1], 'base64url').toString('utf8'));
  expect(claims.userType).toBe('PLATFORM_USER');
  expect(claims.storeBridge).toBe(true);
  expect(claims).not.toHaveProperty('storeScopeType');
  expect(claims).not.toHaveProperty('storeIds');
});

When('HQ管理者が検証スタッフの店舗集合を店舗1と店舗2に再割当する', async ({ request }) => {
  await updateStaffStores(request, [Number(STORE1_ID), Number(otherStoreId)]);
});

Then('同じトークンのまま店舗2の受注一覧が読める', async ({ request }) => {
  expect(await listOrdersStatus(request, staffToken, otherStoreId)).toBe(200);
});

Then('同じトークンのまま授権店舗一覧に店舗2が現れる', async ({ request }) => {
  const stores = await getAuthorizedStores(request, staffToken);
  expect(stores.map(store => String(store.id))).toEqual(expect.arrayContaining([STORE1_ID, otherStoreId]));
});

When('HQ管理者が検証スタッフの店舗集合を店舗1だけに戻す', async ({ request }) => {
  await updateStaffStores(request, [Number(STORE1_ID)]);
});

Then('同じトークンのまま店舗2の受注一覧が拒否される', async ({ request }) => {
  expect(await listOrdersStatus(request, staffToken, otherStoreId)).toBe(403);
  const stores = await getAuthorizedStores(request, staffToken);
  expect(stores.map(store => String(store.id))).toEqual([STORE1_ID]);
});

Given('招待受諾済みのキャストと自助登録済みの会員がログイン済みである', async ({ request }) => {
  const suffix = Date.now().toString();
  storeAdminToken = await loginAsStoreAdmin(request);
  createdCastId = await createCast(request, storeAdminToken, '作用域検証キャスト-' + suffix);
  const invitation = await issueCastInvitation(request, storeAdminToken, createdCastId);
  const castEmail = 'scope-cast-' + suffix + '@kizuna.test';
  const password = 'Scope-' + suffix + '-fixture';
  await acceptCastInvitation(request, invitation, castEmail, password, '作用域検証キャスト');
  castToken = await loginPlatformUser(request, castEmail, password);
  const memberEmail = 'scope-member-' + suffix + '@kizuna.test';
  await registerMember(request, memberEmail, password, '作用域検証会員');
  memberToken = await loginPlatformUser(request, memberEmail, password);
});

Then('キャストと会員の me 応答に店舗集合が載っていない', async ({ request }) => {
  for (const [token, type] of [[castToken, 'CAST'], [memberToken, 'MEMBER']] as const) {
    const me = await request.get('/api/platform/me', { headers: headers(token) });
    expect(me.status()).toBe(200);
    const body = await me.json();
    expect(body.user_type).toBe(type);
    expect(body).not.toHaveProperty('store_scope_type');
    expect(body).not.toHaveProperty('store_ids');
    const claims = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
    expect(claims.userType).toBe(type);
    expect(claims).not.toHaveProperty('storeScopeType');
    expect(claims).not.toHaveProperty('storeIds');
  }
});

Then('キャストと会員の授権店舗一覧は拒否される', async ({ request }) => {
  for (const token of [castToken, memberToken]) {
    const response = await request.get('/api/platform/stores/me', { headers: headers(token) });
    expect(response.status()).toBe(403);
  }
});

Then('キャストの所属店舗一覧は在籍から解決される', async ({ request }) => {
  const response = await request.get('/api/platform/me/stores', { headers: headers(castToken) });
  expect(response.status(), await response.text()).toBe(200);
  const stores = (await response.json()) as { store_id: number; store_name: string }[];
  expect(stores.map(store => String(store.store_id))).toEqual([STORE1_ID]);
});

After(async ({ request }) => {
  if (createdCastId && storeAdminToken) {
    await deleteCast(request, storeAdminToken, createdCastId).catch(() => {});
    createdCastId = '';
  }
});
