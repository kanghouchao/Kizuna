import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { createCustomer, loginAsStoreAdmin, loginViaUiAndEnterStore, STORE_HEADERS } from './store-api';
import { PLATFORM_URL } from '../base-url';

const { Given, When, Then } = createBdd();
let customerId = '';
let token = '';
const longEmail = `${'long.contact.'.repeat(4)}person+tag@EXAMPLE.COM`;

Given('連絡先検証用の顧客編集画面を開く', async ({ page, request }) => {
  token = await loginAsStoreAdmin(request);
  customerId = await createCustomer(request, token, `連絡先検証-${Date.now()}`);
  const storeId = await loginViaUiAndEnterStore(page);
  await page.goto(`${PLATFORM_URL}/store/${storeId}/customers/${customerId}/edit`);
  await expect(page.getByText('連絡先はありません', { exact: true })).toBeVisible();
});

When('電話とメールを追加して電話を優先にする', async ({ page }) => {
  await page.getByRole('button', { name: '連絡先を追加', exact: true }).click();
  let dialog = page.getByRole('dialog', { name: /^連絡先を(追加|編集)$/ });
  await dialog.getByLabel('連絡先の値').fill('090-1234-5678');
  await dialog.getByRole('button', { name: '保存する' }).click();
  await expect(dialog).toBeHidden();
  await page.getByRole('button', { name: '優先にする' }).click();
  await expect(page.getByRole('button', { name: '優先を解除' })).toBeVisible();
  await page.getByRole('button', { name: '連絡先を追加', exact: true }).click();
  dialog = page.getByRole('dialog', { name: /^連絡先を(追加|編集)$/ });
  await dialog.getByRole('combobox').click();
  await page.getByRole('option', { name: 'メール', exact: true }).click();
  await dialog.getByLabel('連絡先の値').fill(longEmail);
  await dialog.getByRole('button', { name: '保存する' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByText(longEmail.replace('EXAMPLE.COM', 'example.com'), { exact: true })).toBeVisible();
});

Then('狭幅と両テーマで長い連絡先を確認できる', async ({ page }) => {
  for (const theme of ['light', 'dark']) {
    await page.evaluate(value => { localStorage.setItem('theme', value); document.documentElement.classList.toggle('dark', value === 'dark'); }, theme);
    for (const width of [390, 1280]) {
      await page.setViewportSize({ width, height: 900 });
      const add = page.getByRole('button', { name: '連絡先を追加', exact: true });
      await add.scrollIntoViewIfNeeded();
      await add.focus();
      await page.keyboard.press('Enter');
      await expect(page.getByRole('dialog', { name: /^連絡先を(追加|編集)$/ })).toBeVisible();
      await page.keyboard.press('Escape');
      await expect(page.getByRole('dialog', { name: /^連絡先を(追加|編集)$/ })).toBeHidden();
      await expect(add).toBeFocused();
      await page.screenshot({ path: `test-results/customer-contacts-${theme}-${width}.png`, fullPage: true });
      if (width === 390) {
        const edit = page.getByRole('button', { name: '編集', exact: true }).first();
        await edit.scrollIntoViewIfNeeded();
        await expect(edit).toBeInViewport();
        await page.screenshot({ path: `test-results/customer-contacts-${theme}-390-right.png` });
        await add.scrollIntoViewIfNeeded();
      }
      await add.click();
      const dialog = page.getByRole('dialog', { name: /^連絡先を(追加|編集)$/ });
      await dialog.getByLabel('連絡先の値').fill('+12025550123');
      await dialog.getByRole('button', { name: '保存する' }).click();
      await expect(page.getByText('日本の有効な電話番号を入力してください', { exact: true })).toBeVisible();
      await expect(dialog.getByLabel('連絡先の値')).toHaveValue('+12025550123');
      const errorToast = page.locator('[data-slot=toast]').filter({ hasText: '日本の有効な電話番号を入力してください' });
      await expect(errorToast).toHaveCSS('opacity', '1');
      await page.screenshot({ path: `test-results/customer-contacts-error-${theme}-${width}.png` });
      await page.keyboard.press('Escape');
      await errorToast.locator('[data-slot=toast-close]').click();
    }
  }
  await page.setViewportSize({ width: 1280, height: 900 });
});

When('電話を編集して削除する', async ({ page }) => {
  await page.getByRole('button', { name: '編集', exact: true }).first().click();
  const dialog = page.getByRole('dialog', { name: /^連絡先を(追加|編集)$/ });
  await dialog.getByLabel('連絡先の値').fill('080-1234-5678');
  await dialog.getByRole('button', { name: '保存する' }).click();
  await expect(dialog).toBeHidden();
  await page.getByRole('button', { name: '削除', exact: true }).first().click();
  await page.getByRole('alertdialog').getByRole('button', { name: '削除する' }).click();
  await expect(page.getByRole('button', { name: '優先を解除' })).toHaveCount(0);
});

Then('連絡先の履歴が残り顧客の削除が拒否される', async ({ page, request }) => {
  await expect(page.getByText('変更後: 電話: +818012345678／指定なし／削除済み', { exact: false })).toBeVisible();
  const response = await request.delete(`/api/store/customers/${customerId}`, { headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` } });
  expect(response.status()).toBe(409);
  expect((await response.json()).error).toContain('履歴');
});

When('別の担当者が削除したメールを削除しようとする', async ({ page, request }) => {
  const headers = { ...STORE_HEADERS, Authorization: `Bearer ${token}` };
  const response = await request.get(`/api/store/customers/${customerId}/contacts`, { headers });
  expect(response.ok()).toBeTruthy();
  const contact = (await response.json()).content.find((row: { type: string }) => row.type === 'EMAIL');
  const deleted = await request.delete(`/api/store/customers/${customerId}/contacts/${contact.id}`, { headers });
  expect(deleted.status()).toBe(204);
  await page.getByRole('button', { name: '削除', exact: true }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: '削除する' }).click();
});

Then('古い連絡先が画面から消える', async ({ page }) => {
  await expect(page.getByText('連絡先はありません', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '削除', exact: true })).toHaveCount(0);
});

Given('新規顧客登録画面を開く', async ({ page }) => {
  const storeId = await loginViaUiAndEnterStore(page);
  await page.goto(`${PLATFORM_URL}/store/${storeId}/customers/create`);
});

When('海外の電話番号を連絡先に指定して顧客を保存する', async ({ page }) => {
  await page.getByLabel('名前 *', { exact: true }).fill(`連絡先修正-${Date.now()}`);
  await page.getByRole('button', { name: '連絡先を追加', exact: true }).click();
  await page.getByLabel('連絡先の値').fill('+12025550123');
  await page.getByRole('button', { name: '保存する', exact: true }).click();
});

Then('電話番号のエラー理由と入力が残り修正して登録できる', async ({ page }) => {
  await expect(page.getByText('日本の有効な電話番号を入力してください', { exact: true })).toBeVisible();
  await expect(page.getByLabel('連絡先の値')).toHaveValue('+12025550123');
  await page.getByLabel('連絡先の値').fill('09012345678');
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page).toHaveURL(/\/customers\/?$/);
});
