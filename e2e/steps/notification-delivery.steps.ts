import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { APP_URL } from '../base-url';
import { latestMailTo } from './mailpit';
import { authorizedStoreIds, createConsentingGuestApplication, createPermissionRole, createServiceIdentity, createStaffFixture, expectSidebarLinkAbsentEverywhere, loginInBrowser, loginUser, loginViaUiAndEnterStore, memberIdOf, registerMember, selectRailContext, STORE1_ID, STORE_HEADERS, openLoginForm, storeUrl, waitStaffLanding } from './store-api';
const { Given, When, Then, After } = createBdd();
let email = '';
let password = '';
let operator = '';
let applicationId = '';
let guestEmail = '';
let deliveryId = '';
let serviceName = '';
let admin = '';
const GUEST_BODY = 'ご予約内容の確認をお願いします。';
const GUEST_LINK = 'http://store1.kizuna.test/reservation';
const headers = () => ({ ...STORE_HEADERS, Authorization: `Bearer ${operator}` });

Given('業務通知の担当者とサービス主体と同意済みゲスト申請がある', async ({ request, page }) => {
  await loginViaUiAndEnterStore(page);
  admin = await loginUser(request, 'admin@kizuna.test', 'pass1234');
  expect((await request.get('/api/notification-deliveries', { headers: { ...STORE_HEADERS, Authorization: `Bearer ${admin}` } })).status()).toBe(403);
  const suffix = Date.now().toString();
  email = 'notification-' + suffix + '@kizuna.test';
  password = 'Notification-' + suffix + '-fixture';
  const role = await createPermissionRole(request, admin, '通知運用-' + suffix, ['TASK_MANAGE', 'NOTIFICATION_DELIVER', 'NOTIFICATION_VIEW', 'NOTIFICATION_MANAGE', 'NOTIFICATION_SEND', 'MEMBER_NOTIFICATION_VIEW', 'MEMBER_NOTIFICATION_MANAGE', 'MEMBER_NOTIFICATION_SEND']);
  // 担当者は店舗 1 だけを持つ。会員宛の通知と定期処理の画面は権限だけで開く。
  await createStaffFixture(request, admin, email, password, [role], [Number(STORE1_ID)]);
  operator = await loginUser(request, email, password);
  const executionRole = await createPermissionRole(request, admin, '通知実行-' + suffix, ['TASK_EXECUTE', 'NOTIFICATION_DELIVER']);
  serviceName = '通知サービス-' + suffix;
  await createServiceIdentity(request, admin, serviceName, [executionRole], await authorizedStoreIds(request, admin));
  guestEmail = 'notification-' + suffix + '@example.invalid';
  applicationId = await createConsentingGuestApplication(request, guestEmail);
});

