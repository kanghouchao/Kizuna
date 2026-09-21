import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { addCustomerContact, createCustomer, loginAsStoreAdmin, loginViaUiAndEnterStore } from './store-api';
import { PLATFORM_URL } from '../base-url';

const { Given, When, Then } = createBdd();
let storeId = '';
let value = '';
let names: string[] = [];

Given('非優先連絡先を共有する顧客が同じ店舗にいる', async ({ page, request }) => {
  const token = await loginAsStoreAdmin(request);
  const marker = Date.now();
  value = `${'long.contact.'.repeat(4)}${marker}@example.com`;
  names = [`探索甲${marker}`, `探索乙${marker}`];
  for (const name of names) {
    const id = await createCustomer(request, token, name);
    await addCustomerContact(request, token, id, 'EMAIL', value);
    await addCustomerContact(request, token, id, 'LINE', value);
  }
  storeId = await loginViaUiAndEnterStore(page);
});

When('顧客一覧で非優先メールを検索する', async ({ page }) => {
  await page.goto(`${PLATFORM_URL}/store/${storeId}/customers`);
  await page.getByPlaceholder('名前・電話・メール・LINE ID で検索...').fill(value);
  await page.getByRole('button', { name: '検索', exact: true }).click();
});

Then('一致したメールと顧客名を確認できる', async ({ page }) => {
  for (const name of names) await expect(page.getByRole('row').filter({ hasText: name })).toContainText(`一致: メール ${value}`);
  for (const theme of ['light', 'dark']) {
    await page.evaluate(t => { localStorage.setItem('theme', t); document.documentElement.classList.toggle('dark', t === 'dark'); }, theme);
    for (const width of [390, 1280]) {
      await page.setViewportSize({ width, height: 900 });
      const match = page.getByText(`一致: メール ${value}`, { exact: true }).first();
      await match.scrollIntoViewIfNeeded();
      await expect(match).toBeInViewport();
      expect(await match.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
      await page.screenshot({ path: `test-results/customer-list-matches-${theme}-${width}.png`, fullPage: true });
    }
  }
});

When('重複候補で共有連絡先を検索する', async ({ page }) => {
  await page.goto(`${PLATFORM_URL}/store/${storeId}/customers/duplicates`);
  await page.getByLabel('連絡先で検索').fill(value);
  await page.getByRole('button', { name: '検索', exact: true }).click();
});

Then('メールと LINE の一致を区別して明示的に比較できる', async ({ page }) => {
  await expect(page.getByText(value, { exact: true })).toHaveCount(2);
  await expect(page.getByText('メール', { exact: true })).toBeVisible();
  await expect(page.getByText('LINE ID', { exact: true })).toBeVisible();
  for (const name of names) await page.getByRole('checkbox', { name: `${name} を見比べる` }).first().click();
  await expect(page.getByRole('table', { name: '2 行の比較' })).toHaveCount(1);
  await expect(page.getByRole('button', { name: '統合する', exact: true })).toBeDisabled();
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

Then('候補を狭幅と両テーマでキーボード操作できる', async ({ page }) => {
  for (const theme of ['light', 'dark']) {
    await page.evaluate(t => { localStorage.setItem('theme', t); document.documentElement.classList.toggle('dark', t === 'dark'); }, theme);
    for (const width of [390, 1280]) {
      await page.setViewportSize({ width, height: 900 });
      const search = page.getByLabel('連絡先で検索');
      await search.scrollIntoViewIfNeeded();
      await search.focus();
      await page.keyboard.press('Enter');
      await expect(page.getByText(value, { exact: true })).toHaveCount(2);
      await expect(page.getByRole('table', { name: '2 行の比較' })).toHaveCount(0);
      const checkbox = page.getByRole('checkbox', { name: `${names[0]} を見比べる` }).first();
      await checkbox.scrollIntoViewIfNeeded();
      await checkbox.focus();
      await page.keyboard.press('Space');
      await expect(checkbox).toBeChecked();
      await page.screenshot({ path: `test-results/customer-search-${theme}-${width}.png`, fullPage: true });
      if (width === 390) {
        const submit = page.getByRole('button', { name: '検索', exact: true });
        await submit.scrollIntoViewIfNeeded();
        await expect(submit).toBeInViewport();
        await page.screenshot({ path: `test-results/customer-search-${theme}-390-right.png` });
      }
    }
  }
});

Then('候補取得の失敗から再試行できる', async ({ page }) => {
  const route = '**/api/store/customers/duplicates?*';
  await page.route(route, r => r.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ error: '取得に失敗しました' }) }));
  await page.getByRole('button', { name: '検索', exact: true }).click();
  await expect(page.getByRole('main').getByRole('alert')).toContainText('重複候補の取得に失敗しました');
  await expect(page.getByText(value, { exact: true })).toHaveCount(0);
  await page.unroute(route);
  await page.getByRole('button', { name: '再試行' }).click();
  await expect(page.getByText(value, { exact: true })).toHaveCount(2);
});
