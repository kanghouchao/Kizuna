import { randomUUID } from 'node:crypto';
import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import { adjustCustomerPoints, createCustomer, linkMemberToCustomer, loginAsStoreAdmin, loginViaUiAndEnterStore, registerMember } from './store-api';

const { Given, When, Then } = createBdd();
let marker = '';
let customerId = '';
let storeId = '';
const longLandmark = '駅の北口から郵便局を通り過ぎて右側の建物。'.repeat(10);

Given('一覧検証用の未関連・零残高・残高ありの顧客がいる', async ({ page, request }) => {
  const token = await loginAsStoreAdmin(request);
  marker = `一覧検証${Date.now()}`;
  customerId = await createCustomer(request, token, `${marker}未関連`);
  for (const [label, balance] of [['零', 0], ['多い', 123456]] as const) {
    const id = await createCustomer(request, token, `${marker}${label}`);
    const code = await registerMember(request, `${randomUUID()}@example.test`, randomUUID(), label);
    await linkMemberToCustomer(request, token, id, code);
    if (balance) {
      await adjustCustomerPoints(request, token, id, balance, '一覧検証');
    }
  }
  storeId = await loginViaUiAndEnterStore(page);
  await page.goto(`${PLATFORM_URL}/store/${storeId}/customers`);
  await page.getByPlaceholder('名前・電話・メール・LINE ID で検索...').fill(marker);
  await page.getByRole('button', { name: '検索', exact: true }).click();
  await expect(page.locator('tbody tr')).toHaveCount(3);
});

When('会員ポイントを両方向で並べ替える', async ({ page }) => {
  for (const [label, names] of [
    ['会員ポイント（少ない順）', ['零', '多い', '未関連']],
    ['会員ポイント（多い順）', ['多い', '零', '未関連']],
  ] as const) {
    await page.getByRole('combobox', { name: '並び順' }).click();
    await page.getByRole('option', { name: label, exact: true }).click();
    for (const [index, name] of names.entries()) {
      await expect(page.locator('tbody tr').nth(index)).toContainText(`${marker}${name}`);
    }
  }
});

Then('未関連は末尾になり来店なしと零残高を区別できる', async ({ page }) => {
  await expect(page.getByText('来店なし', { exact: true })).toHaveCount(3);
  await expect(page.getByText('0 pt', { exact: true })).toBeVisible();
  await expect(page.getByText('123,456 pt', { exact: true })).toBeVisible();
  await expect(page.locator('tbody tr').last().getByText('未関連', { exact: true })).toBeVisible();
});

Then('顧客一覧を狭幅と両テーマでキーボード操作でき取得失敗から回復できる', async ({ page }) => {
  for (const width of [390, 1280]) {
    await page.setViewportSize({ width, height: 900 });
    for (const theme of ['light', 'dark']) {
      await page.evaluate(value => document.documentElement.classList.toggle('dark', value === 'dark'), theme);
      const select = page.getByRole('combobox', { name: '並び順' });
      await select.focus();
      await page.keyboard.press('Enter');
      await expect(page.getByRole('listbox')).toBeVisible();
      await expect(page.locator('[data-slot=select-content]')).toHaveCSS('opacity', '1');
      await page.screenshot({ path: `test-results/customer-list-${theme}-${width}.png` });
      await page.keyboard.press('Escape');
      await expect(select).toBeFocused();
      await page.getByText('123,456 pt', { exact: true }).scrollIntoViewIfNeeded();
      await page.screenshot({ path: `test-results/customer-list-values-${theme}-${width}.png` });
    }
  }
  await page.route('**/api/store/customers?**', route => route.fulfill({ status: 500, json: { error: '一覧取得失敗' } }));
  await page.getByRole('button', { name: '検索', exact: true }).click();
  await expect(page.getByRole('alert').filter({ hasText: '顧客一覧の取得に失敗しました' })).toBeVisible();
  await expect(page.locator('tbody tr')).toHaveCount(0);
  await page.unroute('**/api/store/customers?**');
  await page.getByRole('button', { name: '再試行' }).click();
  await expect(page.locator('tbody tr')).toHaveCount(3);
});

When('顧客の長い目印を編集して保存する', async ({ page }) => {
  await page.goto(`${PLATFORM_URL}/store/${storeId}/customers/${customerId}/edit`);
  await page.getByRole('textbox', { name: '目印', exact: true }).fill(longLandmark);
  for (const width of [390, 1280]) {
    await page.setViewportSize({ width, height: 900 });
    for (const theme of ['light', 'dark']) {
      await page.evaluate(value => document.documentElement.classList.toggle('dark', value === 'dark'), theme);
      await page.getByRole('textbox', { name: '目印', exact: true }).focus();
      await page.screenshot({ path: `test-results/customer-landmark-${theme}-${width}.png` });
      await page.keyboard.press('Tab');
    }
  }
  await page.route(`**/api/store/customers/${customerId}`, async route => {
    if (route.request().method() === 'PUT') await route.fulfill({ status: 500, json: { error: '保存失敗' } });
    else await route.continue();
  });
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page.getByText('顧客情報の更新に失敗しました', { exact: true })).toBeVisible();
  await expect(page.getByRole('textbox', { name: '目印', exact: true })).toHaveValue(longLandmark);
  await page.unroute(`**/api/store/customers/${customerId}`);
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page).toHaveURL(/\/customers$/);
});

Then('目印を再表示して空欄へ更新できる', async ({ page }) => {
  const path = `${PLATFORM_URL}/store/${storeId}/customers/${customerId}/edit`;
  await page.goto(path);
  await expect(page.getByRole('textbox', { name: '目印', exact: true })).toHaveValue(longLandmark);
  await page.getByRole('textbox', { name: '目印', exact: true }).fill('');
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page).toHaveURL(/\/customers$/);
  await page.goto(path);
  await expect(page.getByRole('textbox', { name: '目印', exact: true })).toHaveValue('');
});
