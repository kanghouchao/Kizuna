import { expect, type APIRequestContext } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import {
  acceptCastInvitation, acceptExistingCastInvitation, cancelOrder,
  completeAgreedOrder, correctOrderExtension, createAgreedOrder, createCast, createCourse,
  getAuthorizedStores, getOrder, invalidateOrder, issueCastInvitation,
  loginAsStoreAdmin, loginPlatformUser, STORE1_ID, STORE_HEADERS, suspendCast, withdrawCast,
} from './store-api';

const { Given, When, Then } = createBdd();
const base = '/api/platform/monthly-remunerations';
let manager = '';
let castToken = '';
let firstReader = '';
let secondReader = '';
let allReader = '';
let storeOnlyReader = '';
let readerEmail = '';
let allReaderEmail = '';
let password = '';
let storeName = '';
let secondStore = '';
let secondStoreName = '';
let personId = 0;
let foreignPersonId = 0;
let name = '';
let originalMonth = '';
let originalDate = '';
let originalOrder = '';
let secondOrder = '';
let foreignOrder = '';
const headers = (token: string) => ({ Authorization: 'Bearer ' + token });

async function monthly(request: APIRequestContext, token = firstReader, storeId = STORE1_ID, page = 0, size = 20) {
  const response = await request.get(base, {
    headers: headers(token),
    params: { store_id: storeId, person_id: personId, month: originalMonth, page, size },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}

Given('月次平台照会用の本人と異なる授権集合の参照者がいる', async ({ request, $testInfo }) => {
  $testInfo.setTimeout(120000);
  manager = await loginAsStoreAdmin(request);
  const admin = await loginPlatformUser(request, 'admin@kizuna.test', 'pass');
  const stores = await getAuthorizedStores(request, manager);
  storeName = stores.find(s => String(s.id) === STORE1_ID)!.name;
  const other = stores.find(s => String(s.id) !== STORE1_ID)!;
  secondStore = String(other.id);
  secondStoreName = other.name;
  const suffix = Date.now().toString();
  password = 'Monthly-' + suffix + '-pass';
  name = '月次平台・長い源氏名の折り返し確認・' + suffix;
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date());
  originalDate = new Date(Date.UTC(Number(today.slice(0, 4)), Number(today.slice(5, 7)) - 1, 0)).toISOString().slice(0, 10);
  originalMonth = originalDate.slice(0, 7);
  const first = await createCast(request, manager, name + '-旧名');
  const email = 'platform-monthly-cast-' + suffix + '@kizuna.test';
  await acceptCastInvitation(request, await issueCastInvitation(request, manager, first), email, password, name);
  castToken = await loginPlatformUser(request, email, password);
  const course = await createCourse(request, manager, '月次平台・長いサービス概要の折り返しを確認するコース');
  originalOrder = await createAgreedOrder(request, manager, first, course, '月次平台顧客', { businessDate: originalDate });
  await completeAgreedOrder(request, manager, originalOrder);
  expect((await getOrder(request, manager, STORE1_ID, originalOrder)).completed_at!.slice(0, 7) > originalMonth).toBeTruthy();
  await withdrawCast(request, manager, first);
  const second = await createCast(request, manager, name + '-新名');
  await acceptExistingCastInvitation(request, castToken, await issueCastInvitation(request, manager, second));
  secondOrder = await createAgreedOrder(request, manager, second, course, '再入店後', { businessDate: originalDate });
  await completeAgreedOrder(request, manager, secondOrder);
  await createAgreedOrder(request, manager, second, course, '未完了', { businessDate: originalDate });
  const cancelled = await createAgreedOrder(request, manager, second, course, '取消', { businessDate: originalDate });
  await cancelOrder(request, manager, cancelled, '提供しないため取消');
  const next = await createAgreedOrder(request, manager, second, course, '翌月', { businessDate: today });
  await completeAgreedOrder(request, manager, next);
  await withdrawCast(request, manager, second);
  const foreign = await createCast(request, manager, name + '-他店', secondStore);
  await acceptExistingCastInvitation(request, castToken, await issueCastInvitation(request, manager, foreign, secondStore));
  const foreignCourse = await createCourse(request, manager, '他店月次コース', secondStore);
  foreignOrder = await createAgreedOrder(request, manager, foreign, foreignCourse, '他店', { storeId: secondStore, businessDate: originalDate });
  await completeAgreedOrder(request, manager, foreignOrder, secondStore);
  await suspendCast(request, manager, foreign, secondStore);
  const onlyForeign = await createCast(request, manager, name + '-他店限定', secondStore);
  await acceptCastInvitation(request, await issueCastInvitation(request, manager, onlyForeign, secondStore),
    'platform-monthly-foreign-' + suffix + '@kizuna.test', password, '他店限定本人');
  const role = await request.post('/api/platform/roles', { headers: headers(admin),
    data: { name: '月次平台参照-' + suffix, permissions: ['ORDER_SET_MANAGE', 'PLATFORM_MENU_VIEW'] } });
  expect(role.status(), await role.text()).toBe(201);
  const roleId = (await role.json()).id;
  const storeOnlyRole = await request.post('/api/platform/roles', { headers: headers(admin),
    data: { name: '月次店舗権限のみ-' + suffix, permissions: ['ORDER_MANAGE', 'PLATFORM_MENU_VIEW'] } });
  expect(storeOnlyRole.status(), await storeOnlyRole.text()).toBe(201);
  const storeOnlyRoleId = (await storeOnlyRole.json()).id;
  const tokens: string[] = [];
  for (const [index, scope] of [STORE1_ID, secondStore, 'all', STORE1_ID].entries()) {
    const email = 'platform-monthly-reader-' + suffix + '-' + index + '@kizuna.test';
    if (index === 0) readerEmail = email;
    if (index === 2) allReaderEmail = email;
    const created = await request.post('/api/platform/staff', { headers: headers(admin), data: {
      email, password, display_name: '月次参照者-' + suffix + '-' + index,
      role_ids: [index === 3 ? storeOnlyRoleId : roleId], store_scope_type: scope === 'all' ? 'ALL_STORES' : 'SPECIFIC_STORES',
      store_ids: scope === 'all' ? [] : [Number(scope)],
    } });
    expect(created.status(), await created.text()).toBe(201);
    tokens.push(await loginPlatformUser(request, email, password));
  }
  [firstReader, secondReader, allReader, storeOnlyReader] = tokens;
  const candidates = await request.get(base + '/casts', { headers: headers(firstReader),
    params: { store_id: STORE1_ID, search: name + '-旧名' } });
  expect(candidates.status()).toBe(200);
  const people = (await candidates.json()).content;
  expect(people).toHaveLength(1);
  expect(people[0].name).toBe(name + '-新名');
  personId = people[0].person_id;
  const foreignCandidates = await request.get(base + '/casts', { headers: headers(secondReader),
    params: { store_id: secondStore, search: name + '-他店限定' } });
  expect(foreignCandidates.status()).toBe(200);
  foreignPersonId = (await foreignCandidates.json()).content[0].person_id;
});

When('平台参照者が店舗と本人と原月を選択する', async ({ page }) => {
  await page.goto(PLATFORM_URL + '/platform/login');
  await page.getByLabel('メールアドレス', { exact: true }).fill(readerEmail);
  await page.getByLabel('パスワード', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login$/, { timeout: 15000 });
  await page.goto(PLATFORM_URL + '/platform/orders');
  await page.getByRole('link', { name: '月次給与明細', exact: true }).click();
  await page.getByRole('button', { name: '照会', exact: true }).click();
  await expect(page.getByText('店舗を選択してください', { exact: true })).toBeVisible();
  await page.getByRole('combobox', { name: '店舗', exact: true }).click();
  await page.getByRole('option').filter({ has: page.getByText(storeName, { exact: true }) }).click();
  await page.getByRole('combobox', { name: 'キャスト本人', exact: true }).click();
  await page.getByPlaceholder('源氏名で検索').fill(name + '-旧名');
  await page.getByRole('option').filter({ hasText: name + '-新名' }).click();
  await page.getByLabel('対象月').fill(originalMonth);
  await page.getByRole('button', { name: '照会', exact: true }).click();
  await expect(page.getByLabel('月次報酬合計')).toHaveText('¥14,000');
});

Then('平台の月次合計は同店の再入店を含み他店と未完了と取消を含まない', async ({ request, page }) => {
  const first = await monthly(request, firstReader, STORE1_ID, 0, 1);
  const second = await monthly(request, firstReader, STORE1_ID, 1, 1);
  for (const result of [first, second]) {
    expect(result).toMatchObject({ store_id: Number(STORE1_ID), store_name: storeName, person_id: personId,
      name: name + '-新名', month: originalMonth, total_remuneration: 14000, orders: { total_elements: 2 } });
  }
  expect([first.orders.content[0].order_id, second.orders.content[0].order_id].sort())
    .toEqual([originalOrder, secondOrder].sort());
  await expect(page.getByRole('button', { name: '訂正履歴', exact: true })).toHaveCount(2);
  await expect(page.locator('a[href*="/store/"]')).toHaveCount(0);
  expect((await monthly(request, secondReader, secondStore)).total_remuneration).toBe(7000);
});

When('平台照会の原月受注を訂正して無効化する', async ({ request }) => {
  await correctOrderExtension(request, manager, originalOrder, '原月の延長報酬を訂正');
  expect((await monthly(request)).total_remuneration).toBe(16000);
  await invalidateOrder(request, manager, originalOrder, '未提供の原月受注を無効化');
});

Then('平台で原月の最新合計と既存の訂正履歴を確認できる', async ({ request, page }) => {
  const result = await monthly(request);
  expect(result.total_remuneration).toBe(7000);
  expect(result.orders.content.find((row: { order_id: string }) => row.order_id === originalOrder))
    .toMatchObject({ business_date: originalDate, accrued_remuneration: 0, completion_invalidated: true });
  await page.getByRole('button', { name: '照会', exact: true }).click();
  await expect(page.getByLabel('月次報酬合計')).toHaveText('¥7,000');
  const row = page.getByRole('row').filter({ hasText: originalOrder });
  await expect(row).toContainText('無効化済み');
  await row.getByRole('button', { name: '訂正履歴' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toContainText('原月の延長報酬を訂正');
  await expect(dialog).toContainText('未提供の原月受注を無効化');
  await expect(dialog).toContainText('¥7,000 → ¥9,000');
  await expect(dialog).toContainText('¥9,000 → ¥0');
});

Then('月次平台照会は異なる授権集合と直接HTTP要求を検証する', async ({ request }) => {
  for (const [token, expected] of [[firstReader, STORE1_ID], [secondReader, secondStore]]) {
    const response = await request.get(base + '/stores', { headers: headers(token), params: { size: 1 } });
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ total_elements: 1, content: [{ store_id: Number(expected) }] });
  }
  const all = await request.get(base + '/stores', { headers: headers(allReader) });
  expect(all.status()).toBe(200);
  expect((await all.json()).content.map((s: { store_id: number }) => s.store_id))
    .toEqual(expect.arrayContaining([Number(STORE1_ID), Number(secondStore)]));
  for (const search of ['%', '_', '  ' + storeName.toUpperCase() + '  ']) {
    const response = await request.get(base + '/stores', { headers: headers(firstReader), params: { search } });
    expect(response.status()).toBe(200);
    const result = await response.json();
    expect(result.total_elements).toBe(search.trim() === storeName.toUpperCase() ? 1 : 0);
  }
  for (const suffix of ['', '/casts', '/pdf']) {
    for (const storeId of [secondStore, '9223372036854775807']) {
      const denied = await request.get(base + suffix, { headers: { ...STORE_HEADERS, ...headers(firstReader) },
        params: { store_id: storeId, person_id: personId, month: originalMonth } });
      expect(denied.status()).toBe(403);
    }
  }
  const wrongPerson = await request.get(base, { headers: headers(firstReader),
    params: { store_id: STORE1_ID, person_id: foreignPersonId, month: originalMonth } });
  expect(wrongPerson.status()).toBe(404);
  const missing = await request.get(base + '/casts', { headers: headers(allReader),
    params: { store_id: '9223372036854775807' } });
  expect(missing.status()).toBe(404);
  for (const suffix of ['', '/casts', '/stores', '/pdf']) {
    const params = { store_id: STORE1_ID, person_id: personId, month: originalMonth };
    expect((await request.get(base + suffix, { params })).status()).toBe(401);
    for (const token of [storeOnlyReader, castToken]) {
      expect((await request.get(base + suffix, { headers: headers(token), params })).status()).toBe(403);
    }
  }
  expect((await monthly(request, allReader, STORE1_ID)).total_remuneration).toBe(14000);
  expect((await monthly(request, allReader, secondStore)).total_remuneration).toBe(7000);
  const suspendedCandidates = await request.get(base + '/casts', { headers: headers(secondReader),
    params: { store_id: secondStore, search: name + '-他店' } });
  expect(suspendedCandidates.status()).toBe(200);
  expect((await suspendedCandidates.json()).content.map((person: { person_id: number }) => person.person_id)).toContain(personId);
  expect((await request.get('/api/platform/stores/me', { headers: headers(firstReader) })).status()).toBe(403);
  expect((await request.get(base + '/stores', { headers: headers('invalid.jwt') })).status()).toBe(401);
  const history = await request.get('/api/platform/orders/' + foreignOrder + '/corrections', { headers: headers(firstReader) });
  expect(history.status()).toBe(404);
  const storeBypass = await request.get('/api/store/monthly-remunerations',
    { headers: { ...STORE_HEADERS, ...headers(firstReader) }, params: { person_id: personId, month: originalMonth } });
  expect(storeBypass.status()).toBe(403);
  const selfBypass = await request.get('/api/platform/me/remunerations/' + originalOrder, { headers: headers(firstReader) });
  expect(selfBypass.status()).toBe(403);
  for (const [key, value] of [['month', '2026-13'], ['page', '-1'], ['page', '2147483647'],
      ['person_id', '0'], ['store_id', '0']]) {
    const invalid = await request.get(base, { headers: headers(firstReader),
      params: { store_id: STORE1_ID, person_id: personId, month: originalMonth, [key]: value } });
    expect(invalid.status()).toBe(400);
  }
  for (const size of [0, -1, 2001]) {
    const result = await monthly(request, firstReader, STORE1_ID, 0, size);
    expect(result.orders.size).toBe(Math.max(1, Math.min(size, 2000)));
    expect(result.total_remuneration).toBe(14000);
  }
  const beyond = await monthly(request, firstReader, STORE1_ID, 99, 1);
  expect(beyond.orders.content).toHaveLength(0);
  expect(beyond.total_remuneration).toBe(14000);
  const empty = await request.get(base, { headers: headers(firstReader),
    params: { store_id: STORE1_ID, person_id: personId, month: '2000-01' } });
  expect(empty.status()).toBe(200);
  expect(await empty.json()).toMatchObject({ total_remuneration: 0, orders: { content: [] } });
  const malformed = await request.get(base, { params: { store_id: 'bad' } });
  expect(malformed.status()).toBe(400);
});

Then('平台月次画面の明暗と長い名称と狭幅とキーボードを確認できる', async ({ page, $testInfo }) => {
  const longStoreName = storeName + '・長い店舗名称の省略表示確認'.repeat(6);
  const storesPath = '**/api/platform/monthly-remunerations/stores?*';
  await page.route(storesPath, async route => {
    const response = await route.fetch();
    const data = await response.json();
    data.content = data.content.map((store: { store_id: number; store_name: string }) =>
      store.store_id === Number(STORE1_ID) ? { ...store, store_name: longStoreName } : store);
    await route.fulfill({ response, json: data });
  });
  await page.getByRole('combobox', { name: '店舗', exact: true }).click();
  await page.getByRole('option').filter({ hasText: longStoreName }).click();
  await expect(page.getByRole('combobox', { name: '店舗', exact: true })).toContainText(longStoreName);
  await page.unroute(storesPath);
  await page.setViewportSize({ width: 1024, height: 900 });
  for (const theme of ['light', 'dark']) {
    await page.evaluate(value => document.documentElement.classList.toggle('dark', value === 'dark'), theme);
    await expect(page.getByText(name + '-新名', { exact: false }).first()).toBeVisible();
    await page.getByLabel('対象月').focus();
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: '照会', exact: true })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.getByLabel('月次報酬合計')).toHaveText('¥14,000');
    const file = $testInfo.outputPath('platform-monthly-' + theme + '.png');
    await page.screenshot({ path: file, fullPage: true });
    await $testInfo.attach('月次給与明細-' + theme, { path: file, contentType: 'image/png' });
  }
  await page.setViewportSize({ width: 390, height: 844 });
  const file = $testInfo.outputPath('platform-monthly-narrow.png');
  await page.screenshot({ path: file, fullPage: true });
  await $testInfo.attach('月次給与明細-狭幅', { path: file, contentType: 'image/png' });
  const historyButton = page.getByRole('button', { name: '訂正履歴', exact: true }).first();
  await historyButton.scrollIntoViewIfNeeded();
  await historyButton.click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.setViewportSize({ width: 1280, height: 900 });
});

Then('平台月次画面は失敗から再試行でき店舗切替で明細を消す', async ({ page }) => {
  const path = /\/api\/platform\/monthly-remunerations\?/;
  await page.route(path, route => route.fulfill({ status: 500, json: { error: '検証用の取得失敗' } }));
  await page.getByRole('button', { name: '照会', exact: true }).click();
  await expect(page.getByRole('main').getByRole('alert')).toContainText('月次給与明細を取得できませんでした');
  await expect(page.getByLabel('月次報酬合計')).toHaveCount(0);
  await page.unroute(path);
  await page.getByRole('button', { name: '再試行', exact: true }).click();
  await expect(page.getByLabel('月次報酬合計')).toHaveText('¥14,000');
  await page.getByRole('combobox', { name: '店舗', exact: true }).click();
  await page.getByRole('option').filter({ has: page.getByText(secondStoreName, { exact: true }) }).click();
  await expect(page.getByLabel('月次報酬合計')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '訂正履歴', exact: true })).toHaveCount(0);
  await expect(page.getByRole('combobox', { name: 'キャスト本人', exact: true })).toContainText('源氏名で検索');
});

Given('月次平台画面の確認者が全店授権である', async () => { readerEmail = allReaderEmail; });
