import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { APP_URL } from '../base-url';
import {
  ADMIN_PASSWORD,
  authorizedStoreIds,
  createPermissionRole,
  createStaffFixture,
  expectSidebarLinkAbsentEverywhere,
  getAuthorizedStores,
  loginAsStoreAdmin,
  loginUser,
  openLoginForm,
  revealSidebarLink,
  selectRailContext,
  sidebarPanel,
  sidebarRail,
  storePath,
  storeUrl,
  waitSidebarReady,
} from './store-api';

const { Given, Then, When } = createBdd();

// 既定の店長はグループの木の従業員の権限も持つので、店舗の木だけの利用者は検証用のロールで作る。
// ロールの作成は統括（ROLE_MANAGE）、従業員の作成は配る権限を持つ店長（委譲は権限の部分集合に限る）が行う。
const STORE_TREE_PERMISSIONS = ['ORDER_MANAGE', 'CAST_MANAGE', 'CUSTOMER_MANAGE'];
let storeOnlyEmail = '';
let storeOnlyPassword = '';

Given('店舗の木の権限だけを持つロールで2店舗を担当する従業員を API で用意する', async ({ request }) => {
  const suffix = Date.now().toString();
  const admin = await loginUser(request, 'admin@kizuna.test', ADMIN_PASSWORD);
  const roleId = await createPermissionRole(request, admin, '店舗の木だけ-' + suffix, STORE_TREE_PERMISSIONS);
  const manager = await loginAsStoreAdmin(request);
  const storeIds = await authorizedStoreIds(request, manager);
  expect(storeIds.length).toBeGreaterThanOrEqual(2);
  storeOnlyEmail = 'store-tree-' + suffix + '@kizuna.test';
  storeOnlyPassword = 'StoreTree-' + suffix + '-fixture';
  await createStaffFixture(request, manager, storeOnlyEmail, storeOnlyPassword, [roleId], storeIds);
});

When('用意した従業員でログインする', async ({ page }) => {
  await openLoginForm(page);
  await page.getByLabel('メールアドレス', { exact: true }).fill(storeOnlyEmail);
  await page.getByLabel('パスワード', { exact: true }).fill(storeOnlyPassword);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
});

// ログイン系ステップ（統一ログイン画面を開く / メール...でログインする / …のダッシュボードへ遷移する）
// は login.steps.ts の定義を再利用する（playwright-bdd は steps/**/*.ts を横断解決するため
// 同一ステップ文言の再定義は不要）。

// リンクの有無・クリックは、そのリンクを含む文脈を選んでから行う（既定の文脈に無ければレールで探す）。
// 「ない」はどの文脈のパネルにも無いことを指す。文脈ごとの断言は「サイドバーのパネル」系のステップを使う。
const linkByName = (name: string) => (panel: Locator) =>
  panel.getByRole('link', { name, exact: true });

Then('サイドバーに {string} のリンクがある', async ({ page }, name: string) => {
  await expect(await revealSidebarLink(page, linkByName(name))).toBeVisible();
});

Then('サイドバーに {string} のリンクがない', async ({ page }, name: string) => {
  await expectSidebarLinkAbsentEverywhere(page, linkByName(name));
});

When('サイドバーの {string} リンクをクリックする', async ({ page }, name: string) => {
  await (await revealSidebarLink(page, linkByName(name))).click();
});

When('サイドバーのレールで {string} を選ぶ', async ({ page }, label: string) => {
  await selectRailContext(page, label);
});

Then('サイドバーのレールに {string} が表示される', async ({ page }, label: string) => {
  await expect(sidebarRail(page).getByRole('link', { name: label, exact: true })).toBeVisible();
});

Then('サイドバーのレールに {string} が表示されない', async ({ page }, label: string) => {
  // パネルの描画完了を待ってから欠如を断言する（未描画のまま 0 件になる空振りを避ける）。
  await waitSidebarReady(page);
  await expect(sidebarRail(page).getByRole('link', { name: label, exact: true })).toHaveCount(0);
});

Then('サイドバーのレールで {string} が選択されている', async ({ page }, label: string) => {
  await expect(sidebarRail(page).getByRole('link', { name: label, exact: true })).toHaveAttribute('aria-current', 'page');
  await expect(sidebarRail(page).locator('[aria-current="page"]')).toHaveCount(1);
});

Then('サイドバーにレールが表示されない', async ({ page }) => {
  await waitSidebarReady(page);
  await expect(sidebarRail(page)).toHaveCount(0);
  // レールが無いときはパネルが 256px 全幅を占める。
  const width = await page.locator('aside').evaluate(el => el.getBoundingClientRect().width);
  expect(Math.round(width)).toBe(256);
});

Then('サイドバーのパネルの見出しが {string} である', async ({ page }, title: string) => {
  await expect(sidebarPanel(page).locator('span[title]')).toHaveText(title);
});

Then('サイドバーのパネルに {string} のリンクがある', async ({ page }, name: string) => {
  await expect(sidebarPanel(page).getByRole('link', { name, exact: true })).toBeVisible();
});