async function loginAsOperator(page: Page) {
  await page.context().clearCookies();
  await openLoginForm(page);
  await page.getByLabel('メールアドレス', { exact: true }).fill(email);
  await page.getByLabel('パスワード', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await waitStaffLanding(page);
}

When('担当者が通知を作成して内容を確認し送信待ちにする', async ({ page, request, $testInfo }) => {
  await loginAsOperator(page);
  await page.goto(storeUrl(STORE1_ID, `/notification-deliveries`));
  await page.getByRole('button', { name: '通知を作成', exact: true }).click();
  const create = page.getByRole('dialog', { name: '業務通知を作成' });
  await create.getByRole('combobox', { name: '起点', exact: true }).click();
  await page.getByRole('option', { name: 'ゲスト申請', exact: true }).click();
  await create.getByLabel('受注・ゲスト申請ID').fill(applicationId);
  await create.getByLabel('件名', { exact: true }).fill('ご予約の確認');
  await create.getByLabel('本文', { exact: true }).fill(`${GUEST_BODY}\n${GUEST_LINK}`);
  await create.getByLabel('送信予定（この端末の時刻）').fill('2026-01-01T10:00');
  const [created] = await Promise.all([
    page.waitForResponse(response => response.url().endsWith('/notification-deliveries') && response.request().method() === 'POST'),
    create.getByRole('button', { name: '下書きを作成', exact: true }).click(),
  ]);
  expect(created.status()).toBe(201);
  const row = await created.json();
  deliveryId = row.id;
  expect(row.contact_decision).toBe('ALLOWED');
  expect(row.transport_availability).toBe('AVAILABLE');
  expect(JSON.stringify(row)).not.toContain('@example.invalid');
  const detail = page.getByRole('dialog', { name: '通知の内容・送信履歴' });
  await expect(detail.getByText('業務連絡が許可されています', { exact: true })).toBeVisible();
  await detail.getByLabel('内容を確認した理由・再試行の理由').fill('内容確認');
  await page.route(`**/notification-deliveries/${deliveryId}/queue`, route => route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ error: '通知が見つかりません' }) }), { times: 1 });
  await detail.getByRole('button', { name: '確認して送信待ちにする' }).click();
  await expect(detail.getByRole('button', { name: '一覧へ戻る' })).toBeVisible();
  await expect(detail.getByRole('button', { name: '確認して送信待ちにする' })).toHaveCount(0);
  await detail.getByRole('button', { name: '一覧へ戻る' }).click();
  await expect(detail).toBeHidden();
  await page.getByRole('button', { name: '内容・履歴' }).first().click();
  await detail.getByLabel('内容を確認した理由・再試行の理由').fill('内容と業務連絡の許可を確認');
  await detail.getByLabel('内容を確認した理由・再試行の理由').press('Tab');
  await expect(detail.getByRole('button', { name: '確認して送信待ちにする' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(detail.getByText('送信待ち', { exact: true })).toBeVisible();
  expect((await request.post(`/api/notification-deliveries/${deliveryId}/queue`, { headers: headers(), data: { version: row.version, reason: '同一操作' } })).status()).toBe(409);
  await expect(page.getByText('通知を送信待ちに登録しました', { exact: true })).toBeHidden({ timeout: 10000 });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await detail.evaluate(element => element.scrollWidth <= element.clientWidth)).toBeTruthy();
  await page.screenshot({ path: $testInfo.outputPath('notification-light-mobile.png'), fullPage: true, animations: 'disabled' });
  await page.evaluate(() => document.documentElement.classList.add('dark'));
  await page.screenshot({ path: $testInfo.outputPath('notification-dark-mobile.png'), fullPage: true, animations: 'disabled' });
  await detail.press('Escape');
  await page.setViewportSize({ width: 1280, height: 900 });
});

async function executeNotifications(page: Page) {
  await page.goto(`${APP_URL}/admin/task-executions`);
  await page.getByRole('combobox', { name: '処理', exact: true }).click();
  await page.getByRole('option', { name: '業務通知の送信', exact: true }).click();
  await page.getByRole('combobox', { name: '実行主体', exact: true }).click();
  await page.getByRole('option', { name: serviceName, exact: true }).click();
  await page.getByLabel('対象日', { exact: true }).fill(new Date().toISOString().slice(0, 10));
  await page.getByRole('button', { name: '通知を送信器へ引き渡す', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '実行の詳細' }).getByText('成功', { exact: true })).toBeVisible();
}

When('管理画面で店舗の業務通知を実行する', async ({ page }) => {
  await executeNotifications(page);
});

Then('通知は送信済みになり、確認した件名と本文のメールがゲスト申請の宛先に届く', async ({ page, request, $testInfo }) => {
  await expect.poll(async () => {
    const response = await request.get(`/api/notification-deliveries/${deliveryId}`, { headers: headers() });
    expect(response.status()).toBe(200);
    return (await response.json()).status;
  }).toBe('SENT');
  const mail = await latestMailTo(request, guestEmail);
  expect(mail.subject).toBe('ご予約の確認');
  expect(mail.text).toContain(GUEST_BODY);
  expect(mail.firstLink).toBe(GUEST_LINK);
  const history = await request.get(`/api/notification-deliveries/${deliveryId}/attempts`, { headers: headers() });
  expect(history.status()).toBe(200);
  const attempts = (await history.json()).content;
  expect(attempts).toHaveLength(1);
  expect(attempts[0]).toMatchObject({ attempt_number: 1, status: 'SENT', reason: '内容と業務連絡の許可を確認' });
  await page.goto(storeUrl(STORE1_ID, `/notification-deliveries`));
  await page.getByRole('row').filter({ hasText: applicationId }).getByRole('button', { name: '内容・履歴' }).click();
  const detail = page.getByRole('dialog', { name: '通知の内容・送信履歴' });
  await expect(detail.getByText('メール提供元受理', { exact: true }).first()).toBeVisible();
  await expect(detail.getByRole('button', { name: '明示的に再試行する' })).toHaveCount(0);
  await page.screenshot({ path: $testInfo.outputPath('notification-sent-history.png'), fullPage: true, animations: 'disabled' });
});

