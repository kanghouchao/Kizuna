import { expect, type Page } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import { ADMIN_PASSWORD, STORE_HEADERS, createPermissionRole, createPlatformStaffFixture, loginPlatformUser, loginViaUiAndEnterStore } from './store-api';
const { Given, When, Then } = createBdd();
let email = '';
let password = '';
let token = '';
let storeId = '';
let reviewId = '';
let receipt: Record<string, unknown>;
const headers = () => ({ ...STORE_HEADERS, Authorization: `Bearer ${token}` });

Given('既定権限を持たない口コミ担当者が用意されている', async ({ page, request }) => {
  storeId = await loginViaUiAndEnterStore(page);
  const admin = await loginPlatformUser(request, 'admin@kizuna.test', ADMIN_PASSWORD);
  expect((await request.get('/api/store/reviews', { headers: { ...STORE_HEADERS, Authorization: `Bearer ${admin}` } })).status()).toBe(403);
  const suffix = Date.now().toString();
  email = `review-${suffix}@kizuna.test`;
  password = `Review-${suffix}-fixture`;
  const role = await createPermissionRole(request, admin, `口コミ担当-${suffix}`, ['REVIEW_VIEW', 'REVIEW_MANAGE', 'REVIEW_MODERATE', 'PLATFORM_MENU_VIEW', 'STORE_MENU_VIEW']);
  await createPlatformStaffFixture(request, admin, email, password, [role]);
  token = await loginPlatformUser(request, email, password);
  expect((await request.get('/api/store/reviews', { headers: { Authorization: `Bearer ${token}` } })).status()).toBe(403);
  expect((await request.get('/api/store/reviews', { headers: STORE_HEADERS })).status()).toBe(401);
});

async function confirm(page: Page) {
  const confirmation = page.getByRole('alertdialog');
  await expect(confirmation).toBeVisible();
  await confirmation.getByRole('button', { name: '確定する', exact: true }).click();
  await expect(confirmation).toBeHidden();
}

When('担当者が口コミを受付し公開許可なしで内部承認する', async ({ page, request, $testInfo }) => {
  await page.context().clearCookies();
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel('メールアドレス', { exact: true }).fill(email);
  await page.getByLabel('パスワード', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).toHaveURL(/\/platform\/dashboard/, { timeout: 15000 });
  await page.goto(`${PLATFORM_URL}/store/${storeId}/reviews`);
  await page.getByRole('button', { name: '口コミを受付', exact: true }).click();
  const intake = page.getByRole('dialog', { name: '口コミを受付', exact: true });
  await intake.getByLabel('口コミ本文').fill('  受付時の原文をそのまま保存します。\n二行目の長い本文です。  ');
  await intake.getByLabel('表示名（空欄は匿名）').fill('表示名');
  await intake.getByLabel('受け取った日時（この端末の時刻）').fill('2026-01-01T09:00');
  const [created] = await Promise.all([
    page.waitForResponse(response => response.url().endsWith('/store/reviews') && response.request().method() === 'POST'),
    intake.getByRole('button', { name: '受付を記録', exact: true }).click(),
  ]);
  expect(created.status()).toBe(201);
  receipt = created.request().postDataJSON();
  const result = await created.json();
  reviewId = result.review.id;
  expect(result.review.intake_source).toBe('STAFF_RECORDED');
  expect(result.review.permission_status).toBe('NOT_GRANTED');
  expect(result.review).not.toHaveProperty('verified_customer');
  expect(result.review.publication_connection).toBe('NOT_CONFIGURED');
  const detail = page.getByRole('dialog', { name: '口コミの内容・履歴', exact: true });
  await expect(detail.getByText('スタッフによる記録', { exact: true })).toBeVisible();
  await detail.getByRole('button', { name: '内部承認', exact: true }).click();
  const decision = page.getByRole('dialog', { name: '内部承認', exact: true });
  await expect(decision.getByText('内部承認のみ。公開には別途許可と公開設定が必要です。')).toBeVisible();
  await decision.getByLabel('判断・変更の理由').fill('掲載判断とは別に内容を確認');
  await decision.getByLabel('判断・変更の理由').press('Tab');
  await expect(decision.getByRole('button', { name: '確認へ' })).toBeFocused();
  await page.keyboard.press('Enter');
  await confirm(page);
  await expect(detail.getByText('内部承認済み', { exact: true })).toBeVisible();
  await expect(detail.getByText('公開許可未取得', { exact: true })).toBeVisible();
  await expect(detail.getByText('公開連携は未設定', { exact: true })).toBeVisible();
  const current = await request.get(`/api/store/reviews/${reviewId}`, { headers: headers() });
  expect(current.status()).toBe(200);
  expect((await current.json()).publication_eligible).toBe(false);
  expect((await request.post(`/api/store/reviews/${reviewId}/withdrawals`, { headers: headers(), data: { version: 1.5, reason: '不正な版', dedupe_key: 'fractional-version' } })).status()).toBe(400);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await detail.evaluate(element => element.scrollWidth <= element.clientWidth)).toBeTruthy();
  await expect(page.getByText('口コミの操作を記録しました', { exact: true })).toHaveCount(0, { timeout: 10000 });
  await page.screenshot({ path: $testInfo.outputPath('review-light-mobile.png'), fullPage: true, animations: 'disabled' });
  await page.evaluate(() => document.documentElement.classList.add('dark'));
  await page.screenshot({ path: $testInfo.outputPath('review-dark-mobile.png'), fullPage: true, animations: 'disabled' });
  await page.setViewportSize({ width: 1280, height: 900 });
});

