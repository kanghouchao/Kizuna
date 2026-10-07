import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import { createPermissionRole, createPlatformStaffFixture, loginPlatformUser } from './store-api';
const { Given, When, Then } = createBdd();
let operator = '';
let viewer = '';

let email = '';
let password = '';
let serviceId = 0;
let incompleteId = 0;
let serviceName = '';
let asOf = '';
const headers = (token: string) => ({ Authorization: `Bearer ${token}` });

Given('失効処理用の主体と権限不足の主体が用意されている', async ({ request }) => {
  const admin = await loginPlatformUser(request, 'admin@kizuna.test', 'pass');
  const suffix = Date.now().toString();
  password = 'Expiry-' + suffix + '-fixture';
  email = 'expiry-' + suffix + '@kizuna.test';
  const role = await createPermissionRole(request, admin, '失効運用-' + suffix, ['TASK_MANAGE', 'POINT_EXPIRE', 'AUDIT_VIEW', 'PLATFORM_MENU_VIEW']);
  await createPlatformStaffFixture(request, admin, email, password, [role]);
  const viewerRole = await createPermissionRole(request, admin, '失効閲覧-' + suffix, ['TASK_MANAGE']);
  const viewerEmail = 'expiry-view-' + suffix + '@kizuna.test';
  await createPlatformStaffFixture(request, admin, viewerEmail, password, [viewerRole]);
  viewer = await loginPlatformUser(request, viewerEmail, password);
  operator = await loginPlatformUser(request, email, password);
  const complete = await createPermissionRole(request, admin, '失効処理-' + suffix, ['TASK_EXECUTE', 'POINT_EXPIRE']);
  const incomplete = await createPermissionRole(request, admin, '実行のみ-' + suffix, ['TASK_EXECUTE']);
  serviceName = '日次失効サービス-' + suffix;
  for (const [roleId, name] of [[complete, serviceName], [incomplete, '権限不足-' + suffix]] as const) {
    const response = await request.post('/api/platform/service-identities', { headers: headers(admin), data: { display_name: name, role_ids: [roleId], store_scope_type: 'ALL_STORES', store_ids: [] } });
    expect(response.status(), await response.text()).toBe(201);
    const id = (await response.json()).id;
    if (roleId === complete) serviceId = id;
    else incompleteId = id;
  }
  asOf = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Tokyo' }).format(new Date());
});

When('失効処理の候補と再送と未来日の拒否を確認する', async ({ request }) => {
  const candidates = await request.get('/api/platform/task-executions/service-identities', { headers: headers(operator), params: { task_name: 'POINT_EXPIRY', size: 100 } });
  expect(candidates.status()).toBe(200);
  const ids = (await candidates.json()).content.map((row: { id: number }) => row.id);
  expect(ids).toContain(serviceId);
  expect(ids).not.toContain(incompleteId);
  expect((await request.get('/api/platform/task-executions/service-identities', { headers: headers(operator), params: { task_name: 'UNREGISTERED' } })).status()).toBe(400);
  const body = { task_name: 'POINT_EXPIRY', logical_key: crypto.randomUUID(), service_user_id: serviceId, store_id: null, period_start: asOf, period_end: asOf };
  expect((await request.post('/api/platform/task-executions', { headers: headers(operator), data: { ...body, service_user_id: incompleteId } })).status()).toBe(403);
  expect((await request.post('/api/platform/task-executions', { headers: headers(viewer), data: body })).status()).toBe(403);
  const first = await request.post('/api/platform/task-executions', { headers: headers(operator), data: body });
  expect(first.status(), await first.text()).toBe(201);
  const run = (await first.json()).execution;
  expect(run.status).toBe('SUCCEEDED');
  const replay = await request.post('/api/platform/task-executions', { headers: headers(viewer), data: body });
  expect(replay.status()).toBe(200);
  expect((await replay.json()).execution.id).toBe(run.id);
  const futureDate = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10);
  const future = await request.post('/api/platform/task-executions', { headers: headers(operator), data: { ...body, logical_key: crypto.randomUUID(), period_start: futureDate, period_end: futureDate } });
  expect(future.status()).toBe(201);
  const failed = (await future.json()).execution;
  expect(failed.status).toBe('FAILED');
  expect((await request.post(`/api/platform/task-executions/${failed.id}/retries`, { headers: headers(viewer), data: { reason: '権限境界の検証' } })).status()).toBe(403);
});

Then('失効処理を画面から選択して結果を確認できる', async ({ page, $testInfo }) => {
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel('メールアドレス', { exact: true }).fill(email);
  await page.getByLabel('パスワード', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).toHaveURL(/\/platform\/dashboard/, { timeout: 15000 });
  await page.goto(`${PLATFORM_URL}/platform/task-executions`);
  await page.getByRole('combobox', { name: '処理', exact: true }).click();
  await page.getByRole('option', { name: '期限切れポイントの記帳', exact: true }).click();
  await page.getByRole('combobox', { name: '実行主体', exact: true }).click();
  await page.getByRole('option', { name: serviceName, exact: true }).click();
  await page.getByLabel('対象日', { exact: true }).fill(asOf);
  await page.getByRole('button', { name: '期限切れポイントを記帳', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '実行の詳細' });
  await expect(dialog.getByText('成功', { exact: true })).toBeVisible();
  await dialog.press('Escape');
  await page.setViewportSize({ width: 1024, height: 900 });
  await page.screenshot({ path: $testInfo.outputPath('point-expiry-light.png'), fullPage: true, animations: 'disabled' });
  await page.evaluate(() => document.documentElement.classList.add('dark'));
  await page.screenshot({ path: $testInfo.outputPath('point-expiry-dark.png'), fullPage: true, animations: 'disabled' });
});