Then('サイドバーのパネルに {string} のリンクがない', async ({ page }, name: string) => {
  await waitSidebarReady(page);
  await expect(sidebarPanel(page).getByRole('link', { name, exact: true })).toHaveCount(0);
});

Then('サイドバーのパネルの最上段が {string} である', async ({ page }, name: string) => {
  await expect(sidebarPanel(page).locator('nav a').first()).toHaveText(name);
});

Then('サイドバーのパネルに見出し {string} がない', async ({ page }, heading: string) => {
  await expect(sidebarPanel(page).locator('nav h3', { hasText: heading })).toHaveCount(0);
});

Then('URL が {string} で終わる', async ({ page }, suffix: string) => {
  await expect(page).toHaveURL(new RegExp(`${suffix}/?$`), { timeout: 15000 });
});

Then('URL が店舗の {string} で終わる', async ({ page }, subPath: string) => {
  await expect(page).toHaveURL(new RegExp(`/store/\\d+${subPath}/?$`), { timeout: 15000 });
});

// 店名から店長の授権店舗の id を実行時に引く（店舗 1 以外の id は固定しない）。
async function storeIdByName(request: APIRequestContext, name: string): Promise<string> {
  const stores = await getAuthorizedStores(request, await loginAsStoreAdmin(request));
  const store = stores.find(candidate => candidate.name === name);
  expect(store, `店長の授権店舗に ${name} が無い`).toBeDefined();
  return String(store!.id);
}

Then('URL が店舗 {string} のダッシュボードである', async ({ page, request }, name: string) => {
  const storeId = await storeIdByName(request, name);
  await expect(page).toHaveURL(new RegExp(`${storePath(storeId, '/dashboard')}/?$`), { timeout: 15000 });
});

Then('店舗のダッシュボードに店舗 {string} が表示される', async ({ page }, name: string) => {
  // 店舗の欄は店名と稼働状態（準備中 / 稼働中）のバッジを並べる。
  const card = page.getByRole('main').locator('dl').filter({ has: page.locator('dt', { hasText: /^店舗$/ }) });
  await expect(card.locator('dd')).toHaveText(new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(準備中|稼働中)$`));
});

let openedUrl = '';
let requestedApiPaths: string[] = [];

function recordApiRequests(page: Page) {
  requestedApiPaths = [];
  page.on('request', req => {
    const path = new URL(req.url()).pathname;
    if (path.startsWith('/api/')) requestedApiPaths.push(path);
  });
}

When('店舗 {string} の {string} を開く', async ({ page, request }, name: string, subPath: string) => {
  const storeId = await storeIdByName(request, name);
  recordApiRequests(page);
  await page.goto(storeUrl(storeId, subPath));
  openedUrl = page.url();
});

When('管理画面の {string} を開く', async ({ page }, path: string) => {
  recordApiRequests(page);
  await page.goto(`${APP_URL}${path}`);
  openedUrl = page.url();
});

When('控えた URL を開く', async ({ page }) => {
  await page.goto(openedUrl);
});

Then('画面の見出しが {string} である', async ({ page }, heading: string) => {
  await expect(page.getByRole('main').getByRole('heading', { level: 1, name: heading, exact: true })).toBeVisible({ timeout: 15000 });
});

Then('警告 {string} が出て、店舗 {string} のダッシュボードへ戻る', async ({ page, request }, message: string, name: string) => {
  const storeId = await storeIdByName(request, name);
  await expect(page).toHaveURL(storeUrl(storeId, '/dashboard'), { timeout: 15000 });
  await expect(page.locator('[data-slot=toast]').filter({ hasText: `警告：${message}` })).toBeVisible();
});

// 開いた画面は描かれずに着地へ置き換わるので、その画面自身の取得は 1 件も飛ばない。残るのはシェル（本人・
// 授権店舗・メニュー）の取得だけ。着地の店舗ダッシュボードが取得を持つようになったら、その端点もここで除く。
Then('開いた画面自身の取得は送られていない', async ({ page }) => {
  await page.waitForLoadState('networkidle');
  const shell = ['/api/me', '/api/stores/me', '/api/menus/me', '/api/auth/refresh'];
  expect(requestedApiPaths.filter(path => !shell.includes(path))).toEqual([]);
});

// 着地先は作成日の最も新しい授権店舗。店舗 id は作成順に採番されるので、id の最大の店舗がそれに当たる。
Then('用意した従業員は作成日の最も新しい授権店舗のダッシュボードへ着地し、パネルとレールがその店舗を示す', async ({ page, request }) => {
  const token = await loginUser(request, storeOnlyEmail, storeOnlyPassword);
  const stores = await getAuthorizedStores(request, token);
  expect(stores.length).toBeGreaterThanOrEqual(2);
  const newest = stores.reduce((a, b) => (a.id > b.id ? a : b));
  await expect(page).toHaveURL(new RegExp(`${storePath(newest.id, '/dashboard')}/?$`), { timeout: 15000 });
  await expect(sidebarPanel(page).locator('span[title]')).toHaveText(newest.name);
  await expect(sidebarRail(page).getByRole('link', { name: newest.name, exact: true })).toHaveAttribute('aria-current', 'page');
});
