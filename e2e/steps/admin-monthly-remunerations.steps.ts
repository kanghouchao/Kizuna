import { expect, type APIRequestContext } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { APP_URL } from '../base-url';
import {
  acceptCastInvitation, acceptExistingCastInvitation, cancelOrder,
  completeAgreedOrder, correctOrderExtension, createAgreedOrder, createCast, createCourse,
  getAuthorizedStores, getOrder, invalidateOrder, issueCastInvitation,
  loginAsStoreAdmin, loginUser, STORE1_ID, suspendCast, switchStoreOnRail, withdrawCast,
  openLoginForm,
  monthInTokyo,
} from './store-api';

const { Given, When, Then } = createBdd();
const base = '/api/monthly-remunerations';
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
const headers = (token: string, storeId = STORE1_ID) =>
  ({ Authorization: 'Bearer ' + token, 'X-Store-ID': storeId });

async function monthly(request: APIRequestContext, token = firstReader, storeId = STORE1_ID, page = 0, size = 20) {
  const response = await request.get(base, {
    headers: headers(token, storeId),
    params: { person_id: personId, month: originalMonth, page, size },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}

Given('月次横断照会用の本人と異なる授権集合の照会者がいる', async ({ request, $testInfo }) => {
  $testInfo.setTimeout(120000);
  manager = await loginAsStoreAdmin(request);
  const admin = await loginUser(request, 'admin@kizuna.test', 'pass1234');
  const stores = await getAuthorizedStores(request, manager);
  storeName = stores.find(s => String(s.id) === STORE1_ID)!.name;
  const other = stores.find(s => String(s.id) !== STORE1_ID)!;
  secondStore = String(other.id);
  secondStoreName = other.name;
  const suffix = Date.now().toString();
  password = 'Monthly-' + suffix + '-pass';
  name = '月次グループ級・長い源氏名の折り返し確認・' + suffix;
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date());
  originalDate = new Date(Date.UTC(Number(today.slice(0, 4)), Number(today.slice(5, 7)) - 1, 0)).toISOString().slice(0, 10);
  originalMonth = originalDate.slice(0, 7);
  const first = await createCast(request, manager, name + '-旧名');
  const email = 'admin-monthly-cast-' + suffix + '@kizuna.test';
  await acceptCastInvitation(request, await issueCastInvitation(request, manager, first), email, password, name);
  castToken = await loginUser(request, email, password);
  const course = await createCourse(request, manager, '月次グループ級・長いサービス概要の折り返しを確認するコース');
  originalOrder = await createAgreedOrder(request, manager, first, course, '月次グループ級顧客', { businessDate: originalDate });
  await completeAgreedOrder(request, manager, originalOrder);
  expect(monthInTokyo((await getOrder(request, manager, STORE1_ID, originalOrder)).completed_at!) > originalMonth).toBeTruthy();
  await withdrawCast(request, manager, first);
  const second = await createCast(request, manager, name + '-新名');
  await acceptExistingCastInvitation(request, await issueCastInvitation(request, manager, second), email, password);
  secondOrder = await createAgreedOrder(request, manager, second, course, '再入店後', { businessDate: originalDate });
  await completeAgreedOrder(request, manager, secondOrder);
  await createAgreedOrder(request, manager, second, course, '未完了', { businessDate: originalDate });
  const cancelled = await createAgreedOrder(request, manager, second, course, '取消', { businessDate: originalDate });
  await cancelOrder(request, manager, cancelled, '提供しないため取消');
  const next = await createAgreedOrder(request, manager, second, course, '翌月', { businessDate: today });
  await completeAgreedOrder(request, manager, next);
  await withdrawCast(request, manager, second);
  const foreign = await createCast(request, manager, name + '-他店', secondStore);
  await acceptExistingCastInvitation(request, await issueCastInvitation(request, manager, foreign, secondStore), email, password);
  const foreignCourse = await createCourse(request, manager, '他店月次コース', secondStore);
  foreignOrder = await createAgreedOrder(request, manager, foreign, foreignCourse, '他店', { storeId: secondStore, businessDate: originalDate });
  await completeAgreedOrder(request, manager, foreignOrder, secondStore);
  await suspendCast(request, manager, foreign, secondStore);
  const onlyForeign = await createCast(request, manager, name + '-他店限定', secondStore);
  await acceptCastInvitation(request, await issueCastInvitation(request, manager, onlyForeign, secondStore),
    'admin-monthly-foreign-' + suffix + '@kizuna.test', password, '他店限定本人');
  const role = await request.post('/api/roles', { headers: { Authorization: 'Bearer ' + admin },
    data: { name: '月次グループ級参照-' + suffix, permissions: ['ORDER_SET_VIEW', 'REMUNERATION_VIEW'] } });
  expect(role.status(), await role.text()).toBe(201);
  const roleId = (await role.json()).id;
  const storeOnlyRole = await request.post('/api/roles', { headers: { Authorization: 'Bearer ' + admin },
    data: { name: '月次店舗権限のみ-' + suffix, permissions: ['ORDER_MANAGE',] } });
  expect(storeOnlyRole.status(), await storeOnlyRole.text()).toBe(201);
  const storeOnlyRoleId = (await storeOnlyRole.json()).id;
  const tokens: string[] = [];
  for (const [index, scope] of [STORE1_ID, secondStore, 'all', STORE1_ID].entries()) {
    const email = 'admin-monthly-reader-' + suffix + '-' + index + '@kizuna.test';
    if (index === 0) readerEmail = email;
    if (index === 2) allReaderEmail = email;
    const created = await request.post('/api/staff', { headers: { Authorization: 'Bearer ' + admin }, data: {
      email, password, display_name: '月次参照者-' + suffix + '-' + index,
      role_ids: [index === 3 ? storeOnlyRoleId : roleId], store_scope_type: scope === 'all' ? 'ALL_STORES' : 'SPECIFIC_STORES',
      store_ids: scope === 'all' ? [] : [Number(scope)],
    } });
    expect(created.status(), await created.text()).toBe(201);
    tokens.push(await loginUser(request, email, password));
  }
  [firstReader, secondReader, allReader, storeOnlyReader] = tokens;
  const candidates = await request.get(base + '/casts', { headers: headers(firstReader),
    params: { search: name + '-旧名' } });
  expect(candidates.status()).toBe(200);
  const people = (await candidates.json()).content;
  expect(people).toHaveLength(1);
  expect(people[0].name).toBe(name + '-新名');
  personId = people[0].person_id;
  const foreignCandidates = await request.get(base + '/casts', { headers: headers(secondReader, secondStore),
    params: { search: name + '-他店限定' } });
  expect(foreignCandidates.status()).toBe(200);
  foreignPersonId = (await foreignCandidates.json()).content[0].person_id;
});

When('横断照会者が受注照会から現在店舗の月次給与明細で本人と原月を選択する', async ({ page }) => {
  await openLoginForm(page);
  await page.getByLabel('メールアドレス', { exact: true }).fill(readerEmail);
  await page.getByLabel('パスワード', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login$/, { timeout: 15000 });
  await page.goto(APP_URL + '/admin/orders');
  await page.getByRole('link', { name: '月次給与明細', exact: true }).click();
  await expect(page).toHaveURL(/\/store\/orders\/monthly-remunerations$/);
  if (readerEmail === allReaderEmail) await switchStoreOnRail(page, storeName);
  // 横断照会者は日別（受注の担当者の画面）を持たない。
  await expect(page.getByRole('button', { name: '日別', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '照会', exact: true }).click();
  await expect(page.getByText('キャスト本人を選択してください', { exact: true })).toBeVisible();
  await page.getByRole('combobox', { name: 'キャスト本人', exact: true }).click();
  await page.getByPlaceholder('源氏名で検索').fill(name + '-旧名');
  await page.getByRole('option').filter({ hasText: name + '-新名' }).click();
  await page.getByLabel('対象月').fill(originalMonth);
  await page.getByRole('button', { name: '照会', exact: true }).click();
  await expect(page.getByLabel('月次報酬合計')).toHaveText('¥14,000');
});

Then('横断照会の月次合計は同店の再入店を含み他店と未完了と取消を含まない', async ({ request, page }) => {
  const first = await monthly(request, firstReader, STORE1_ID, 0, 1);
  const second = await monthly(request, firstReader, STORE1_ID, 1, 1);
  for (const result of [first, second]) {
    expect(result).toMatchObject({ person_id: personId, name: name + '-新名', month: originalMonth,
      total_remuneration: 14000, orders: { total_elements: 2 } });
    expect(result).not.toHaveProperty('store_id');
  }
  expect([first.orders.content[0].order_id, second.orders.content[0].order_id].sort())
    .toEqual([originalOrder, secondOrder].sort());
  await expect(page.getByRole('button', { name: '訂正履歴', exact: true })).toHaveCount(2);
  // 受注の編集画面は受注の担当者の権限で開くので、横断照会者には導線を出さない。
  await expect(page.locator('a[href*="/edit"]')).toHaveCount(0);
  // 保証・ボーナスの内訳は読めるが、管理（受注の担当者の端点）は出さず、その取得失敗も出さない。
  const breakdown = page.getByRole('button', { name: '保証・ボーナスを含む月次明細', exact: true });
  await breakdown.click();
  await expect(page.getByRole('heading', { name: '受注報酬・保証・ボーナス', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'ボーナスを記録' })).toHaveCount(0);
  await expect(page.getByText('取得できませんでした')).toHaveCount(0);
  await breakdown.click();
  expect((await monthly(request, secondReader, secondStore)).total_remuneration).toBe(7000);
});

When('横断照会の原月受注を訂正して無効化する', async ({ request }) => {
  await correctOrderExtension(request, manager, originalOrder, '原月の延長報酬を訂正');
  expect((await monthly(request)).total_remuneration).toBe(16000);
  await invalidateOrder(request, manager, originalOrder, '未提供の原月受注を無効化');
});

Then('横断照会で原月の最新合計と既存の訂正履歴を確認できる', async ({ request, page }) => {
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

Then('月次横断照会は異なる授権集合と直接HTTP要求を検証する', async ({ request }) => {
  const params = { person_id: personId, month: originalMonth };
  // 店舗は X-Store-ID だけで名乗る。本人の店舗集合の外（存在しない店舗を含む）は全経路で拒否する。
  for (const suffix of ['', '/casts', '/pdf']) {
    for (const storeId of [secondStore, '9223372036854775807']) {
      const denied = await request.get(base + suffix, { headers: headers(firstReader, storeId), params });
      expect(denied.status()).toBe(403);
    }
    expect((await request.get(base + suffix, { headers: { 'X-Store-ID': STORE1_ID }, params })).status()).toBe(401);
    expect((await request.get(base + suffix, { headers: headers(castToken), params })).status()).toBe(403);
    expect((await request.get(base + suffix, { headers: headers(storeOnlyReader), params })).status()).toBe(200);
  }
  const wrongPerson = await request.get(base, { headers: headers(firstReader),
    params: { person_id: foreignPersonId, month: originalMonth } });
  expect(wrongPerson.status()).toBe(404);
  expect((await monthly(request, allReader, STORE1_ID)).total_remuneration).toBe(14000);
  expect((await monthly(request, allReader, secondStore)).total_remuneration).toBe(7000);
  const suspendedCandidates = await request.get(base + '/casts', { headers: headers(secondReader, secondStore),
    params: { search: name + '-他店' } });
  expect(suspendedCandidates.status()).toBe(200);
  expect((await suspendedCandidates.json()).content.map((person: { person_id: number }) => person.person_id)).toContain(personId);
  // 授権店舗一覧は権限を 1 つ以上持つ全員に開く（STORE_VIEW の有無は問わない）。中身は本人の店舗集合だけ。
  const ownStores = await request.get('/api/stores/me', { headers: headers(firstReader) });
  expect(ownStores.status()).toBe(200);
  expect((await ownStores.json()).map((s: { id: number }) => String(s.id))).toEqual([STORE1_ID]);
  // 訂正履歴は受注の店舗を X-Store-ID に名指して読む。集合外の店舗は 403、別店舗の受注は行の隔離で 404。
  const corrections = (storeId: string) => request.get('/api/orders/' + foreignOrder + '/corrections',
    { headers: headers(firstReader, storeId) });
  expect((await corrections(secondStore)).status()).toBe(403);
  expect((await corrections(STORE1_ID)).status()).toBe(404);
  expect((await request.get('/api/orders/' + foreignOrder + '/corrections',
    { headers: headers(secondReader, secondStore) })).status()).toBe(200);
  // 横断照会は照会だけで、受注そのものと日別の読みは受注の担当者の権限に残る。
  expect((await request.get('/api/orders/' + originalOrder, { headers: headers(firstReader) })).status()).toBe(403);
  expect((await request.get('/api/daily-remunerations',
    { headers: headers(firstReader), params: { person_id: personId, business_date: originalDate } })).status()).toBe(403);
  const selfBypass = await request.get('/api/me/remunerations/' + originalOrder, { headers: headers(firstReader) });
  expect(selfBypass.status()).toBe(403);
  for (const [key, value] of [['month', '2026-13'], ['page', '-1'], ['page', '2147483647'], ['person_id', '0']]) {
    const invalid = await request.get(base, { headers: headers(firstReader), params: { ...params, [key]: value } });
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
  const empty = await request.get(base, { headers: headers(firstReader), params: { person_id: personId, month: '2000-01' } });
  expect(empty.status()).toBe(200);
  expect(await empty.json()).toMatchObject({ total_remuneration: 0, orders: { content: [] } });
});

Then('横断照会の月次画面の明暗と狭幅とキーボードを確認できる', async ({ page, $testInfo }) => {
  await page.setViewportSize({ width: 1024, height: 900 });
  for (const theme of ['light', 'dark']) {
    await page.evaluate(value => document.documentElement.classList.toggle('dark', value === 'dark'), theme);
    await expect(page.getByText(name + '-新名', { exact: false }).first()).toBeVisible();
    await page.getByLabel('対象月').focus();
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: '照会', exact: true })).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.getByLabel('月次報酬合計')).toHaveText('¥14,000');
    const file = $testInfo.outputPath('admin-monthly-' + theme + '.png');
    await page.screenshot({ path: file, fullPage: true });
    await $testInfo.attach('月次給与明細-' + theme, { path: file, contentType: 'image/png' });
  }
  await page.setViewportSize({ width: 390, height: 844 });
  const file = $testInfo.outputPath('admin-monthly-narrow.png');
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

Then('横断照会の月次画面は失敗から再試行でき店舗切替で明細を消す', async ({ page }) => {
  const path = /\/api\/monthly-remunerations\?/;
  await page.route(path, route => route.fulfill({ status: 500, json: { error: '検証用の取得失敗' } }));
  await page.getByRole('button', { name: '照会', exact: true }).click();
  await expect(page.getByRole('main').getByRole('alert')).toContainText('月次給与明細を取得できませんでした');
  await expect(page.getByLabel('月次報酬合計')).toHaveCount(0);
  await page.unroute(path);
  await page.getByRole('button', { name: '再試行', exact: true }).click();
  await expect(page.getByLabel('月次報酬合計')).toHaveText('¥14,000');
  await switchStoreOnRail(page, secondStoreName);
  await expect(page.getByLabel('月次報酬合計')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '訂正履歴', exact: true })).toHaveCount(0);
  await expect(page.getByRole('combobox', { name: 'キャスト本人', exact: true })).toContainText('源氏名で検索');
});

Given('月次横断画面の確認者が全店授権である', async () => { readerEmail = allReaderEmail; });
