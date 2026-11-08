import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { APP_URL } from '../base-url';
import { acceptCastInvitation, createAgreedOrder, completeAgreedOrder, createCast, createCourse, invalidateOrder, issueCastInvitation, loginAsStoreAdmin, loginUser, withdrawCast, STORE1_ID, openLoginForm } from './store-api';

const { Given, When, Then } = createBdd();
let email = '';
let password = '';
let ownId = '';
let otherId = '';
let ownToken = '';
const privateName = '報酬画面に出してはいけない顧客';

Given('本人と他人の受注を作成し本人の完了受注を無効化して退店する', async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  const suffix = Date.now().toString();
  email = `remuneration-${suffix}@kizuna.test`;
  password = `Cast-${suffix}-pass`;
  const castId = await createCast(request, token, `報酬本人-${suffix}`);
  const invitation = await issueCastInvitation(request, token, castId);
  await acceptCastInvitation(request, invitation, email, password, '報酬本人');
  const courseId = await createCourse(request, token, `報酬コース-${suffix}`, STORE1_ID);
  ownId = await createAgreedOrder(request, token, castId, courseId, privateName);
  await completeAgreedOrder(request, token, ownId);
  await invalidateOrder(request, token, ownId, '未提供のため無効化');
  const otherCast = await createCast(request, token, `報酬他人-${suffix}`);
  otherId = await createAgreedOrder(request, token, otherCast, courseId, '他人の顧客');
  await completeAgreedOrder(request, token, otherId);
  await withdrawCast(request, token, castId);
  ownToken = await loginUser(request, email, password);
});

When('退店した本人がログインして報酬明細を開く', async ({ page }) => {
  await openLoginForm(page);
  await page.getByLabel('メールアドレス').fill(email);
  await page.getByLabel('パスワード', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).toHaveURL(/\/cast\/?$/);
  await page.getByRole('navigation', { name: 'メニュー' }).getByRole('link', { name: '報酬', exact: true }).click();
});

Then('本人の報酬明細から原条件と無効化履歴を確認できる', async ({ page }) => {
  await expect(page.getByRole('heading', { level: 1, name: '報酬', exact: true })).toBeVisible();
  // 退店後も店舗の月次明細から今月の累計を取れる（失敗表示ではなく金額が出る）
  await expect(page.getByRole('region', { name: '今月の累計' })).toContainText(/¥[\d,]+/);
  const rows = page.getByRole('list', { name: '報酬明細' }).getByRole('link');
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText('無効化');
  await rows.first().click();
  await expect(page.getByRole('heading', { level: 1, name: '報酬明細', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: '戻る' })).toBeVisible();
  const summary = page.getByRole('region', { name: '概要' });
  await expect(summary).toContainText('無効化');
  await expect(summary).not.toContainText(ownId);
  const item = page.getByRole('region', { name: /^報酬コース-/ });
  await expect(item.getByRole('definition').filter({ hasText: '¥12,000' })).toBeVisible();
  await expect(item.getByRole('definition').filter({ hasText: '¥7,000' })).toBeVisible();
  const change = page.getByRole('region', { name: '変更履歴' }).getByRole('article', { name: /無効化/ });
  await expect(change).toContainText('未提供のため無効化');
  await expect(change).toContainText('¥7,000 → ¥0');
  await expect(page.getByText(privateName)).toHaveCount(0);
});

Then('顧客個人情報と他人の受注は本人へ返されない', async ({ request }) => {
  const headers = { Authorization: `Bearer ${ownToken}` };
  const list = await request.get('/api/me/remunerations', { headers });
  expect(list.ok()).toBeTruthy();
  expect((await list.json()).content.map((row: { order_id: string }) => row.order_id)).toEqual([ownId]);
  const detail = await request.get(`/api/me/remunerations/${ownId}`, { headers });
  expect(detail.ok()).toBeTruthy();
  expect(await detail.text()).not.toMatch(/customer|contact|remarks|報酬画面に出してはいけない顧客/);
  for (const suffix of ['', '/changes']) {
    const denied = await request.get(`/api/me/remunerations/${otherId}${suffix}`, { headers });
    expect(denied.status()).toBe(404);
  }
});