When('口コミの公開許可を記録して撤回する', async ({ page, request }) => {
  const detail = page.getByRole('dialog', { name: '口コミの内容・履歴', exact: true });
  await detail.getByRole('button', { name: '公開許可を記録', exact: true }).click();
  const grant = page.getByRole('dialog', { name: '公開許可を記録', exact: true });
  await grant.getByLabel('許可を受けた日時（この端末の時刻）').fill('2026-01-02T09:00');
  await grant.getByLabel('許可を確認した根拠').fill('書面で対象文面と表示名の自店舗サイト掲載を確認');
  await grant.getByRole('button', { name: '確認へ', exact: true }).click();
  await confirm(page);
  await expect(detail.getByText('公開許可あり', { exact: true })).toBeVisible();
  const allowed = await request.get(`/api/store/reviews/${reviewId}`, { headers: headers() });
  expect((await allowed.json()).publication_eligible).toBe(true);
  await detail.getByRole('button', { name: '公開許可を撤回', exact: true }).click();
  const revoke = page.getByRole('dialog', { name: '公開許可を撤回', exact: true });
  await revoke.getByLabel('撤回を受けた日時（この端末の時刻）').fill('2026-01-03T09:00');
  await revoke.getByLabel('判断・変更の理由').fill('公開許可の撤回を受け付けた');
  await revoke.getByRole('button', { name: '確認へ', exact: true }).click();
  await confirm(page);
  await expect(detail.getByText('公開許可撤回済み', { exact: true })).toBeVisible();
  await expect(detail.getByText('内部承認済み', { exact: true })).toBeVisible();
  const replay = await request.post('/api/store/reviews', { headers: headers(), data: receipt });
  expect(replay.status()).toBe(200);
  const replayed = await replay.json();
  expect(replayed.operation.replayed).toBe(true);
  expect(replayed.review.permission_status).toBe('REVOKED');
  expect(replayed.review.publication_eligible).toBe(false);
});

Then('訂正先は審査待ちと許可未取得になり旧記録の履歴が残る', async ({ page, request, $testInfo }) => {
  const detail = page.getByRole('dialog', { name: '口コミの内容・履歴', exact: true });
  await detail.getByRole('button', { name: '訂正再受付', exact: true }).click();
  const correction = page.getByRole('dialog', { name: '訂正再受付', exact: true });
  await expect(correction.getByText(/公開許可は引き継ぎません/)).toBeVisible();
  await correction.getByLabel('口コミ本文').fill('訂正後の新しい文面');
  await correction.getByLabel('判断・変更の理由').fill('原文の誤記を訂正');
  await correction.getByRole('button', { name: '確認へ', exact: true }).click();
  await confirm(page);
  await expect(detail.getByText('訂正後の新しい文面', { exact: true })).toBeVisible();
  await expect(detail.getByText('審査待ち', { exact: true })).toBeVisible();
  await expect(detail.getByText('公開許可未取得', { exact: true })).toBeVisible();
  const oldResponse = await request.get(`/api/store/reviews/${reviewId}`, { headers: headers() });
  const old = await oldResponse.json();
  expect(old.status).toBe('WITHDRAWN');
  expect(old.permission_status).toBe('REVOKED');
  expect(old.permission.evidence_note).toBe('書面で対象文面と表示名の自店舗サイト掲載を確認');
  const historyResponse = await request.get(`/api/store/reviews/${reviewId}/history`, { headers: headers(), params: { size: 2 } });
  const history = await historyResponse.json();
  expect(history.content).toHaveLength(2);
  expect(history.content[0].type).toBe('CORRECTION_LINKED');
  expect(history.next_cursor).toBeTruthy();
  expect(JSON.stringify(history)).not.toContain('書面で対象文面と表示名の自店舗サイト掲載を確認');
  const listResponse = await request.get('/api/store/reviews', { headers: headers(), params: { review_id: old.superseded_by_id } });
  const list = await listResponse.json();
  expect(list.content).toHaveLength(1);
  expect(list.content[0]).not.toHaveProperty('body');
  expect(list.content[0]).not.toHaveProperty('permission');
  await expect(page.getByText('口コミの操作を記録しました', { exact: true })).toHaveCount(0, { timeout: 10000 });
  await page.screenshot({ path: $testInfo.outputPath('review-correction.png'), fullPage: true, animations: 'disabled' });
});
