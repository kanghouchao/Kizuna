import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { APP_URL } from '../base-url';

const { When, Then } = createBdd();

const ROLES_URL = `${APP_URL}/admin/roles`;
const STAFF_URL = `${APP_URL}/admin/staff`;

// 作成するロール名はシナリオごとに一意化する（失敗した過去 run の残骸との重複＝strict モード違反を
// 避ける。staff-management.steps.ts の採番手法に倣う）。
let createdRoleName = '';

When('ロール管理画面を開く', async ({ page }) => {
  await page.goto(ROLES_URL, { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('link', { name: 'ロールを追加', exact: true })).toBeVisible();
});

When(
  '名称 {string}・権限 {string} でロールを追加する',
  async ({ page }, baseName: string, permissionLabel: string) => {
    createdRoleName = `${baseName}-${Date.now()}`;
    await page.getByRole('link', { name: 'ロールを追加', exact: true }).click();
    await expect(page).toHaveURL(/\/admin\/roles\/create\/?$/);

    await page.getByLabel('ロール名', { exact: true }).fill(createdRoleName);
    await page.getByRole('checkbox', { name: permissionLabel, exact: true }).check();
    await page.getByRole('button', { name: '保存する', exact: true }).click();
    // 成功時のみ一覧へ戻る（失敗時はエラートーストのまま画面に留まる）。
    await expect(page).toHaveURL(/\/admin\/roles\/?$/, { timeout: 15000 });
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

Then('従業員追加のロール選択に {string} が出る', async ({ page }, _label: string) => {
  // ロール管理で作ったロールが、授与側（従業員追加）の選択肢として実際に届くことまで見る。
  await page.goto(STAFF_URL, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: '従業員を追加', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '従業員を追加' });
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
  '{string} の削除ボタンは無効で、編集画面では名称だけが固定されている',
  async ({ page }, systemRoleName: string) => {
    // 既定ロールは権限を編集できる。改名と削除はサーバ側で拒否されるため、導線の側で塞いである。
    const row = page.getByRole('row', { name: new RegExp(systemRoleName) });
    await expect(row).toBeVisible({ timeout: 15000 });
    await expect(row.getByRole('button', { name: '削除', exact: true })).toBeDisabled();
    await row.getByRole('link', { name: '編集', exact: true }).click();
    await expect(page).toHaveURL(/\/admin\/roles\/\d+\/edit\/?$/);
    await expect(page.getByRole('heading', { name: `${systemRoleName} を編集` })).toBeVisible();
    await expect(page.getByLabel('ロール名', { exact: true })).toBeDisabled();
    await page.getByRole('link', { name: 'キャンセル', exact: true }).click();
    await expect(page).toHaveURL(/\/admin\/roles\/?$/);
  }
);
