import { expect, Page } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import { ADMIN_EMAIL, ADMIN_PASSWORD, getStoreConfig, loginAsStoreAdmin, setCustomTexts } from './store-api';

const { Given, When, Then, After } = createBdd();

// StoreProfileForm.tsx の模版テキスト欄は <label> と <textarea> が id/htmlFor で
// 紐付いていない（隣接する兄弟要素）ため getByLabel は使えず、隣接セレクタで特定する。
const ACCESS_NOTE_LABEL = 'アクセス補足（店舗情報ページに表示）';
const accessNoteTextarea = (page: Page) =>
  page.locator(`label:text-is("${ACCESS_NOTE_LABEL}") + textarea`);

let originalCustomTexts: Record<string, string> | null = null;
// 退避ステップが実際に成功したかの哨兵。退避前にシナリオが失敗した場合、
// After が空オブジェクトで上書きして既存 custom_texts を消し飛ばすのを防ぐ。
let snapshotCaptured = false;
let testValue = '';
// ルート移設（#413）で店舗管理画面 URL に storeId が必須になった。ログイン着地先の
// /store/{id}/dashboard から読み取り、以降の管理画面遷移で使う（seed id をハードコードしない）。
let storeId = '';

Given('店舗 {string} の管理画面にログインしている', async ({ page }, _store: string) => {
  // 統一ログイン（/platform/login）は platform ドメイン（kizuna.test）で提供され、店長ロールの
  // セッションも platform ドメイン上のまま /store/* を操作する（#324、platform-login.steps.ts と同じ）。
  // token cookie はオリジン別に分離されるため、以降の管理画面遷移も PLATFORM_URL を用いる。
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel('メールアドレス', { exact: true }).fill(ADMIN_EMAIL);
  await page.getByLabel('パスワード', { exact: true }).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).toHaveURL(/\/store\/\d+\/dashboard\/?$/, { timeout: 15000 });
  storeId = new URL(page.url()).pathname.match(/\/store\/(\d+)/)?.[1] ?? '';
});

Given('店舗設定の現在値を退避する', async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  const config = await getStoreConfig(request, token);
  originalCustomTexts = (config.custom_texts as Record<string, string> | undefined) ?? null;
  snapshotCaptured = true;
});

When('店舗情報ページでアクセス補足を一意な検証値に変更して保存する', async ({ page }) => {
  testValue = `E2E設定保存-${Date.now()}`;
  // ログイン済みセッション（token cookie）は platform ドメインにあるため管理画面も PLATFORM_URL で開く。
  await page.goto(`${PLATFORM_URL}/store/${storeId}/settings/profile`);
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

// custom_texts を退避値へ復元する（テスト失敗・途中クラッシュでも実行）。
// ただし退避が成功していない場合は復元しない — 退避前の失敗で空オブジェクトを
// PUT すると既存 custom_texts を消し飛ばすため。ベストエフォート。
After({ tags: '@store-settings' }, async ({ request }) => {
  if (!snapshotCaptured) return;
  const token = await loginAsStoreAdmin(request);
  await setCustomTexts(request, token, originalCustomTexts ?? {}).catch(() => {});
  originalCustomTexts = null;
  snapshotCaptured = false;
  testValue = '';
});
