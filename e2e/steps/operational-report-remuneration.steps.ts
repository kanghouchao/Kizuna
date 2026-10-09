import { expect, type APIRequestContext } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { randomUUID } from 'node:crypto';
import { Client } from 'pg';
import { zipEntry } from './report-export';
import { PLATFORM_URL } from '../base-url';
import {
  ADMIN_PASSWORD, STORE1_ID, STORE_HEADERS, acceptCastInvitation,
  acceptExistingCastInvitation, createCast, createCourse, createAgreedOrder,
  completeAgreedOrder, correctOrderExtension, invalidateOrder, deleteService, createBonusAward, cancelBonusAward,
  issueCastInvitation, withdrawCast, recordAttendance, createPermissionRole,
  createPlatformStaffFixture, loginAsStoreAdmin, loginPlatformUser, getAuthorizedStores,
} from './store-api';

const { Given, When, Then, After } = createBdd();
const base = '/api/platform/operational-reports';
let manager = '', reader = '', legacy = '', foreign = '', course = '', order = '';
let bonus = { id: '', version: 0 }, movedBonus = { id: '', version: 0 };
let person = 0, year = '', privateName = '', privateReason = '', readerEmail = '', readerPassword = '';
const headers = (token = reader) => ({ Authorization: `Bearer ${token}` });
const storeHeaders = () => ({ ...STORE_HEADERS, ...headers() });
const parameters = (from = '08-31', to = '09-04') => ({
  from: `${year}-${from}`, to: `${year}-${to}`, group_by: 'day', include_remuneration: true,
});
async function report(request: APIRequestContext, from?: string, to?: string) {
  const response = await request.get(base, { headers: headers(), params: parameters(from, to) });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}
async function database() {
  if (process.env.PGHOST !== 'database') throw new Error('専用 E2E DB でのみ実行できます');
  const db = new Client({ application_name: 'report-remuneration388-bdd' });
  await db.connect();
  return db;
}

