import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import { createConsentingGuestApplication, createPermissionRole, createPlatformStaffFixture, loginPlatformUser, loginViaUiAndEnterStore, STORE_HEADERS } from './store-api';
const { Given, When, Then } = createBdd();
let email = '';
let password = '';
let operator = '';
let applicationId = '';
let deliveryId = '';
let serviceName = '';
let storeId = '';
let storeName = '';
const headers = () => ({ ...STORE_HEADERS, Authorization: `Bearer ${operator}` });

Given('業務通知の担当者とサービス主体と同意済みゲスト申請がある', async ({ request, page }) => {
  storeId = await loginViaUiAndEnterStore(page);
  const admin = await loginPlatformUser(request, 'admin@kizuna.test', 'pass');
  const suffix = Date.now().toString();
  email = 'notification-' + suffix + '@kizuna.test';
  password = 'Notification-' + suffix + '-fixture';
  const role = await createPermissionRole(request, admin, '通知運用-' + suffix, ['TASK_MANAGE', 'NOTIFICATION_DELIVER', 'NOTIFICATION_VIEW', 'NOTIFICATION_MANAGE', 'NOTIFICATION_SEND', 'PLATFORM_MENU_VIEW', 'STORE_MENU_VIEW']);
  await createPlatformStaffFixture(request, admin, email, password, [role]);
  operator = await loginPlatformUser(request, email, password);
  const executionRole = await createPermissionRole(request, admin, '通知実行-' + suffix, ['TASK_EXECUTE', 'NOTIFICATION_DELIVER']);
  serviceName = '通知サービス-' + suffix;
  const service = await request.post('/api/platform/service-identities', {
    headers: { Authorization: `Bearer ${admin}` },
    data: { display_name: serviceName, role_ids: [executionRole], store_scope_type: 'ALL_STORES', store_ids: [] },
  });
  expect(service.status(), await service.text()).toBe(201);
  const stores = await request.get('/api/platform/task-executions/stores', { headers: { Authorization: `Bearer ${operator}` }, params: { size: 100 } });
  expect(stores.status()).toBe(200);
  storeName = (await stores.json()).content.find((row: { id: string }) => row.id === storeId).name;
  applicationId = await createConsentingGuestApplication(request, 'notification-' + suffix + '@example.invalid');
});

When('担当者が通知を作成して内容を確認し送信待ちにする', async ({ page, request, $testInfo }) => {
  await page.context().clearCookies();
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel('メールアドレス', { exact: true }).fill(email);
  await page.getByLabel('パスワード', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).toHaveURL(/\/platform\/dashboard/, { timeout: 15000 });
  await page.goto(`${PLATFORM_URL}/store/${storeId}/notification-deliveries`);
  await page.getByRole('button', { name: '通知を作成', exact: true }).click();
  const create = page.getByRole('dialog', { name: '業務通知を作成' });
  await create.getByRole('combobox', { name: '起点', exact: true }).click();
  await page.getByRole('option', { name: 'ゲスト申請', exact: true }).click();
  await create.getByLabel('受注・ゲスト申請ID').fill(applicationId);
  await create.getByLabel('件名', { exact: true }).fill('ご予約の確認');
  await create.getByLabel('本文', { exact: true }).fill('ご予約内容の確認をお願いします。');
  await create.getByLabel('送信予定（この端末の時刻）').fill('2026-01-01T10:00');
  const [created] = await Promise.all([
    page.waitForResponse(response => response.url().endsWith('/notification-deliveries') && response.request().method() === 'POST'),
    create.getByRole('button', { name: '下書きを作成', exact: true }).click(),
  ]);
  expect(created.status()).toBe(201);
  const row = await created.json();
  deliveryId = row.id;
  expect(row.contact_decision).toBe('ALLOWED');
  expect(row.transport_availability).toBe('UNAVAILABLE');
  expect(JSON.stringify(row)).not.toContain('@example.invalid');
  const detail = page.getByRole('dialog', { name: '通知の内容・送信履歴' });
  await expect(detail.getByText('業務連絡が許可されています', { exact: true })).toBeVisible();
  await detail.getByLabel('内容を確認した理由・再試行の理由').fill('内容と業務連絡の許可を確認');
  await detail.getByRole('button', { name: '確認して送信待ちにする' }).click();
  await expect(detail.getByText('送信待ち', { exact: true })).toBeVisible();
  expect((await request.post(`/api/store/notification-deliveries/${deliveryId}/queue`, { headers: headers(), data: { version: row.version, reason: '同一操作' } })).status()).toBe(409);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await detail.evaluate(element => element.scrollWidth <= element.clientWidth)).toBeTruthy();
  await page.screenshot({ path: $testInfo.outputPath('notification-light-mobile.png'), fullPage: true });
  await page.evaluate(() => document.documentElement.classList.add('dark'));
  await page.screenshot({ path: $testInfo.outputPath('notification-dark-mobile.png'), fullPage: true });
  await detail.press('Escape');
  await page.setViewportSize({ width: 1280, height: 900 });
});

When('管理画面で店舗の業務通知を実行する', async ({ page }) => {
  await page.goto(`${PLATFORM_URL}/platform/task-executions`);
  await page.getByRole('combobox', { name: '処理', exact: true }).click();
  await page.getByRole('option', { name: '業務通知の送信', exact: true }).click();
  await page.getByRole('combobox', { name: '対象店舗', exact: true }).click();
  await page.getByRole('option', { name: storeName, exact: true }).click();
  await page.getByRole('combobox', { name: '実行主体', exact: true }).click();
  await page.getByRole('option', { name: serviceName, exact: true }).click();
  await page.getByLabel('対象日', { exact: true }).fill(new Date().toISOString().slice(0, 10));
  await page.getByRole('button', { name: '通知を送信器へ引き渡す', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '実行の詳細' }).getByText('成功', { exact: true })).toBeVisible();
});

Then('未設定の通知を成功扱いせず履歴から明示的に再試行できる', async ({ page, request, $testInfo }) => {
  await expect.poll(async () => {
    const response = await request.get(`/api/store/notification-deliveries/${deliveryId}`, { headers: headers() });
    expect(response.status()).toBe(200);
    return (await response.json()).status;
  }).toBe('FAILED');
  await page.goto(`${PLATFORM_URL}/store/${storeId}/notification-deliveries`);
  await page.getByRole('button', { name: '内容・履歴' }).first().click();
  const detail = page.getByRole('dialog', { name: '通知の内容・送信履歴' });
  await expect(detail.getByText('送信設定が利用できません', { exact: true })).toBeVisible();
  await expect(detail.getByText('未送信', { exact: true }).first()).toBeVisible();
  await detail.getByLabel('内容を確認した理由・再試行の理由').fill('未送信の結果を確認して再試行');
  await detail.getByRole('button', { name: '明示的に再試行する' }).click();
  await expect(detail.getByText('送信待ち', { exact: true })).toBeVisible();
  const history = await request.get(`/api/store/notification-deliveries/${deliveryId}/attempts`, { headers: headers() });
  expect(history.status()).toBe(200);
  const attempts = (await history.json()).content;
  expect(attempts).toHaveLength(1);
  expect(attempts[0].failure_code).toBe('UNAVAILABLE');
  expect(attempts[0].reason).toBe('内容と業務連絡の許可を確認');
  await page.screenshot({ path: $testInfo.outputPath('notification-retry-history.png'), fullPage: true });
});