async function putConfig(request: APIRequestContext, key: string, value: string) {
  const response = await request.put(`/api/system-configs/${key}`, {
    headers: { Authorization: `Bearer ${admin}` },
    data: { config_value: value },
  });
  expect(response.status(), await response.text()).toBe(200);
}

// 閉じたポートへの接続失敗は MailService が「受理されたか分からない」として結果不明に倒す。
Given('メールの接続先が送信結果を確かめられない状態にある', async ({ request }) => {
  await putConfig(request, 'smtp_port', '1');
  await putConfig(request, 'smtp_host', '127.0.0.1');
});

// 空でない smtp_host を残すと後続の通知シナリオのメールが Mailpit に届かなくなるので、種の既定値へ必ず戻す。
After({ tags: '@notification-unknown-resolution' }, async ({ request }) => {
  await putConfig(request, 'smtp_host', '');
  await putConfig(request, 'smtp_port', '25');
});

When('担当者が送信待ちにした通知を管理画面で実行する', async ({ page, request }) => {
  const created = await request.post('/api/notification-deliveries', {
    headers: headers(),
    data: {
      source_type: 'APPLICATION',
      source_id: applicationId,
      channel: 'EMAIL',
      purpose: 'BUSINESS',
      subject: 'ご予約の確認',
      body: 'ご予約内容の確認をお願いします。',
      scheduled_at: '2026-01-01T01:00:00Z',
      dedupe_key: 'unknown-' + applicationId,
    },
  });
  expect(created.status(), await created.text()).toBe(201);
  const row = await created.json();
  deliveryId = row.id;
  expect(row.transport_availability).toBe('AVAILABLE');
  const queued = await request.post(`/api/notification-deliveries/${deliveryId}/queue`, {
    headers: headers(),
    data: { version: row.version, reason: '内容と業務連絡の許可を確認' },
  });
  expect(queued.status(), await queued.text()).toBe(200);
  await loginAsOperator(page);
  await executeNotifications(page);
});

Then('結果不明の通知を未送達と判定して再試行できる', async ({ page, request, $testInfo }) => {
  await expect.poll(async () => {
    const response = await request.get(`/api/notification-deliveries/${deliveryId}`, { headers: headers() });
    expect(response.status()).toBe(200);
    return (await response.json()).status;
  }, { timeout: 30000 }).toBe('UNKNOWN');
  await page.goto(storeUrl(STORE1_ID, `/notification-deliveries`));
  await page.getByRole('row').filter({ hasText: applicationId }).getByRole('button', { name: '内容・履歴' }).click();
  const detail = page.getByRole('dialog', { name: '通知の内容・送信履歴' });
  await expect(detail.getByText(/送信結果が不明です/)).toBeVisible();
  await expect(detail.getByRole('button', { name: '明示的に再試行する' })).toHaveCount(0);
  await detail.getByRole('radio', { name: /未送達/ }).click();
  await detail.getByLabel('確認した内容・判定の理由').fill('提供元の管理画面で未配信を確認');
  await page.screenshot({ path: $testInfo.outputPath('notification-unknown-form.png'), fullPage: true, animations: 'disabled' });
  await detail.getByRole('button', { name: '判定を記録する' }).click();
  await expect(page.getByText('判定を記録しました', { exact: true })).toBeVisible();
  await expect(detail.getByRole('button', { name: '判定を記録する' })).toHaveCount(0);
  const judged = detail.getByRole('listitem').filter({ hasText: '提供元の管理画面で未配信を確認' });
  await expect(judged.getByText('未送達と判定', { exact: true })).toBeVisible();
  await expect(judged.getByText(/職員の判定/)).toBeVisible();
  await page.screenshot({ path: $testInfo.outputPath('notification-unknown-resolved.png'), fullPage: true, animations: 'disabled' });
  await detail.getByLabel('内容を確認した理由・再試行の理由').fill('未送達の判定にもとづき再試行');
  await detail.getByRole('button', { name: '明示的に再試行する' }).click();
  await expect(detail.getByText('送信待ち', { exact: true })).toBeVisible();

  const delivery = await (await request.get(`/api/notification-deliveries/${deliveryId}`, { headers: headers() })).json();
  expect(delivery.status).toBe('QUEUED');
  const history = await request.get(`/api/notification-deliveries/${deliveryId}/attempts`, { headers: headers() });
  const attempts = (await history.json()).content;
  expect(attempts).toHaveLength(2);
  expect(attempts[0]).toMatchObject({ attempt_number: 2, status: 'FAILED', failure_code: 'MANUAL_RESOLUTION', reason: '提供元の管理画面で未配信を確認' });
  expect(attempts[0].task_execution_id).toBeUndefined();
  expect(attempts[1]).toMatchObject({ attempt_number: 1, status: 'UNKNOWN', failure_code: 'UNKNOWN' });
  const again = await request.post(`/api/notification-deliveries/${deliveryId}/resolutions`, {
    headers: headers(),
    data: { version: delivery.version, outcome: 'SENT', reason: '重ねて判定' },
  });
  expect(again.status()).toBe(409);
});

