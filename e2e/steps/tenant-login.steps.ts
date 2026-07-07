import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';

const { Given, When, Then } = createBdd();

// tenant/central の判別は Host で行われ、baseURL は store1 ドメインを指すため
// 店舗名パラメータは経路の説明として受け取るのみ（遷移先は baseURL 固定）。
Given('テナント店舗 {string} のログイン画面を開く', async ({ page }, _store: string) => {
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'ログイン', exact: true })).toBeVisible();
});

When(
  'メールアドレス {string} とパスワード {string} でログインする',
  async ({ page }, email: string, password: string) => {
    await page.getByLabel('ログイン名', { exact: true }).fill(email);
    await page.getByLabel('パスワード', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  }
);

Then('テナントダッシュボードに到達する', async ({ page }) => {
  await expect(page).toHaveURL(/\/tenant\/dashboard\/?$/, { timeout: 15000 });
});