Given('帳票用の歴史在籍に日跨ぎ出勤と受注のない保証日と付与がある', async ({ request, $testInfo }) => {
  $testInfo.setTimeout(120000);
  year = String(1993 - $testInfo.retry);
  course = '';
  manager = await loginAsStoreAdmin(request);
  const owner = await loginPlatformUser(request, 'admin@kizuna.test', ADMIN_PASSWORD);
  const suffix = randomUUID(), password = randomUUID();
  readerPassword = password;
  privateName = `出力禁止本人名-${suffix}`;
  privateReason = `出力禁止内部理由-${suffix}`;
  foreign = String((await getAuthorizedStores(request, manager)).find(s => String(s.id) !== STORE1_ID)!.id);
  const common = ['ORDER_MANAGE', 'ORDER_SET_MANAGE', 'STORE_MENU_VIEW', 'PLATFORM_MENU_VIEW',
    'OPERATIONAL_REPORT_VIEW', 'OPERATIONAL_REPORT_EXPORT'];
  const tokens: string[] = [];
  for (const enhanced of [true, false]) {
    const role = await createPermissionRole(request, owner, `帳票報酬-${enhanced}-${suffix}`,
      enhanced ? [...common, 'REMUNERATION_VIEW', 'GUARANTEE_MANAGE', 'BONUS_AWARD', 'REMUNERATION_CORRECT'] : common);
    const email = `report-remuneration-${enhanced}-${suffix}@example.test`;
    if (enhanced) readerEmail = email;
    await createPlatformStaffFixture(request, owner, email, password, [role], [Number(STORE1_ID)]);
    tokens.push(await loginPlatformUser(request, email, password));
  }
  [reader, legacy] = tokens;
  const first = await createCast(request, manager, privateName + '旧');
  const castEmail = `report-cast-${suffix}@example.test`;
  await acceptCastInvitation(request, await issueCastInvitation(request, manager, first), castEmail, password, privateName);
  const castToken = await loginPlatformUser(request, castEmail, password);
  await recordAttendance(request, manager, {
    cast_id: first, actual_start_at: `${year}-08-31T22:00:00`, actual_end_at: `${year}-09-01T01:00:00`,
  });
  await withdrawCast(request, manager, first);
  const second = await createCast(request, manager, privateName);
  await acceptExistingCastInvitation(request, castToken, await issueCastInvitation(request, manager, second));
  await recordAttendance(request, manager, {
    cast_id: second, actual_start_at: `${year}-08-31T23:00:00`, actual_end_at: `${year}-09-01T02:00:00`,
  });
  await recordAttendance(request, manager, {
    cast_id: second, actual_start_at: `${year}-09-01T10:00:00`, actual_end_at: `${year}-09-01T14:00:00`,
  });
  for (const day of ['08-30', '09-03']) {
    await recordAttendance(request, manager, {
      cast_id: second, actual_start_at: `${year}-${day}T10:00:00`, actual_end_at: null,
    });
  }
  const candidates = await request.get('/api/store/monthly-remunerations/casts', {
    headers: storeHeaders(), params: { search: privateName },
  });
  expect(candidates.status(), await candidates.text()).toBe(200);
  person = (await candidates.json()).content[0].person_id;
  const guarantee = await request.post('/api/store/remuneration-guarantees', {
    headers: storeHeaders(), data: {
      person_id: person, effective_from: `${year}-08-31`, state: 'ACTIVE', daily_amount: 10000,
      reason: privateReason, expected_version: 0, request_id: randomUUID(),
    },
  });
  expect(guarantee.status(), await guarantee.text()).toBe(201);
  const unknown = await createCast(request, manager, privateName + '未設定');
  await acceptCastInvitation(request, await issueCastInvitation(request, manager, unknown),
    `report-unknown-${suffix}@example.test`, password, privateName + '未設定');
  await recordAttendance(request, manager, {
    cast_id: unknown, actual_start_at: `${year}-09-04T10:00:00`, actual_end_at: `${year}-09-04T14:00:00`,
  });
  course = await createCourse(request, manager, '帳票報酬コース');
  order = await createAgreedOrder(request, manager, second, course, '出力禁止顧客名', { businessDate: `${year}-09-01` });
  await completeAgreedOrder(request, manager, order);
  bonus = await createBonusAward(request, reader, person, `${year}-09-01`, 1500, privateReason);
  movedBonus = await createBonusAward(request, reader, person, `${year}-09-02`, 500, privateReason);
});

Then('報酬込み帳票は未確定と既知小計を区別し期間外の未知日を除く', async ({ request }) => {
  const data = await report(request);
  expect(data.total_remuneration).toBe(7000);
  expect(data.remuneration).toEqual({
    known_guarantee_total: 13000, guarantee_total: null, bonus_total: 2000, total: null,
    pending_attendance_days: 1, not_configured_days: 1,
  });
  expect(data.rows.content).toHaveLength(5);
  const bounded = await report(request, '08-31', '09-02');
  expect(bounded.remuneration).toEqual({
    known_guarantee_total: 13000, guarantee_total: 13000, bonus_total: 2000, total: 22000,
    pending_attendance_days: 0, not_configured_days: 0,
  });
  const noOrder = await report(request, '08-31', '08-31');
  expect(noOrder).toMatchObject({ total_order_count: 0, total_remuneration: 0,
    remuneration: { guarantee_total: 10000, bonus_total: 0, total: 10000 } });
  expect(noOrder.rows.content).toHaveLength(1);
  const bonusOnly = await report(request, '09-02', '09-02');
  expect(bonusOnly).toMatchObject({ total_order_count: 0,
    remuneration: { known_guarantee_total: 0, guarantee_total: 0, bonus_total: 500, total: 500 } });
  expect(bonusOnly.rows.content).toHaveLength(1);
  for (const group_by of ['month', 'store']) {
    const response = await request.get(base, { headers: headers(), params: { ...parameters('08-31', '09-02'), group_by } });
    expect(response.status(), await response.text()).toBe(200);
    const grouped = await response.json();
    expect(grouped.remuneration).toEqual(bounded.remuneration);
    expect(grouped.rows.content).toHaveLength(group_by === 'month' ? 2 : 1);
    expect(grouped.rows.content.reduce((sum: number, row: { remuneration: { total: number } }) => sum + row.remuneration.total, 0)).toBe(22000);
  }
  const store = await request.get('/api/store/operational-reports', { headers: storeHeaders(), params: parameters() });
  expect(store.status(), await store.text()).toBe(200);
  expect((await store.json()).remuneration).toEqual(data.remuneration);
});

