import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { APP_URL } from '../base-url';
import {
  STORE_HEADERS,
  createCast,
  issueCastInvitation,
  loginAsStoreAdmin,
  loginViaUiAndEnterStore,
  todayInTokyo,
} from './store-api';

const { Given, When, Then } = createBdd();

Given('在籍操作用のキャスト編集画面を開く', async ({ page, request }) => {
  await loginViaUiAndEnterStore(page);
  const token = await loginAsStoreAdmin(request);
  const id = await createCast(request, token, `在籍操作-${Date.now()}`);
  await page.goto(`${APP_URL}/store/casts/${id}/edit`);
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
  await page.locator('summary').filter({ hasText: /^在籍履歴$/ }).click();
  const history = page.getByRole('region', { name: '在籍履歴', exact: true });
  await expect(history.getByRole('listitem')).toHaveCount(4);
  await expect(history.getByText('入店（在籍中）', { exact: true })).toBeVisible();
  await expect(history.getByText('在籍中 → 退店', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText('在籍状態: 退店', { exact: true })).toBeVisible();
});

let invitedCastName: string;
let withdrawnInvitation: string;

Given('招待発行済みのキャスト編集画面を開く', async ({ page, request }) => {
  await loginViaUiAndEnterStore(page);
  const token = await loginAsStoreAdmin(request);
  invitedCastName = `招待不可-${Date.now()}`;
  const id = await createCast(request, token, invitedCastName);
  withdrawnInvitation = await issueCastInvitation(request, token, id);
  await page.goto(`${APP_URL}/store/casts/${id}/edit`);
  await expect(page.getByText('在籍状態: 在籍中', { exact: true })).toBeVisible();
});

Then('管理一覧は招待不可を表示し招待ページは受諾を案内しない', async ({ page }) => {
  await expect(page.getByText('在籍状態: 退店', { exact: true })).toBeVisible();
  await page.goto(`${APP_URL}/store/casts`);
  await page.getByPlaceholder('名前で検索...').fill(invitedCastName);
  await page.getByRole('button', { name: '検索', exact: true }).click();
  const row = page.getByRole('row').filter({ hasText: invitedCastName });
  await expect(row.getByText('招待不可', { exact: true })).toBeVisible();
  await expect(row.getByRole('button', { name: '招待を発行' })).toHaveCount(0);
  await expect(row.getByRole('button', { name: '再発行' })).toHaveCount(0);
  await page.context().clearCookies();
  await page.goto(`${APP_URL}/auth/invite#${encodeURIComponent(withdrawnInvitation)}`);
  await expect(page.getByText('この招待は利用できません。店舗の担当者にご確認ください。')).toBeVisible();
  await expect(page.getByRole('button', { name: '新規登録して受諾' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '既存アカウントでログイン' })).toHaveCount(0);
});

let startedOnCastName: string;
const FUTURE_STARTED_ON = '2099-04-01';

Given('キャスト新規登録画面を開く', async ({ page }) => {
  await loginViaUiAndEnterStore(page);
  await page.goto(`${APP_URL}/store/casts/create`);
  await expect(page.getByRole('heading', { name: '新規キャスト登録' })).toBeVisible();
});

Then('入店日の既定値は当日である', async ({ page }) => {
  await expect(page.getByLabel('入店日 *')).toHaveValue(todayInTokyo());
});

When('入店日を未来日にしてキャストを登録する', async ({ page }) => {
  startedOnCastName = `入店日-${Date.now()}`;
  await page.getByLabel('源氏名 *').fill(startedOnCastName);
  await page.getByLabel('入店日 *').fill(FUTURE_STARTED_ON);
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await page.waitForURL(/\/store\/casts$/);
});

Then('キャスト一覧に登録した入店日が表示される', async ({ page }) => {
  await page.getByPlaceholder('名前で検索...').fill(startedOnCastName);
  await page.getByRole('button', { name: '検索', exact: true }).click();
  const row = page.getByRole('row').filter({ hasText: startedOnCastName });
  await expect(row.getByText(FUTURE_STARTED_ON, { exact: true })).toBeVisible();
});

Then('入店日の無いキャスト作成は 400 で拒否される', async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  const response = await request.post('/api/casts', {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { name: `入店日なし-${Date.now()}` },
  });
  expect(response.status(), await response.text()).toBe(400);
});
