import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';

const { When, Then } = createBdd();

const ROLES_URL = `${PLATFORM_URL}/platform/roles`;
const STAFF_URL = `${PLATFORM_URL}/platform/staff`;

// 作成するロール名はシナリオごとに一意化する（失敗した過去 run の残骸との重複＝strict モード違反を
// 避ける。staff-management.steps.ts の採番手法に倣う）。
let createdRoleName = '';

When('ロール管理画面を開く', async ({ page }) => {
  await page.goto(ROLES_URL, { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'ロールを追加', exact: true })).toBeVisible();
});

When(
  '名称 {string}・権限 {string} でロールを追加する',
  async ({ page }, baseName: string, permissionCode: string) => {
    createdRoleName = `${baseName}-${Date.now()}`;
    await page.getByRole('button', { name: 'ロールを追加', exact: true }).click();

    const dialog = page.getByRole('dialog', { name: 'ロールを追加' });
    await dialog.getByLabel('ロール名', { exact: true }).fill(createdRoleName);
    // 権限ラベルはバックエンドの権限コードをそのまま表示する（日本語名は持たない）。
    await dialog.getByRole('checkbox', { name: permissionCode, exact: true }).check();
    await dialog.getByRole('button', { name: '保存する', exact: true }).click();
    // 成功時のみモーダルが閉じる（失敗時はエラートーストのまま開いたまま）。
    await expect(dialog).toBeHidden({ timeout: 15000 });
  }
);

Then(
  'ロール一覧に {string} が {string} として表示される',
  async ({ page }, _label: string, kindLabel: string) => {
    // {string} は可読性のための表記。実際の照合は一意名（createdRoleName）で行う。
    const row = page.getByRole('row', { name: new RegExp(createdRoleName) });
    await expect(row).toBeVisible({ timeout: 15000 });
    await expect(row.getByText(kindLabel, { exact: true })).toBeVisible();
  }
);

Then('スタッフ追加のロール選択に {string} が出る', async ({ page }, _label: string) => {
  // ロール管理で作ったロールが、授与側（スタッフ追加）の選択肢として実際に届くことまで見る。
  await page.goto(STAFF_URL, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'スタッフを追加', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'スタッフを追加' });
  await expect(dialog.getByRole('checkbox', { name: createdRoleName, exact: true })).toBeVisible({
    timeout: 15000,
  });
  await dialog.getByRole('button', { name: 'キャンセル', exact: true }).click();
});

When('作成したロールを削除する', async ({ page }) => {
  await page.goto(ROLES_URL, { waitUntil: 'domcontentloaded' });
  const row = page.getByRole('row', { name: new RegExp(createdRoleName) });
  await expect(row).toBeVisible({ timeout: 15000 });
  await row.getByRole('button', { name: '削除', exact: true }).click();
  await page.getByRole('button', { name: '削除する', exact: true }).click();
});

Then('ロール一覧から {string} が消える', async ({ page }, _label: string) => {
  await expect(page.getByRole('row', { name: new RegExp(createdRoleName) })).toHaveCount(0, {
    timeout: 15000,
  });
  createdRoleName = '';
});

Then(
  '{string} の編集ボタンと削除ボタンが無効である',
  async ({ page }, systemRoleName: string) => {
    // 平台既定ロールは改名・権限変更・削除がサーバ側で拒否されるため、導線自体が無効化されている。
    const row = page.getByRole('row', { name: new RegExp(systemRoleName) });
    await expect(row).toBeVisible({ timeout: 15000 });
    await expect(row.getByRole('button', { name: '編集', exact: true })).toBeDisabled();
    await expect(row.getByRole('button', { name: '削除', exact: true })).toBeDisabled();
  }
);
