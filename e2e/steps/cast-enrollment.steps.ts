import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import { createCast, loginAsStoreAdmin, loginViaUiAndEnterStore } from './store-api';

const { Given, When, Then } = createBdd();

Given('在籍操作用のキャスト編集画面を開く', async ({ page, request }) => {
  const storeId = await loginViaUiAndEnterStore(page);
  const token = await loginAsStoreAdmin(request);
  const id = await createCast(request, token, `在籍操作-${Date.now()}`);
  await page.goto(`${PLATFORM_URL}/store/${storeId}/casts/${id}/edit`);
  await expect(page.getByText('在籍状態: 在籍中', { exact: true })).toBeVisible();
});

When('在籍操作で停止して再開する', async ({ page }) => {
  await page.getByRole('button', { name: '停止する', exact: true }).click();
  await expect(page.getByText('在籍状態: 在籍停止', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '再開する', exact: true }).click();
  await expect(page.getByText('在籍状態: 在籍中', { exact: true })).toBeVisible();
});

When('在籍操作で退店を一度取り消してから確定する', async ({ page }) => {
  await page.getByRole('region', { name: '在籍操作' }).getByRole('button', { name: '退店する', exact: true }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'キャンセル', exact: true }).click();
  await expect(page.getByRole('alertdialog')).toBeHidden();
  await expect(page.getByText('在籍状態: 在籍中', { exact: true })).toBeVisible();
  await page.getByRole('region', { name: '在籍操作' }).getByRole('button', { name: '退店する', exact: true }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: '退店する', exact: true }).click();
});

Then('退店状態と入店からの在籍履歴が表示される', async ({ page }) => {
  await expect(page.getByText('在籍状態: 退店', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '退店する', exact: true })).toHaveCount(0);
  const history = page.getByRole('region', { name: '在籍履歴', exact: true });
  await expect(history.getByRole('listitem')).toHaveCount(4);
  await expect(history.getByText('入店（在籍中）', { exact: true })).toBeVisible();
  await expect(history.getByText('在籍中 → 退店', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText('在籍状態: 退店', { exact: true })).toBeVisible();
});
