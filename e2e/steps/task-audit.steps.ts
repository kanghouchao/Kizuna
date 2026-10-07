import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import { createPermissionRole, createPlatformStaffFixture, loginPlatformUser, STORE1_ID } from './store-api';
const { Given, When, Then } = createBdd();
let admin = '';
let operator = '';
let restricted = '';
let email = '';
let limitedEmail = '';
let password = '';
let serviceId = 0;
let serviceName = '';
let roleId = 0;
let serviceRoleId = 0;
const headers = (token: string) => ({ Authorization: `Bearer ${token}` });
type MenuNode = { path: string | null; items: MenuNode[] };
const menuPaths = (menus: MenuNode[]): (string | null)[] => menus.flatMap(menu => [menu.path, ...menuPaths(menu.items)]);

Given('専用の処理実行者とサービスIDが用意されている', async ({ request, $testInfo }) => {
  $testInfo.setTimeout(120000);
  admin = await loginPlatformUser(request, 'admin@kizuna.test', 'pass');
  expect((await request.get('/api/platform/task-executions', { headers: headers(admin) })).status()).toBe(403);
  expect((await request.get('/api/platform/audit-events', { headers: headers(admin) })).status()).toBe(403);
  const suffix = Date.now().toString();
  password = 'Audit-' + suffix + '-fixture';
  email = 'audit-' + suffix + '@kizuna.test';
  roleId = await createPermissionRole(request, admin, '実行監査-' + suffix, ['TASK_MANAGE', 'AUDIT_VIEW', 'PLATFORM_MENU_VIEW']);
  await createPlatformStaffFixture(request, admin, email, password, [roleId]);
  operator = await loginPlatformUser(request, email, password);
  limitedEmail = 'audit-limited-' + suffix + '@kizuna.test';
  await createPlatformStaffFixture(request, admin, limitedEmail, password, [roleId], [Number(STORE1_ID)]);
  restricted = await loginPlatformUser(request, limitedEmail, password);
  serviceRoleId = await createPermissionRole(request, admin, '実行確認-' + suffix, ['TASK_EXECUTE']);
  serviceName = '履歴に残るサービス-' + suffix;
  const service = await request.post('/api/platform/service-identities', { headers: headers(admin), data: { display_name: serviceName, role_ids: [serviceRoleId], store_scope_type: 'ALL_STORES', store_ids: [] } });
  expect(service.status(), await service.text()).toBe(201);
  serviceId = (await service.json()).id;
});

