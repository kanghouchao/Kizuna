import { expect, type Page, type Request } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { loginViaUiAndEnterStore } from './store-api';
import { APP_URL } from '../base-url';

const { Given, When, Then } = createBdd();

// 共有の店舗に他のシナリオの受注が残るので、行ではなく URL・入力欄・作業キューの要求で条件を確かめる
let keyword = '';
// 直前の操作で起きた作業キューの要求の query。手順名の「取得している・取り直す」はこれで確かめる
let lastQuery = new URLSearchParams();

const customerInput = (page: Page) => page.getByLabel('お客様名', { exact: true });

const isWorkQueue = (request: Request) => new URL(request.url()).pathname.endsWith('/orders/work-queue');

/** 操作で起きた作業キューの要求を待ち、その query を返す。 */
async function workQueueQueryAfter(page: Page, action: () => Promise<unknown>) {
  const [request] = await Promise.all([page.waitForRequest(isWorkQueue), action()]);
  return new URL(request.url()).searchParams;
}

Given('店長が受注一覧を開いている', async ({ page }) => {
  keyword = `履歴${Date.now()}`;
  await loginViaUiAndEnterStore(page);
  await page.goto(`${APP_URL}/store/orders`);
  await expect(customerInput(page)).toBeVisible({ timeout: 15000 });
});

When('受注一覧をお客様名で検索する', async ({ page }) => {
  await customerInput(page).fill(keyword);
  const query = await workQueueQueryAfter(page, () =>
    page.getByRole('button', { name: '検索', exact: true }).click(),
  );
  expect(query.get('customer_name')).toBe(keyword);
});

When('受注一覧の並びを降順に変える', async ({ page }) => {
  lastQuery = await workQueueQueryAfter(page, () =>
    page.getByRole('button', { name: /昇順（押すと降順）/ }).click(),
  );
  await expect(page.getByRole('button', { name: /降順（押すと昇順）/ })).toBeVisible();
});

Then('URL に検索語と降順が載り、その条件で作業キューを取得している', async ({ page }) => {
  const url = new URL(page.url());
  expect(url.searchParams.get('customer_name')).toBe(keyword);
  expect(url.searchParams.get('desc')).toBe('true');
  expect(lastQuery.get('customer_name')).toBe(keyword);
  expect(lastQuery.get('desc')).toBe('true');
});

When('ブラウザで戻る', async ({ page }) => {
  lastQuery = await workQueueQueryAfter(page, () => page.goBack());
});

Then('URL と入力欄は検索語だけになり、昇順で作業キューを取り直す', async ({ page }) => {
  await expect(page).toHaveURL(url => url.searchParams.get('desc') === null);
  expect(new URL(page.url()).searchParams.get('customer_name')).toBe(keyword);
  await expect(customerInput(page)).toHaveValue(keyword);
  await expect(page.getByRole('button', { name: /昇順（押すと降順）/ })).toBeVisible();
  expect(lastQuery.get('customer_name')).toBe(keyword);
  expect(lastQuery.get('desc')).not.toBe('true');
});

Then('URL と入力欄は条件なしに戻る', async ({ page }) => {
  await expect(page).toHaveURL(url => url.pathname.endsWith('/store/orders') && url.search === '');
  await expect(customerInput(page)).toHaveValue('');
  expect(lastQuery.get('customer_name')).toBeNull();
});

When('ブラウザで進む', async ({ page }) => {
  const query = await workQueueQueryAfter(page, () => page.goForward());
  expect(query.get('customer_name')).toBe(keyword);
  expect(query.get('desc')).not.toBe('true');
  await expect(customerInput(page)).toHaveValue(keyword);
  expect(new URL(page.url()).searchParams.get('desc')).toBeNull();
});

When('受注一覧を再読込する', async ({ page }) => {
  // 再読込はセッションの復元から始まるので、作業キューの要求を待ってから確かめる
  const query = await workQueueQueryAfter(page, () => page.reload());
  expect(query.get('customer_name')).toBe(keyword);
});

Then('入力欄に検索語が残り、その条件で作業キューを取得している', async ({ page }) => {
  await expect(customerInput(page)).toHaveValue(keyword);
  expect(new URL(page.url()).searchParams.get('customer_name')).toBe(keyword);
});
