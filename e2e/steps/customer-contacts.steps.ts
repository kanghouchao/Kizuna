import { expect, type Page } from '@playwright/test';
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

async function changeBusinessPermission(page: Page, status: string, index = 0) {
  await page.getByRole('button', { name: '業務連絡の可否を変更', exact: true }).nth(index).click();
  const dialog = page.getByRole('dialog', { name: '業務連絡の可否を変更' });
  await dialog.getByRole('combobox').click();
  await page.getByRole('option', { name: status, exact: true }).click();
  await dialog.getByLabel('出所', { exact: true }).fill('電話で確認');
  await dialog.getByLabel('根拠', { exact: true }).fill('本人から用途を確認して回答を取得');
  await dialog.getByRole('button', { name: '可否を保存', exact: true }).click();
  await expect(dialog).toBeHidden();
}

When('同じ電話を二件追加して業務連絡を許可する', async ({ page }) => {
  for (let i = 0; i < 2; i++) {
    await page.getByRole('button', { name: '連絡先を追加', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: '連絡先を追加' });
    await dialog.getByLabel('連絡先の値').fill('090-1234-5678');
    await dialog.getByRole('button', { name: '保存する' }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByRole('button', { name: '業務連絡の可否を変更', exact: true })).toHaveCount(i + 1);
    await changeBusinessPermission(page, '許可', i);
  }
  await expect(page.getByText('業務連絡: 許可／共同制約: 許可（連絡可）', { exact: true })).toHaveCount(2);
});

When('一件の業務連絡を拒否して削除する', async ({ page }) => {
  await changeBusinessPermission(page, '拒否');
  await expect(page.getByText('業務連絡: 許可／共同制約: 拒否（連絡不可）', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '削除', exact: true }).first().click();
  await page.getByRole('alertdialog').getByRole('button', { name: '削除する' }).click();
});

Then('残る電話は業務連絡の拒否を引き継ぎ履歴に由来が残る', async ({ page }) => {
  await expect(page.getByText('業務連絡: 拒否／共同制約: 拒否（連絡不可）', { exact: true })).toBeVisible();
  await expect(page.getByText('制約の継承（新たな同意ではありません）', { exact: false })).toBeVisible();
  await expect(page.getByText(/^除去元: .*／引継先: /)).toBeVisible();
});

When('残る電話の業務連絡を根拠付きで許可する', async ({ page }) => {
  await changeBusinessPermission(page, '許可');
});

Then('業務連絡だけが可能になり販促は未確認のままになる', async ({ page }) => {
  await expect(page.getByText('業務連絡: 許可／共同制約: 許可（連絡可）', { exact: true })).toBeVisible();
  await expect(page.getByText('販促連絡: 未確認／共同制約: 未確認（連絡不可）', { exact: true })).toBeVisible();
});

Then('可否変更画面を狭幅と両テーマでキーボード操作できる', async ({ page }) => {
  await expect(page.locator('[data-slot=toast]')).toHaveCount(0, { timeout: 15000 });
  for (const theme of ['light', 'dark']) {
    await page.evaluate(value => { localStorage.setItem('theme', value); document.documentElement.classList.toggle('dark', value === 'dark'); }, theme);
    for (const width of [390, 1280]) {
      await page.setViewportSize({ width, height: 900 });
      const trigger = page.getByRole('button', { name: '業務連絡の可否を変更', exact: true });
      await trigger.scrollIntoViewIfNeeded();
      await trigger.focus();
      await page.keyboard.press('Enter');
      const dialog = page.getByRole('dialog', { name: '業務連絡の可否を変更' });
      await expect(dialog).toBeVisible();
      await dialog.getByRole('button', { name: '可否を保存' }).click();
      await expect(dialog.getByText('出所を入力してください')).toBeVisible();
      await expect(dialog.getByLabel('出所', { exact: true })).toBeFocused();
      await dialog.getByLabel('出所', { exact: true }).fill('取得元'.repeat(60));
      await expect(dialog.getByText('出所を入力してください')).toBeHidden();
      await dialog.getByLabel('出所', { exact: true }).press('Tab');
      await expect(dialog.getByLabel('根拠', { exact: true })).toBeFocused();
      await dialog.getByLabel('根拠', { exact: true }).fill('長い根拠の確認。'.repeat(120));
      await expect(dialog.getByRole('button', { name: '可否を保存' })).toBeInViewport();
      await expect(dialog.getByRole('heading', { name: '業務連絡の可否を変更' })).toBeInViewport();
      await page.screenshot({ path: `test-results/contact-permission-${theme}-${width}.png` });
      await page.keyboard.press('Escape');
      await expect(dialog).toBeHidden();
      await expect(trigger).toBeFocused();
    }
  }
});
