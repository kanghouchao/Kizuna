import { expect, type Locator } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import {
  expectSidebarLinkAbsentEverywhere,
  revealSidebarLink,
  selectRailContext,
} from './store-api';

const { Then, When } = createBdd();

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

Then('サイドバーのパネルのリンクがダッシュボードだけである', async ({ page }) => {
  await expect(page.locator('aside nav a')).toHaveCount(1);
  await expect(page.locator('aside nav h3')).toHaveCount(0);
});

Then('サイドバーのパネルに見出し {string} がない', async ({ page }, heading: string) => {
  await expect(page.locator('aside nav h3', { hasText: heading })).toHaveCount(0);
});

Then('URL が {string} で終わる', async ({ page }, suffix: string) => {
  await expect(page).toHaveURL(new RegExp(`${suffix}/?$`), { timeout: 15000 });
});