Then('旧帳票の権限と形式を保ち報酬額の追加参照を守る', async ({ request }) => {
  for (const endpoint of [base, '/api/store/operational-reports']) {
    const endpointHeaders = endpoint === base ? headers(legacy) : { ...STORE_HEADERS, ...headers(legacy) };
    const denied = await request.get(endpoint, { headers: endpointHeaders, params: parameters() });
    expect(denied.status(), await denied.text()).toBe(403);
    for (const format of ['csv', 'xlsx']) {
      const deniedExport = await request.get(endpoint + '/exports', { headers: endpointHeaders, params: { ...parameters(), format } });
      expect(deniedExport.status(), await deniedExport.text()).toBe(403);
    }
    const oldParams = { from: parameters().from, to: parameters().to, group_by: 'day' };
    const response = await request.get(endpoint, { headers: endpointHeaders, params: oldParams });
    expect(response.status(), await response.text()).toBe(200);
    const old = await response.json();
    expect(old.total_remuneration).toBe(7000);
    expect(old).not.toHaveProperty('remuneration');
    expect(old.rows.content).toHaveLength(1);
    expect(old.rows.content[0]).not.toHaveProperty('remuneration');
    const csv = await request.get(endpoint + '/exports', { headers: endpointHeaders, params: { ...oldParams, format: 'csv' } });
    expect(csv.status(), await csv.text()).toBe(200);
    const text = (await csv.body()).toString('utf8');
    expect(text.split('\r\n')[0].split(',')).toHaveLength(16);
    expect(text).not.toContain('remuneration_day');
    expect(text).not.toContain(bonus.id);
  }
  expect((await request.get(base, { headers: headers(), params: { ...parameters(), store_id: foreign } })).status()).toBe(403);
  expect((await request.get('/api/store/operational-reports', {
    headers: { ...storeHeaders(), 'X-Store-ID': foreign }, params: parameters(),
  })).status()).toBe(403);
});

Then('報酬込みCSVとExcelは最小根拠と未知金額を一致させる', async ({ request, $testInfo }) => {
  for (const format of ['csv', 'xlsx']) {
    const response = await request.get(base + '/exports', {
      headers: headers(), params: { ...parameters(), format, page: 99, size: 1 },
    });
    expect(response.status(), await response.text()).toBe(200);
    expect(response.headers()['cache-control']).toBe('no-store');
    const bytes = await response.body();
    const text = format === 'csv' ? bytes.toString('utf8') :
      [1, 2, 3, 4, 5].map(n => zipEntry(bytes, `xl/worksheets/sheet${n}.xml`)).join('\n');
    expect(text).toContain(bonus.id);
    expect(text).toContain(movedBonus.id);
    expect(text).toContain('PENDING_ATTENDANCE');
    expect(text).toContain('NOT_CONFIGURED');
    expect(text).not.toContain(privateName);
    expect(text).not.toContain(privateReason);
    expect(text).not.toContain('出力禁止顧客名');
    expect(text).not.toContain('actor_id');
    expect(text).not.toContain(`${year}-08-30`);
    if (format === 'csv') {
      expect(text.startsWith('\ufeff')).toBe(true);
      expect(text).toContain('completed-orders-remuneration-current-v2');
      expect(text.split('\r\n').filter(line => line.startsWith('"\'remuneration_day"'))).toHaveLength(5);
      expect(text.split('\r\n').filter(line => line.startsWith('"\'bonus"'))).toHaveLength(2);
      const totals = text.split('\r\n')[1].split(',');
      expect(totals.slice(16, 22)).toEqual(['\"13000\"', '\"\"', '\"2000\"', '\"\"', '\"1\"', '\"1\"']);
    } else {
      expect(zipEntry(bytes, 'xl/workbook.xml')).toContain('日別報酬根拠');
      expect(zipEntry(bytes, 'xl/workbook.xml')).toContain('ボーナス根拠');
      const totals = zipEntry(bytes, 'xl/worksheets/sheet1.xml');
      expect(Number(totals.match(/<c r="Q2" t="n"><v>([^<]+)<\/v>/)?.[1])).toBe(13000);
      expect(totals).not.toMatch(/<c r="[RT]2" t="n">/);
      expect(text).not.toContain('<f>');
    }
    await $testInfo.attach(`報酬込み帳票.${format}`, { body: bytes, contentType: response.headers()['content-type'] });
  }
});