When('処理を再送し主体の権限と対象範囲を検証する', async ({ request, browser }) => {
  for (const [token, visible] of [[operator, true], [restricted, false], [admin, false]] as const) {
    const menus = await request.get('/api/platform/menus/me', { headers: headers(token) });
    expect(menus.status()).toBe(200);
    const paths = menuPaths(await menus.json());
    expect(paths.includes('/platform/task-executions')).toBe(visible);
    expect(paths.includes('/platform/audit-events')).toBe(visible);
  }
  const restrictedPage = await browser.newPage();
  try {
    await restrictedPage.goto(`${PLATFORM_URL}/platform/login`);
    await restrictedPage.getByLabel('メールアドレス', { exact: true }).fill(limitedEmail);
    await restrictedPage.getByLabel('パスワード', { exact: true }).fill(password);
    await restrictedPage.getByRole('button', { name: 'ログイン', exact: true }).click();
    await expect(restrictedPage).toHaveURL(/\/platform\/dashboard/, { timeout: 15000 });
    await expect(restrictedPage.locator('aside').locator('a[href="/platform/dashboard"]')).toBeVisible();
    await expect(restrictedPage.locator('aside').locator('a[href="/platform/task-executions"]')).toHaveCount(0);
    await expect(restrictedPage.locator('aside').locator('a[href="/platform/audit-events"]')).toHaveCount(0);
  } finally {
    await restrictedPage.close();
  }
  const body = { task_name: 'SERVICE_IDENTITY_CHECK', logical_key: crypto.randomUUID(), service_user_id: serviceId, store_id: null, period_start: '2026-10-07', period_end: '2026-10-07' };
  expect((await request.get('/api/platform/task-executions', { headers: headers(restricted) })).status()).toBe(403);
  expect((await request.get('/api/platform/audit-events', { headers: headers(restricted) })).status()).toBe(403);
  const initial = await request.post('/api/platform/task-executions', { headers: headers(operator), data: body });
  expect(initial.status(), await initial.text()).toBe(201);
  const execution = (await initial.json()).execution;
  expect(execution.status).toBe('SUCCEEDED');
  expect(execution.processed_count).toBe(0);
  const replay = await request.post('/api/platform/task-executions', { headers: headers(operator), data: body });
  expect(replay.status()).toBe(200);
  expect((await replay.json()).execution.id).toBe(execution.id);
  expect((await request.post('/api/platform/task-executions', { headers: headers(operator), data: { ...body, period_end: '2026-10-08' } })).status()).toBe(409);
  expect((await request.post(`/api/platform/task-executions/${execution.id}/retries`, { headers: headers(operator), data: { reason: '完了済みの再試行' } })).status()).toBe(409);
  expect((await request.post('/api/platform/task-executions', { headers: headers(operator), data: { ...body, task_name: 'UNREGISTERED' } })).status()).toBe(400);
  expect((await request.post(`/api/platform/service-identities/${serviceId}/suspension`, { headers: headers(admin) })).status()).toBe(204);
  const replayAfterSuspension = await request.post('/api/platform/task-executions', { headers: headers(operator), data: body });
  expect(replayAfterSuspension.status(), await replayAfterSuspension.text()).toBe(200);
  expect((await replayAfterSuspension.json()).execution.id).toBe(execution.id);
  expect((await request.post('/api/platform/task-executions', { headers: headers(operator), data: { ...body, period_end: '2026-10-08' } })).status()).toBe(409);
  expect((await request.post('/api/platform/task-executions', { headers: headers(admin), data: body })).status()).toBe(403);
  expect((await request.post('/api/platform/task-executions', { headers: headers(restricted), data: body })).status()).toBe(403);
  expect((await request.post('/api/platform/task-executions', { headers: headers(operator), data: { ...body, logical_key: crypto.randomUUID() } })).status()).toBe(403);
  expect((await request.post(`/api/platform/service-identities/${serviceId}/resumption`, { headers: headers(admin) })).status()).toBe(204);
  for (const [id, replacement, callerRevoked] of [
    [serviceRoleId, ['PLATFORM_MENU_VIEW'], false],
    [roleId, ['AUDIT_VIEW', 'PLATFORM_MENU_VIEW'], true],
  ] as const) {
    const current = await request.get(`/api/platform/roles/${id}`, { headers: headers(admin) });
    expect(current.status()).toBe(200);
    const original = await current.json();
    const changed = await request.put(`/api/platform/roles/${id}`, { headers: headers(admin), data: { name: original.name, permissions: replacement, version: original.version } });
    expect(changed.status(), await changed.text()).toBe(200);
    const replayWithRevocation = await request.post('/api/platform/task-executions', { headers: headers(operator), data: body });
    expect(replayWithRevocation.status()).toBe(callerRevoked ? 403 : 200);
    if (!callerRevoked) {
      expect((await replayWithRevocation.json()).execution.id).toBe(execution.id);
      expect((await request.post('/api/platform/task-executions', { headers: headers(operator), data: { ...body, logical_key: crypto.randomUUID() } })).status()).toBe(403);
    }
    const restored = await request.put(`/api/platform/roles/${id}`, { headers: headers(admin), data: { name: original.name, permissions: original.permissions, version: (await changed.json()).version } });
    expect(restored.status(), await restored.text()).toBe(200);
  }
  const audit = await request.get('/api/platform/audit-events', { headers: headers(operator), params: { action: 'SERVICE_ID_SUSPENDED' } });
  expect(audit.status()).toBe(200);
  const event = (await audit.json()).content.find((row: { target_id: string }) => row.target_id === String(serviceId));
  expect(event).toBeTruthy();
  const detail = await request.get(`/api/platform/audit-events/${event.id}`, { headers: headers(operator) });
  const snapshot = await detail.json();
  expect(snapshot.before_values.enabled).toBe('true');
  expect(snapshot.after_values.enabled).toBe('false');
  expect(JSON.stringify(snapshot)).not.toContain(password);
  expect((await request.put(`/api/platform/audit-events/${event.id}`, { headers: headers(operator), data: snapshot })).status()).toBe(405);
});

