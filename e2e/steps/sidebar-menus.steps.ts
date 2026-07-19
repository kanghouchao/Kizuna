import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';

const { Then } = createBdd();

// ログイン系ステップ（統一ログイン画面を開く / メール...でログインする / 中央・店舗ダッシュボードへ遷移する）
// は platform-login.steps.ts の定義を再利用する（playwright-bdd は steps/**/*.ts を横断解決するため
// 同一ステップ文言の再定義は不要）。

Then('サイドバーに {string} のリンクがある', async ({ page }, name: string) => {
  await expect(page.locator('aside').getByRole('link', { name, exact: true })).toBeVisible();
});

Then('サイドバーに {string} のリンクがない', async ({ page }, name: string) => {
  await expect(page.locator('aside').getByRole('link', { name, exact: true })).toHaveCount(0);
});