Then('報酬込み画面は条件切替と連打と遅延応答を安全に扱う', async ({ page, $testInfo }) => {
  await page.goto(PLATFORM_URL + '/platform/login');
  await page.getByLabel('メールアドレス', { exact: true }).fill(readerEmail);
  await page.getByLabel('パスワード', { exact: true }).fill(readerPassword);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login$/, { timeout: 15000 });
  await page.goto(PLATFORM_URL + '/platform/operational-reports');
  const from = page.getByLabel('開始営業日'), to = page.getByLabel('終了営業日');
  const toggle = page.getByRole('checkbox', { name: '保証不足分・ボーナスを含める' });
  const submit = page.getByRole('button', { name: '照会', exact: true });
  const total = page.getByLabel('報酬合計（円）', { exact: true });
  await from.fill(`${year}-08-31`);
  await to.fill(`${year}-09-04`);
  await toggle.check();
  await submit.click();
  await expect(total).toHaveText('未確定');
  await expect(page.getByLabel('保証不足分（円）', { exact: true })).toHaveText('未確定');
  await expect(page.getByLabel('ボーナス（円）', { exact: true })).toHaveText('2,000');
  await expect(page.getByText('保証不足分の既知小計: 13,000 円', { exact: true })).toBeVisible();
  for (const theme of ['light', 'dark']) {
    await page.setViewportSize({ width: theme === 'dark' ? 390 : 1280, height: 844 });
    await page.evaluate(t => document.documentElement.classList.toggle('dark', t === 'dark'), theme);
    await from.focus();
    await page.keyboard.press('Tab');
    await expect(to).toBeFocused();
    await $testInfo.attach(`報酬込み帳票-${theme}`, {
      body: await page.screenshot({ fullPage: true, animations: 'disabled' }), contentType: 'image/png',
    });
  }
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.evaluate(() => document.documentElement.classList.remove('dark'));
  let releaseRead!: () => void, readFetched!: () => void, readFinished!: () => void;
  const readGate = new Promise<void>(resolve => { releaseRead = resolve; });
  const fetched = new Promise<void>(resolve => { readFetched = resolve; });
  const finished = new Promise<void>(resolve => { readFinished = resolve; });
  await page.route('**/api/platform/operational-reports?*', async route => {
    const url = new URL(route.request().url());
    if (url.searchParams.get('from') !== `${year}-08-31` || url.searchParams.get('to') !== `${year}-08-31`) {
      await route.continue();
      return;
    }
    const response = await route.fetch();
    readFetched();
    await readGate;
    try { await route.fulfill({ response }); } finally { readFinished(); }
  });
  try {
    await to.fill(`${year}-08-31`);
    await submit.click();
    await fetched;
    await from.fill(`${year}-09-02`);
    await to.fill(`${year}-09-02`);
    await submit.click();
    await expect(total).toHaveText('500');
    releaseRead();
    await finished;
    await expect(total).toHaveText('500');
  } finally {
    releaseRead();
    await page.unroute('**/api/platform/operational-reports?*');
  }
  await submit.click();
  await expect(total).toHaveText('500');
  await submit.click();
  await expect(total).toHaveText('500');
  const store = page.getByLabel('店舗ID（空欄は授権全店）');
  await store.fill(foreign);
  const denied = page.waitForResponse(response => response.url().includes('/operational-reports?') && response.status() === 403);
  await submit.click();
  await denied;
  await expect(total).toHaveCount(0);
  await store.fill(STORE1_ID);
  await submit.click();
  await expect(total).toHaveText('500');

  let exportCount = 0, downloads = 0, releaseExport!: () => void, exportFetched!: () => void, exportFinished!: () => void;
  const exportGate = new Promise<void>(resolve => { releaseExport = resolve; });
  const fetchedExport = new Promise<void>(resolve => { exportFetched = resolve; });
  const finishedExport = new Promise<void>(resolve => { exportFinished = resolve; });
  const onDownload = () => { downloads++; };
  page.on('download', onDownload);
  await page.route('**/api/platform/operational-reports/exports?*', async route => {
    exportCount++;
    expect(new URL(route.request().url()).searchParams.get('include_remuneration')).toBe('true');
    const response = await route.fetch();
    exportFetched();
    await exportGate;
    try { await route.fulfill({ response }); } finally { exportFinished(); }
  });
  try {
    const csv = page.getByRole('button', { name: 'CSV 全件出力', exact: true });
    await csv.click();
    await fetchedExport;
    await expect(csv).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Excel 全件出力', exact: true })).toBeDisabled();
    await csv.evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click(); });
    expect(exportCount).toBe(1);
    await toggle.uncheck();
    await submit.click();
    await expect(page.getByLabel('発生済み固定報酬（円）', { exact: true })).toHaveText('0');
    releaseExport();
    await finishedExport;
    await expect(total).toHaveCount(0);
    expect(downloads).toBe(0);
  } finally {
    releaseExport();
    await page.unroute('**/api/platform/operational-reports/exports?*');
    page.off('download', onDownload);
  }
  await toggle.check();
  await submit.click();
  await expect(total).toHaveText('500');
  const downloaded = page.waitForEvent('download');
  await page.getByRole('button', { name: 'CSV 全件出力', exact: true }).click();
  expect((await downloaded).suggestedFilename()).toBe(`operational-report-${year}-09-02-${year}-09-02.csv`);
});

