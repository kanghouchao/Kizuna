import { expect, Page } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { APP_URL } from '../base-url';
import { getStoreConfig, loginAsStoreAdmin, loginViaUiAndEnterStore, setAccessNote, storeUrl, STORE1_ID } from './store-api';

const { Given, When, Then, After } = createBdd();

const ACCESS_NOTE_LABEL = 'アクセス補足（店舗情報ページに表示）';
const accessNoteTextarea = (page: Page) => page.getByLabel(ACCESS_NOTE_LABEL, { exact: true });

let originalAccessNote = '';
// 退避ステップが実際に成功したかの哨兵。退避前にシナリオが失敗した場合、
// After が空文字で上書きして既存のアクセス補足を消し飛ばすのを防ぐ。
let snapshotCaptured = false;
let testValue = '';

Given('店舗 {string} の管理画面にログインしている', async ({ page }, _store: string) => {
  // 統一ログイン（/auth/login）は アプリドメイン（kizuna.test）で提供され、店舗業務画面も
  // アプリドメイン上で操作する。リフレッシュ cookie はホスト限定のため、以降の遷移も APP_URL を用いる。
  await loginViaUiAndEnterStore(page);
});

Given('店舗設定の現在値を退避する', async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  const config = await getStoreConfig(request, token);
  originalAccessNote = (config.access_note as string | null | undefined) ?? '';
  snapshotCaptured = true;
});

When('店舗情報ページでアクセス補足を一意な検証値に変更して保存する', async ({ page }) => {
  testValue = `E2E設定保存-${Date.now()}`;
  // ログイン済みセッション（リフレッシュ cookie）はアプリドメインにあるため管理画面も APP_URL で開く。
  await page.goto(storeUrl(STORE1_ID, `/settings/profile`));
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

// アクセス補足を退避値へ復元する（テスト失敗・途中クラッシュでも実行）。
// ただし退避が成功していない場合は復元しない — 退避前の失敗で空文字を
// PUT すると既存のアクセス補足を消し飛ばすため。ベストエフォート。
After({ tags: '@store-settings' }, async ({ request }) => {
  if (!snapshotCaptured) return;
  const token = await loginAsStoreAdmin(request);
  await setAccessNote(request, token, originalAccessNote).catch(() => {});
  originalAccessNote = '';
  snapshotCaptured = false;
  testValue = '';
});