// 会員宛（店舗を持たない）の行は店舗の行とは別の資源で、X-Store-ID を付けずに扱う。
const groupHeaders = () => ({ Authorization: `Bearer ${operator}` });
const MEMBER_DELIVERIES = '/api/member-notification-deliveries';
let allowedDelivery = '';
let allowedEmail = '';
let unconfirmedDelivery = '';
const memberOf = new Map<string, string>();

async function memberDelivery(request: APIRequestContext, memberEmail: string, allow: boolean) {
  const memberPassword = 'Member-' + Date.now().toString() + '-fixture';
  await registerMember(request, memberEmail, memberPassword, '通知の会員');
  if (allow) {
    const member = await loginUser(request, memberEmail, memberPassword);
    const chosen = await request.put('/api/me/member/notification-settings/BUSINESS', {
      headers: { Authorization: `Bearer ${member}` },
      data: { status: 'ALLOWED' },
    });
    expect(chosen.status(), await chosen.text()).toBe(200);
  }
  const memberId = await memberIdOf(request, admin, memberEmail);
  const created = await request.post(MEMBER_DELIVERIES, {
    headers: groupHeaders(),
    data: {
      source_type: 'MEMBER',
      source_id: memberId,
      channel: 'EMAIL',
      purpose: 'BUSINESS',
      subject: 'ポイントのご案内',
      body: 'ポイントの期限が近づいています。',
      scheduled_at: '2026-01-01T01:00:00Z',
      dedupe_key: 'member-' + memberId,
    },
  });
  expect(created.status(), await created.text()).toBe(201);
  const row = await created.json();
  expect(JSON.stringify(row)).not.toContain(memberEmail);
  memberOf.set(row.id, memberId);
  return row.id as string;
}

Given('業務連絡を許可した会員と許可していない会員に会員宛の通知が起票されている', async ({ request }) => {
  const suffix = Date.now().toString();
  allowedEmail = 'allowed-' + suffix + '@example.invalid';
  allowedDelivery = await memberDelivery(request, allowedEmail, true);
  unconfirmedDelivery = await memberDelivery(request, 'unconfirmed-' + suffix + '@example.invalid', false);
  // 会員宛の行は店舗の行と混ざらない。
  const storeList = await request.get('/api/notification-deliveries', { headers: headers(), params: { size: 100 } });
  const storeIds = (await storeList.json()).content.map((row: { id: string }) => row.id);
  expect(storeIds).not.toContain(allowedDelivery);
  expect((await request.get(`/api/notification-deliveries/${allowedDelivery}`, { headers: headers() })).status()).toBe(404);
});