Then('報酬込み帳票は受注と保証と付与を同じ時点で読む', async ({ request }) => {
  const lock = await database(), observer = await database();
  let pending: ReturnType<typeof request.get> | undefined;
  try {
    await lock.query('BEGIN');
    await lock.query('LOCK TABLE t_orders IN ACCESS EXCLUSIVE MODE');
    pending = request.get(base, { headers: headers(), params: parameters('08-31', '09-02') });
    await expect.poll(async () => {
      const result = await observer.query(`SELECT count(*)::int n FROM pg_stat_activity
        WHERE wait_event_type='Lock' AND application_name <> 'report-remuneration388-bdd' AND query LIKE '%t_orders%'`);
      return result.rows[0].n;
    }, { timeout: 5000 }).toBeGreaterThan(0);
    await lock.query('UPDATE t_orders SET total_fee=total_fee+100, accrued_remuneration=accrued_remuneration+100 WHERE id=$1', [order]);
    await lock.query('UPDATE t_guarantee_terms SET daily_amount=daily_amount+1000 WHERE person_id=$1', [person]);
    await lock.query('UPDATE t_bonus_awards SET amount=amount+100 WHERE id=$1', [bonus.id]);
    await lock.query('COMMIT');
    const response = await pending;
    expect(response.status(), await response.text()).toBe(200);
    expect(await response.json()).toMatchObject({ total_remuneration: 7000,
      remuneration: { guarantee_total: 13000, bonus_total: 2000, total: 22000 } });
    expect(await report(request, '08-31', '09-02')).toMatchObject({ total_remuneration: 7100,
      remuneration: { guarantee_total: 14900, bonus_total: 2100, total: 24100 } });
  } finally {
    await lock.query('ROLLBACK');
    await lock.query('UPDATE t_orders SET total_fee=12000, accrued_remuneration=7000 WHERE id=$1', [order]);
    await lock.query('UPDATE t_guarantee_terms SET daily_amount=10000 WHERE person_id=$1', [person]);
    await lock.query('UPDATE t_bonus_awards SET amount=1500 WHERE id=$1', [bonus.id]);
    await lock.end();
    await observer.end();
    if (pending) await pending.catch(() => undefined);
  }
});

