import { expect, test } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { loginViaUiAndEnterStore } from './store-api';

const { Given, When, Then } = createBdd();
let serviceName = '';

Given('店長がサービス設定を開いている', async ({ page }) => {
  await loginViaUiAndEnterStore(page);
  await page.getByRole('link', { name: 'サービス設定', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'サービス設定', exact: true })).toBeVisible();
  serviceName = `E2Eコース-${Date.now()}`;
});

When('コースの価格と固定報酬を保存する', async ({ page }) => {
  await page.getByRole('button', { name: '新規作成', exact: true }).click();
  await page.getByLabel('名称', { exact: true }).fill(serviceName);
  await page.getByLabel('所要時間（分）', { exact: true }).fill('60');
  await page.getByLabel('価格（円）', { exact: true }).fill('12000');
  await page.getByLabel('固定報酬（円）', { exact: true }).fill('7000');
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: serviceName })).toContainText('12,000 円');
});

When('コースの価格を改定する', async ({ page }) => {
  await page.getByRole('row').filter({ hasText: serviceName }).getByRole('button', { name: '編集', exact: true }).click();
  await page.getByLabel('価格（円）', { exact: true }).fill('15000');
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: serviceName })).toContainText('15,000 円');
});

When('コースを確認して削除する', async ({ page }) => {
  await page.getByRole('row').filter({ hasText: serviceName }).getByRole('button', { name: '削除', exact: true }).click();
  await expect(page.getByRole('alertdialog')).toContainText(serviceName);
  await page.getByRole('button', { name: '削除する', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: serviceName })).toHaveCount(0);
});

Then('削除済み一覧から変更前後と削除の履歴が読める', async ({ page }) => {
  await page.getByRole('combobox', { name: '状態で絞り込み' }).click();
  await page.getByRole('option', { name: '削除済み', exact: true }).click();
  await page.getByRole('button', { name: '絞り込む', exact: true }).click();
  await page.getByRole('row').filter({ hasText: serviceName }).getByRole('button', { name: '履歴', exact: true }).click();
  const history = page.getByRole('dialog', { name: 'サービス変更履歴', exact: true });
  await expect(history.getByRole('heading', { name: '版本 3 · 削除' })).toBeVisible();
  await expect(history.getByRole('heading', { name: '版本 2 · 変更' })).toBeVisible();
  await expect(history.getByRole('heading', { name: '版本 1 · 作成' })).toBeVisible();
  await expect(history).toContainText('12,000 円');
  await expect(history).toContainText('15,000 円');
  await expect(history).toContainText('操作者 ID:');
  await page.screenshot({ animations: 'disabled', path: test.info().outputPath('services-history-light.png') });
  await page.emulateMedia({ colorScheme: 'dark' });
  await expect(page.locator('html')).toHaveClass(/dark/);
  await page.screenshot({ animations: 'disabled', path: test.info().outputPath('services-history-dark.png') });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ animations: 'disabled', path: test.info().outputPath('services-history-narrow.png') });
});
