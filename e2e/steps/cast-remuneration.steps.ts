import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import { acceptCastInvitation, createAgreedOrder, completeAgreedOrder, createCast, createCourse, invalidateOrder, issueCastInvitation, loginAsStoreAdmin, loginPlatformUser, withdrawCast, STORE1_ID } from './store-api';

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
  ownToken = await loginPlatformUser(request, email, password);
});

When('退店した本人がログインして報酬明細を開く', async ({ page }) => {
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel('メールアドレス').fill(email);
  await page.getByLabel('パスワード', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).toHaveURL(/\/cast\/schedule/);
  await page.getByRole('link', { name: '報酬明細', exact: true }).click();
});

Then('本人の報酬明細から原条件と無効化履歴を確認できる', async ({ page }) => {
  await expect(page.getByRole('heading', { name: '報酬明細', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /・退店/ })).toBeVisible();
  await expect(page.getByRole('button', { name: '詳細を確認' })).toHaveCount(1);
  await page.getByRole('button', { name: '詳細を確認' }).click();
  await expect(page.getByText('無効化（有効報酬 0 円）')).toBeVisible();
  await expect(page.getByText('顧客費用: 12,000 円 / 固定報酬: 7,000 円')).toBeVisible();
  await page.getByRole('button', { name: '変更履歴を確認' }).click();
  await expect(page.getByText('未提供のため無効化')).toBeVisible();
  await expect(page.getByText('約定報酬: 7,000 円 / 発生済み報酬: 7,000 円')).toBeVisible();
  await expect(page.getByText('約定報酬: 7,000 円 / 発生済み報酬: 0 円')).toBeVisible();
  await expect(page.getByText(privateName)).toHaveCount(0);
});

Then('顧客個人情報と他人の受注は本人へ返されない', async ({ request }) => {
  const headers = { Authorization: `Bearer ${ownToken}` };
  const list = await request.get('/api/platform/me/remunerations', { headers });
  expect(list.ok()).toBeTruthy();
  expect((await list.json()).content.map((row: { order_id: string }) => row.order_id)).toEqual([ownId]);
  const detail = await request.get(`/api/platform/me/remunerations/${ownId}`, { headers });
  expect(detail.ok()).toBeTruthy();
  expect(await detail.text()).not.toMatch(/customer|contact|remarks|報酬画面に出してはいけない顧客/);
  for (const suffix of ['', '/changes']) {
    const denied = await request.get(`/api/platform/me/remunerations/${otherId}${suffix}`, { headers });
    expect(denied.status()).toBe(404);
  }
});