When('受注と付与を訂正して無効化する', async ({ request }) => {
  await correctOrderExtension(request, manager, order, privateReason);
  expect(await report(request, '08-31', '09-02')).toMatchObject({ total_remuneration: 9000,
    remuneration: { guarantee_total: 11000, bonus_total: 2000, total: 22000 } });
  await invalidateOrder(request, manager, order, privateReason);
  expect(await report(request, '08-31', '09-02')).toMatchObject({ total_remuneration: 0,
    remuneration: { guarantee_total: 20000, bonus_total: 2000, total: 22000 } });
  const correction = await request.post(`/api/store/bonus-awards/${bonus.id}/corrections`, {
    headers: storeHeaders(), data: {
      award_date: `${year}-09-01`, amount: 2500, reason: privateReason, correction_reason: privateReason,
      expected_version: bonus.version, request_id: randomUUID(),
    },
  });
  expect(correction.status(), await correction.text()).toBe(201);
  bonus = (await correction.json()).bonus;
  expect((await report(request, '08-31', '09-02')).remuneration.total).toBe(23000);
  await cancelBonusAward(request, reader, bonus.id, bonus.version, privateReason);
  const move = await request.post(`/api/store/bonus-awards/${movedBonus.id}/corrections`, {
    headers: storeHeaders(), data: {
      award_date: `${year}-10-01`, amount: 500, reason: privateReason, correction_reason: privateReason,
      expected_version: movedBonus.version, request_id: randomUUID(),
    },
  });
  expect(move.status(), await move.text()).toBe(201);
  movedBonus = (await move.json()).bonus;
});

Then('報酬込み帳票は原期間を再計算し付与の移動先だけを更新する', async ({ request }) => {
  expect(await report(request, '08-31', '09-02')).toMatchObject({ total_order_count: 0, invalidated_order_count: 1,
    remuneration: { guarantee_total: 20000, bonus_total: 0, total: 20000 } });
  expect(await report(request, '10-01', '10-01')).toMatchObject({ total_order_count: 0,
    remuneration: { guarantee_total: 0, bonus_total: 500, total: 500 } });
  await cancelBonusAward(request, reader, movedBonus.id, movedBonus.version, privateReason);
  expect((await report(request, '10-01', '10-01')).remuneration.total).toBe(0);
});

Then('報酬込み帳票はExcelの精度と安全整数の合計上限を守る', async ({ request }) => {
  const excelUnsafe = await createBonusAward(request, reader, person, `${year}-10-01`, 1000000000000001, privateReason);
  try {
    expect((await report(request, '10-01', '10-01')).remuneration.bonus_total).toBe(1000000000000001);
    const csv = await request.get(base + '/exports', { headers: headers(), params: { ...parameters('10-01', '10-01'), format: 'csv' } });
    expect(csv.status(), await csv.text()).toBe(200);
    expect((await csv.body()).toString()).toContain('1000000000000001');
    const xlsx = await request.get(base + '/exports', { headers: headers(), params: { ...parameters('10-01', '10-01'), format: 'xlsx' } });
    expect(xlsx.status(), await xlsx.text()).toBe(503);
  } finally { await cancelBonusAward(request, reader, excelUnsafe.id, excelUnsafe.version, privateReason); }
  const boundary = await createBonusAward(request, reader, person, `${year}-10-01`, Number.MAX_SAFE_INTEGER, privateReason);
  const overflow = await createBonusAward(request, reader, person, `${year}-10-01`, 1, privateReason);
  try {
    for (const format of [undefined, 'csv', 'xlsx']) {
      const response = await request.get(base + (format ? '/exports' : ''), {
        headers: headers(), params: { ...parameters('10-01', '10-01'), ...(format ? { format } : {}) },
      });
      expect(response.status(), await response.text()).toBe(503);
    }
  } finally {
    await cancelBonusAward(request, reader, boundary.id, boundary.version, privateReason);
    await cancelBonusAward(request, reader, overflow.id, overflow.version, privateReason);
  }
  expect((await report(request, '10-01', '10-01')).remuneration.total).toBe(0);
});

After({ tags: '@operational-report-remuneration' }, async ({ request }) => {
  if (!course) return;
  await deleteService(request, manager, course, 1);
  course = '';
});
