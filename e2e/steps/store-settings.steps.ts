import { expect, Page } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { ADMIN_EMAIL, ADMIN_PASSWORD, getStoreConfig, loginAsTenantAdmin, setCustomTexts } from './tenant-api';

const { Given, When, Then, After } = createBdd();

// StoreProfileForm.tsx の模版テキスト欄は <label> と <textarea> が id/htmlFor で
// 紐付いていない（隣接する兄弟要素）ため getByLabel は使えず、隣接セレクタで特定する。
const ACCESS_NOTE_LABEL = 'アクセス補足（店舗情報ページに表示）';
const accessNoteTextarea = (page: Page) =>
  page.locator(`label:text-is("${ACCESS_NOTE_LABEL}") + textarea`);

let originalCustomTexts: Record<string, string> | null = null;
let testValue = '';

Given('店舗 {string} の管理画面にログインしている', async ({ page }, _store: string) => {
  await page.goto('/login');
  await page.getByLabel('ログイン名', { exact: true }).fill(ADMIN_EMAIL);
  await page.getByLabel('パスワード', { exact: true }).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).toHaveURL(/\/tenant\/dashboard\/?$/, { timeout: 15000 });
});

Given('店舗設定の現在値を退避する', async ({ request }) => {
  const token = await loginAsTenantAdmin(request);
  const config = await getStoreConfig(request, token);
  originalCustomTexts = (config.custom_texts as Record<string, string> | undefined) ?? null;
});

When('店舗情報ページでアクセス補足を一意な検証値に変更して保存する', async ({ page }) => {
  testValue = `E2E設定保存-${Date.now()}`;
  await page.goto('/tenant/settings/profile');
  await accessNoteTextarea(page).fill(testValue);
  await page.getByRole('button', { name: '設定を保存する', exact: true }).click();
});

Then('保存成功の通知が表示される', async ({ page }) => {
  await expect(page.getByText('設定を保存しました', { exact: true })).toBeVisible();
});

Then('再読込後もアクセス補足が同じ検証値のままである', async ({ page }) => {
  await page.reload();
  await expect(accessNoteTextarea(page)).toHaveValue(testValue);
});

// custom_texts を無条件で退避値へ復元する（テスト失敗・途中クラッシュでも実行）。
// 復元 hook 自身が独立してログインし直すため、テスト側の状態に依存しない。ベストエフォート。
After({ tags: '@store-settings' }, async ({ request }) => {
  const token = await loginAsTenantAdmin(request);
  await setCustomTexts(request, token, originalCustomTexts ?? {}).catch(() => {});
  originalCustomTexts = null;
  testValue = '';
});
