import { expect, type Locator } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import {
  ADMIN_PASSWORD,
  authorizedStoreIds,
  createPermissionRole,
  createStaffFixture,
  expectSidebarLinkAbsentEverywhere,
  loginAsStoreAdmin,
  loginUser,
  openLoginForm,
  revealSidebarLink,
  selectRailContext,
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

// ログイン系ステップ（統一ログイン画面を開く / メール...でログインする / ダッシュボードへ遷移する）
// は login.steps.ts の定義を再利用する（playwright-bdd は steps/**/*.ts を横断解決するため
// 同一ステップ文言の再定義は不要）。

// リンクの有無・クリックは、そのリンクを含む文脈を選んでから行う（既定の文脈に無ければレールで探す）。
// 「ない」はどの文脈のパネルにも無いことを指す。文脈ごとの断言は「サイドバーのパネル」系のステップを使う。
const linkByName = (name: string) => (aside: Locator) =>
  aside.getByRole('link', { name, exact: true });

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
  await expect(page.locator('aside').getByRole('button', { name: label, exact: true })).toBeVisible();
});

Then('サイドバーのレールに {string} が表示されない', async ({ page }, label: string) => {
  // パネルの描画完了を待ってから欠如を断言する（未描画のまま 0 件になる空振りを避ける）。
  await expect(page.locator('aside a[href="/admin/dashboard"]')).toBeVisible({ timeout: 15000 });
  await expect(page.locator('aside').getByRole('button', { name: label, exact: true })).toHaveCount(0);
});

Then('サイドバーにレールが表示されない', async ({ page }) => {
  await expect(page.locator('aside a[href="/admin/dashboard"]')).toBeVisible({ timeout: 15000 });
  await expect(page.locator('aside button[aria-pressed]')).toHaveCount(0);
  // レールが無いときはパネルが 256px 全幅を占める。
  const width = await page.locator('aside').evaluate(el => el.getBoundingClientRect().width);
  expect(Math.round(width)).toBe(256);
});

Then('サイドバーのパネルの見出しが {string} である', async ({ page }, title: string) => {
  await expect(page.locator('aside span[title]')).toHaveText(title);
});

Then('サイドバーのパネルに {string} のリンクがある', async ({ page }, name: string) => {
  await expect(page.locator('aside').getByRole('link', { name, exact: true })).toBeVisible();
});

Then('サイドバーのパネルに {string} のリンクがない', async ({ page }, name: string) => {
  await expect(page.locator('aside').getByRole('link', { name, exact: true })).toHaveCount(0);
});

Then('サイドバーのパネルの最上段が {string} である', async ({ page }, name: string) => {
  await expect(page.locator('aside nav a').first()).toHaveText(name);
});

Then('サイドバーのパネルに見出し {string} がない', async ({ page }, heading: string) => {
  await expect(page.locator('aside nav h3', { hasText: heading })).toHaveCount(0);
});

Then('URL が {string} で終わる', async ({ page }, suffix: string) => {
  await expect(page).toHaveURL(new RegExp(`${suffix}/?$`), { timeout: 15000 });
});