When('担当者がグループの業務通知で会員宛の通知を確認して送信待ちにする', async ({ page, $testInfo }) => {
  await loginAsOperator(page);
  await selectRailContext(page, 'グループ');
  await page.locator('aside').locator('a[href="/admin/notification-deliveries"]').click();
  await expect(page).toHaveURL(/\/admin\/notification-deliveries/);
  await expect(page.getByText('会員宛の業務メールと送信履歴', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '通知を作成', exact: true })).toHaveCount(0);
  const detail = page.getByRole('dialog', { name: '通知の内容・送信履歴' });
  for (const [id, decision] of [[allowedDelivery, '業務連絡が許可されています'], [unconfirmedDelivery, '業務連絡の許可が確認できません']]) {
    const [response] = await Promise.all([
      page.waitForResponse(r => r.url().endsWith(`/member-notification-deliveries/${id}`) && r.request().method() === 'GET'),
      page
        .getByRole('row')
        .filter({ has: page.getByRole('cell').filter({ hasText: new RegExp(`^会員${memberOf.get(id)}$`) }) })
        .getByRole('button', { name: '内容・履歴' })
        .click(),
    ]);
    expect(response.request().headers()['x-store-id']).toBeUndefined();
    await expect(detail.getByText(decision, { exact: true })).toBeVisible();
    await detail.getByLabel('内容を確認した理由・再試行の理由').fill('会員宛の内容を確認');
    await detail.getByRole('button', { name: '確認して送信待ちにする' }).click();
    await expect(detail.getByText('送信待ち', { exact: true })).toBeVisible();
    if (id === allowedDelivery)
      await page.screenshot({ path: $testInfo.outputPath('notification-group.png'), fullPage: true, animations: 'disabled' });
    await detail.press('Escape');
  }
});

Then('許可した会員宛だけが送信されて届き、許可していない会員宛は送信不可になる', async ({ request }) => {
  const statusOf = async (id: string) => (await (await request.get(`${MEMBER_DELIVERIES}/${id}`, { headers: groupHeaders() })).json()).status;
  await expect.poll(() => statusOf(allowedDelivery)).toBe('SENT');
  await expect.poll(() => statusOf(unconfirmedDelivery)).toBe('BLOCKED');
  const lastFailure = async (id: string) => (await (await request.get(`${MEMBER_DELIVERIES}/${id}/attempts`, { headers: groupHeaders() })).json()).content[0].failure_code;
  expect(await lastFailure(unconfirmedDelivery)).toBe('CONTACT_NOT_ALLOWED');
  const mail = await latestMailTo(request, allowedEmail);
  expect(mail.subject).toBe('ポイントのご案内');
  expect(mail.text).toContain('ポイントの期限が近づいています。');
});

Then('店舗の通知の権限だけを持つ職員は会員宛の業務通知に到達できない', async ({ page, request }) => {
  const suffix = Date.now().toString();
  const scopedEmail = 'notification-scoped-' + suffix + '@kizuna.test';
  const scopedPassword = 'Notification-' + suffix + '-scoped';
  const role = await createPermissionRole(request, admin, '店舗通知-' + suffix, ['NOTIFICATION_VIEW', 'NOTIFICATION_SEND', 'MEMBER_VIEW']);
  await createStaffFixture(request, admin, scopedEmail, scopedPassword, [role], [Number(STORE1_ID)]);
  const scoped = await loginUser(request, scopedEmail, scopedPassword);
  expect((await request.get(MEMBER_DELIVERIES, { headers: { Authorization: `Bearer ${scoped}` } })).status()).toBe(403);
  expect((await request.get(`${MEMBER_DELIVERIES}/${allowedDelivery}`, { headers: { Authorization: `Bearer ${scoped}` } })).status()).toBe(403);
  // 店舗の通知は店舗を名乗らなければ扱えない。
  expect((await request.get('/api/notification-deliveries', { headers: { Authorization: `Bearer ${scoped}` } })).status()).toBe(400);
  expect((await request.get('/api/notification-deliveries', { headers: { ...STORE_HEADERS, Authorization: `Bearer ${scoped}` } })).status()).toBe(200);
  await page.context().clearCookies();
  await loginInBrowser(page, scopedEmail, scopedPassword);
  await page.goto(`${APP_URL}/admin/dashboard`);
  await expectSidebarLinkAbsentEverywhere(page, aside => aside.locator('a[href="/admin/notification-deliveries"]'));
});