Then('実行履歴と変更前後の監査を画面で確認できる', async ({ page, request, $testInfo }) => {
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel('メールアドレス', { exact: true }).fill(email);
  await page.getByLabel('パスワード', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).toHaveURL(/\/platform\/dashboard/, { timeout: 15000 });
  await expect(page.locator('aside').locator('a[href="/platform/audit-events"]')).toBeVisible();
  await page.locator('aside').locator('a[href="/platform/task-executions"]').click();
  await expect(page.getByRole('heading', { name: '処理の実行履歴' })).toBeVisible();
  await page.getByRole('combobox', { name: '実行主体' }).click();
  await page.getByRole('option', { name: serviceName }).click();
  await page.getByLabel('対象日', { exact: true }).fill('2026-10-07');
  let lostExecutionId: number | undefined;
  let originalRequest: unknown;
  await page.route('**/api/platform/task-executions', async route => {
    if (route.request().method() === 'POST' && lostExecutionId === undefined) {
      originalRequest = route.request().postDataJSON();
      const committed = await route.fetch();
      expect(committed.status()).toBe(201);
      lostExecutionId = (await committed.json()).execution.id;
      await route.abort('failed');
    } else {
      if (route.request().method() === 'POST') expect(route.request().postDataJSON()).toEqual(originalRequest);
      await route.continue();
    }
  });
  await page.getByRole('button', { name: '実行確認を記録', exact: true }).click();
  await expect(page.getByText('実行に失敗しました', { exact: true })).toBeVisible();
  expect(lostExecutionId).toBeDefined();
  expect((await request.post(`/api/platform/service-identities/${serviceId}/suspension`, { headers: headers(admin) })).status()).toBe(204);
  const replayResponse = page.waitForResponse(response => response.url().endsWith('/api/platform/task-executions') && response.request().method() === 'POST');
  await page.getByRole('button', { name: '実行確認を記録', exact: true }).click();
  const recovered = await replayResponse;
  expect(recovered.status()).toBe(200);
  expect((await recovered.json()).execution.id).toBe(lostExecutionId);
  await page.unroute('**/api/platform/task-executions');
  const execution = page.getByRole('dialog', { name: '実行の詳細' });
  await expect(execution.getByText('成功', { exact: true })).toBeVisible();
  await execution.press('Escape');
  await expect(execution).not.toBeVisible();
  await page.setViewportSize({ width: 1024, height: 900 });
  await page.screenshot({ path: $testInfo.outputPath('task-executions.png'), fullPage: true, animations: 'disabled' });
  await page.goto(`${PLATFORM_URL}/platform/audit-events`);
  await page.getByLabel('操作コード（完全一致）').fill('SERVICE_ID_SUSPENDED');
  await page.getByRole('button', { name: '検索', exact: true }).click();
  await page.getByRole('row').filter({ hasText: String(serviceId) }).first().getByRole('button', { name: '詳細' }).click();
  const audit = page.getByRole('dialog', { name: '監査の詳細' });
  await expect(audit.getByRole('cell', { name: 'false', exact: true })).toBeVisible();
  await page.screenshot({ path: $testInfo.outputPath('audit-event.png'), fullPage: true, animations: 'disabled' });
  await audit.press('Escape');
});
