import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { CENTRAL_URL } from '../base-url';

const { Given, When, Then } = createBdd();

// central は Host（kizuna.test）で判別されるため、baseURL（store1）とは別に絶対 URL で開く。
// cookie はオリジン別に分離されるため、同一ブラウザコンテキストで問題ない。
Given('中央管理画面のログイン画面を開く', async ({ page }) => {
  await page.goto(`${CENTRAL_URL}/login`);
  await expect(page.getByRole('button', { name: 'ログイン', exact: true })).toBeVisible();
});

When(
  'ログイン名 {string} とパスワード {string} で中央にログインする',
  async ({ page }, username: string, password: string) => {
    await page.getByLabel('ログイン名', { exact: true }).fill(username);
    await page.getByLabel('パスワード', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  }
);

Then('中央ダッシュボードに到達する', async ({ page }) => {
  await expect(page).toHaveURL(/\/central\/dashboard\/?$/, { timeout: 15000 });
  await expect(page.getByRole('heading', { name: '管理コンソール', exact: true })).toBeVisible();
  await expect(page.getByText('ようこそ、adminさん', { exact: true })).toBeVisible();
});
