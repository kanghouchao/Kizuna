import { expect, type APIRequestContext } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { randomUUID } from 'node:crypto';
import { Client } from 'pg';
import { zipEntry } from './report-export';
import { PLATFORM_URL } from '../base-url';
import {
  ADMIN_PASSWORD, STORE1_ID, STORE_HEADERS, loginAsStoreAdmin, loginPlatformUser,
  createPermissionRole, createPlatformStaffFixture, getAuthorizedStores,
  createAdvertisingCost, updateAdvertisingCost, deleteAdvertisingCost,
  createCast, issueCastInvitation, acceptCastInvitation, recordAttendance,
  createCourse, createAgreedOrder, completeAgreedOrder, createBonusAward, cancelBonusAward, deleteService,
  getRemunerationCandidates, createRemunerationGuarantee, type AdvertisingCostValues,
} from './store-api';

const { Given, When, Then, After } = createBdd();
const platform = '/api/platform/operational-reports', store = '/api/store/operational-reports';
let manager = '', full = '', restricted = '', storeOnly = '', platformOnly = '', viewOnly = '', exportOnly = '', legacy = '', noRemuneration = '';
let year = '', foreign = '', email = '', password = '', course = '', order = '', attendance = '', person = 0;
let sales = { id: '', version: 0 }, recruitment = { id: '', version: 0 }, bonus = { id: '', version: 0 };
let archivedCsv = '';
const secret = '=非公開の媒体・代理店・理由';
const values = (amount: number, category: AdvertisingCostValues['category'] = 'SALES'): AdvertisingCostValues => ({
  category, amount, media_name: secret, agency_name: secret, plan_name: secret, inquiry_count: null,
});
const headers = (token = full, endpoint = platform, storeId = STORE1_ID) => ({
  ...(endpoint === store ? { ...STORE_HEADERS, 'X-Store-ID': storeId } : {}), Authorization: `Bearer ${token}`,
});
const parameters = (overrides: Record<string, string | boolean | number> = {}) => ({
  from: `${year}-02-01`, to: `${year}-02-29`, group_by: 'month',
  include_advertising: true, include_remuneration: true, ...overrides,
});
async function report(request: APIRequestContext, overrides: Record<string, string | boolean | number> = {}) {
  const response = await request.get(platform, { headers: headers(), params: parameters(overrides) });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}
async function database() {
  if (process.env.PGHOST !== 'database') throw new Error('専用 E2E DB でのみ実行できます');
  const db = new Client({ application_name: 'report-advertising388-bdd' });
  await db.connect();
  return db;
}
const recorded = (count: number, salesAmount: number, recruitmentAmount = 0) => ({
  status: count ? 'RECORDED' : 'NO_RECORDS', entry_count: count, sales_amount: salesAmount,
  recruitment_amount: recruitmentAmount, recorded_total_amount: salesAmount + recruitmentAmount,
});
const notApplicable = (status: string) => ({
  status, entry_count: null, sales_amount: null, recruitment_amount: null, recorded_total_amount: null,
});

Given('広告費帳票用の二店舗に受注と保証と賞与と月次費用がある', async ({ request, $testInfo }) => {
  $testInfo.setTimeout(180000);
  year = String(1924 - $testInfo.retry * 4);
  course = '';
  manager = await loginAsStoreAdmin(request);
  const owner = await loginPlatformUser(request, 'admin@kizuna.test', ADMIN_PASSWORD);
  foreign = String((await getAuthorizedStores(request, manager)).find(s => String(s.id) !== STORE1_ID)!.id);
  const suffix = randomUUID();
  password = randomUUID();
  const common = ['ORDER_MANAGE', 'ORDER_SET_MANAGE', 'STORE_MENU_VIEW', 'PLATFORM_MENU_VIEW',
    'OPERATIONAL_REPORT_VIEW', 'OPERATIONAL_REPORT_EXPORT', 'REMUNERATION_VIEW'];
  const read = ['ADVERTISING_COST_VIEW', 'ADVERTISING_COST_SET_VIEW'];
  const write = ['ADVERTISING_COST_EXPORT', 'ADVERTISING_COST_SET_EXPORT'];
  const permissions = [
    [...read, ...write, 'ADVERTISING_COST_MANAGE', 'GUARANTEE_MANAGE', 'BONUS_AWARD', 'REMUNERATION_CORRECT'],
    [...read, ...write], ['ADVERTISING_COST_VIEW', 'ADVERTISING_COST_EXPORT'],
    ['ADVERTISING_COST_SET_VIEW', 'ADVERTISING_COST_SET_EXPORT'], read, write, [], [...read, ...write],
  ];
  const tokens: string[] = [];
  for (const [index, additional] of permissions.entries()) {
    const role = await createPermissionRole(request, owner, `広告帳票-${index}-${suffix}`,
      [...(index === 7 ? common.filter(permission => permission !== 'REMUNERATION_VIEW') : common), ...additional]);
    const address = `advertising-report-${index}-${suffix}@example.test`;
    if (index === 0) email = address;
    await createPlatformStaffFixture(request, owner, address, password, [role],
      index === 1 ? [Number(STORE1_ID)] : [Number(STORE1_ID), Number(foreign)]);
    tokens.push(await loginPlatformUser(request, address, password));
  }
  [full, restricted, storeOnly, platformOnly, viewOnly, exportOnly, legacy, noRemuneration] = tokens;
  sales = await createAdvertisingCost(request, full, `${year}-02`, values(400));
  recruitment = await createAdvertisingCost(request, full, `${year}-02`, { ...values(600, 'RECRUITMENT'), inquiry_count: 0 });
  await createAdvertisingCost(request, full, `${year}-03`, values(200));
  await createAdvertisingCost(request, full, `${year}-02`, values(800), foreign);
  await createAdvertisingCost(request, full, `${year}-04`, values(0));
  const cast = await createCast(request, manager, `広告帳票本人-${suffix}`);
  await acceptCastInvitation(request, await issueCastInvitation(request, manager, cast),
    `advertising-cast-${suffix}@example.test`, password, `広告帳票本人-${suffix}`);
  attendance = (await recordAttendance(request, manager, { cast_id: cast,
    actual_start_at: `${year}-02-15T10:00:00`, actual_end_at: `${year}-02-15T14:00:00` })).id;
  const candidates = await getRemunerationCandidates(request, full, `広告帳票本人-${suffix}`);
  person = candidates.content[0].person_id;
  await createRemunerationGuarantee(request, full, {
    person_id: person, effective_from: `${year}-02-01`, state: 'ACTIVE', daily_amount: 10000,
    reason: secret, expected_version: 0,
  });
  course = await createCourse(request, manager, '広告帳票コース');
  order = await createAgreedOrder(request, manager, cast, course, secret, { businessDate: `${year}-02-15` });
  await completeAgreedOrder(request, manager, order);
  bonus = await createBonusAward(request, full, person, `${year}-02-15`, 500, secret);
});

Then('広告費帳票は整月と閏年と登録なしを日割りせず区別する', async ({ request }) => {
  const data = await report(request);
  expect(data.advertising).toEqual(recorded(3, 1200, 600));
  expect(data.remuneration.total).toBe(10500);
  expect(data.total_remuneration).toBe(7000);
  expect(data.rows.content).toHaveLength(2);
  const costOnly = data.rows.content.find((row: { store_id: number }) => String(row.store_id) === foreign);
  expect(costOnly).toMatchObject({ order_count: 0, advertising: recorded(1, 800) });
  const month = await report(request, { from: `${year}-03-01`, to: `${year}-03-31` });
  expect(month).toMatchObject({ total_order_count: 0, advertising: recorded(1, 200) });
  expect(month.rows.content).toHaveLength(1);
  expect((await report(request, { from: `${year}-04-01`, to: `${year}-04-30` })).advertising).toEqual(recorded(1, 0));
  const empty = await report(request, { from: `${year}-05-01`, to: `${year}-05-31` });
  expect(empty.advertising).toEqual(recorded(0, 0));
  expect(empty.rows.content).toHaveLength(0);
  for (const group_by of ['month', 'store']) {
    const annual = await report(request, { from: `${year}-01-01`, to: `${year}-12-31`, group_by });
    expect(annual.advertising).toEqual(recorded(5, 1400, 600));
    expect(annual.rows.content).toHaveLength(group_by === 'month' ? 4 : 2);
    expect(annual.rows.content.reduce((sum: number, row: { advertising: { recorded_total_amount: number } }) =>
      sum + row.advertising.recorded_total_amount, 0)).toBe(2000);
  }
  for (const [overrides, status] of [
    [{ from: `${year}-02-02`, to: `${year}-03-31` }, 'NOT_APPLICABLE_PARTIAL_MONTH'],
    [{ to: `${year}-02-28` }, 'NOT_APPLICABLE_PARTIAL_MONTH'],
    [{ group_by: 'day' }, 'NOT_APPLICABLE_DAY_GROUPING'],
    [{ from: `${year}-02-02`, group_by: 'day' }, 'NOT_APPLICABLE_PARTIAL_MONTH'],
  ] as const) {
    const result = await report(request, overrides);
    expect(result.advertising).toEqual(notApplicable(status));
    expect(result.rows.content).toHaveLength(1);
    expect(result.rows.content[0].advertising).toEqual(notApplicable(status));
  }
  const crossing = await report(request, { from: `${Number(year) - 1}-12-01`, to: `${year}-03-31` });
  expect(crossing.advertising).toEqual(recorded(4, 1400, 600));
  const page = await report(request, { size: 1, page: 1 });
  expect(page.rows.content).toHaveLength(1);
  expect(page.rows.total_elements).toBe(2);
  expect(page.advertising).toEqual(data.advertising);
});

Then('広告費帳票は店と本部の閲覧出力権限を独立して検証する', async ({ request }) => {
  for (const endpoint of [store, platform]) {
    const allowed = endpoint === store ? storeOnly : platformOnly;
    const opposite = endpoint === store ? platformOnly : storeOnly;
    for (const token of [legacy, opposite, exportOnly]) {
      for (const condition of [{}, { group_by: 'day' }, { from: `${year}-02-02` }] as Record<string, string>[]) {
        for (const format of [undefined, 'csv', 'xlsx']) {
          const response = await request.get(endpoint + (format ? '/exports' : ''), {
            headers: headers(token, endpoint), params: parameters({ ...condition, ...(format ? { format } : {}) }),
          });
          expect(response.status(), await response.text()).toBe(403);
        }
      }
    }
    for (const format of [undefined, 'csv', 'xlsx']) {
      for (const include_remuneration of [false, true]) {
        const response = await request.get(endpoint + (format ? '/exports' : ''), {
          headers: headers(noRemuneration, endpoint),
          params: parameters({ include_remuneration, ...(format ? { format } : {}) }),
        });
        expect(response.status(), await response.text()).toBe(include_remuneration ? 403 : 200);
      }
    }
    for (const token of [allowed, viewOnly]) {
      const response = await request.get(endpoint, { headers: headers(token, endpoint), params: parameters() });
      expect(response.status(), await response.text()).toBe(200);
    }
    for (const format of ['csv', 'xlsx']) {
      expect((await request.get(endpoint + '/exports', {
        headers: headers(viewOnly, endpoint), params: parameters({ format }),
      })).status()).toBe(403);
      const allowedExport = await request.get(endpoint + '/exports', {
        headers: headers(allowed, endpoint), params: parameters({ format }),
      });
      expect(allowedExport.status(), await allowedExport.text()).toBe(200);
      const old = await request.get(endpoint + '/exports', {
        headers: headers(legacy, endpoint), params: parameters({ include_advertising: false, format }),
      });
      expect(old.status(), await old.text()).toBe(200);
    }
  }
  const scoped = await request.get(platform, { headers: headers(restricted), params: parameters() });
  expect((await scoped.json()).advertising).toEqual(recorded(2, 400, 600));
  for (const endpoint of [platform, store]) {
    for (const format of [undefined, 'csv']) {
      expect((await request.get(endpoint + (format ? '/exports' : ''), {
        headers: headers(restricted, endpoint, foreign),
        params: parameters({ store_id: foreign, ...(format ? { format } : {}) }),
      })).status()).toBe(403);
    }
  }
});

Then('広告費帳票の四つの選択条件は旧形式と最小根拠を保つ', async ({ request, $testInfo }) => {
  for (const include_remuneration of [false, true]) {
    for (const include_advertising of [false, true]) {
      const selection = { include_remuneration, include_advertising };
      const data = await report(request, selection);
      expect(Object.hasOwn(data, 'remuneration')).toBe(include_remuneration);
      expect(Object.hasOwn(data, 'advertising')).toBe(include_advertising);
      const basis = include_advertising ? (include_remuneration ? 'completed-orders-remuneration-advertising-current-v3' :
        'completed-orders-advertising-current-v3') : (include_remuneration ? 'completed-orders-remuneration-current-v2' : 'completed-orders-current-v1');
      expect(data.basis).toBe(basis);
      const columns = 16 + (include_remuneration ? 17 : 0) + (include_advertising ? 10 : 0);
      const sheets = 3 + (include_remuneration ? 2 : 0) + (include_advertising ? 1 : 0);
      for (const format of ['csv', 'xlsx']) {
        const response = await request.get(platform + '/exports', {
          headers: headers(), params: parameters({ ...selection, format, page: 99, size: 1 }),
        });
        expect(response.status(), await response.text()).toBe(200);
        expect(response.headers()['cache-control']).toBe('no-store');
        const bytes = await response.body();
        const text = format === 'csv' ? bytes.toString() :
          Array.from({ length: sheets }, (_, n) => zipEntry(bytes, `xl/worksheets/sheet${n + 1}.xml`)).join('\n');
        expect(text).not.toContain(secret);
        expect(text).not.toContain('inquiry_count');
        expect(text).not.toContain('actor_id');
        expect(text.includes(sales.id)).toBe(include_advertising);
        expect(text.includes(bonus.id)).toBe(include_remuneration);
        if (format === 'csv') {
          expect(bytes.subarray(0, 3).toString('hex')).toBe('efbbbf');
          expect(text.split('\r\n')[0].split(',')).toHaveLength(columns);
          expect(text.split('\r\n').filter(line => line.startsWith('"\'advertising_cost"'))).toHaveLength(include_advertising ? 3 : 0);
          if (include_advertising && include_remuneration) archivedCsv = text;
        } else {
          const workbook = zipEntry(bytes, 'xl/workbook.xml');
          expect(workbook.match(/<sheet /g)).toHaveLength(sheets);
          expect(workbook.includes('広告費根拠')).toBe(include_advertising);
          expect(text).not.toContain('<f>');
        }
        if (include_advertising && include_remuneration) await $testInfo.attach(`広告費込み帳票.${format}`, {
          body: bytes, contentType: response.headers()['content-type'],
        });
      }
    }
  }
  for (const format of ['csv', 'xlsx']) {
    const response = await request.get(platform + '/exports', {
      headers: headers(), params: parameters({ group_by: 'day', include_remuneration: false, format }),
    });
    expect(response.status(), await response.text()).toBe(200);
    const bytes = await response.body();
    if (format === 'csv') {
      const lines = bytes.toString().split('\r\n');
      expect(lines[1]).toContain('NOT_APPLICABLE_DAY_GROUPING');
      expect(lines[1].split(',').slice(17, 21)).toEqual(['""', '""', '""', '""']);
      expect(lines.filter(line => line.startsWith('"\'advertising_cost"'))).toHaveLength(0);
    } else {
      expect(zipEntry(bytes, 'xl/worksheets/sheet4.xml').match(/<row /g)).toHaveLength(1);
    }
  }
});

Then('広告費帳票の画面は条件切替と連打と遅延応答を安全に扱う', async ({ page, $testInfo }) => {
  await page.goto(PLATFORM_URL + '/platform/login');
  await page.getByLabel('メールアドレス', { exact: true }).fill(email);
  await page.getByLabel('パスワード', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login$/, { timeout: 15000 });
  await page.goto(PLATFORM_URL + '/platform/operational-reports');
  const from = page.getByLabel('開始営業日'), to = page.getByLabel('終了営業日');
  const advertising = page.getByRole('checkbox', { name: '広告費を含める' });
  const remuneration = page.getByRole('checkbox', { name: '保証不足分・ボーナスを含める' });
  const submit = page.getByRole('button', { name: '照会', exact: true });
  const total = page.getByLabel('広告登録額合計（円）', { exact: true });
  const grouping = page.getByLabel('集計単位');
  const storeId = page.getByLabel('店舗ID（空欄は授権全店）');
  await expect(advertising).not.toBeChecked();
  await expect(remuneration).not.toBeChecked();
  await from.fill(`${year}-02-01`);
  await to.fill(`${year}-02-29`);
  await advertising.check();
  await remuneration.check();
  await submit.click();
  await expect(total).toHaveText('');
  await expect(page.getByText('対象外: 広告費は日別に配分しません。月別または店舗別で照会してください。').first()).toBeVisible();
  await grouping.click();
  await page.getByRole('option', { name: '月別', exact: true }).click();
  await submit.click();
  await expect(total).toHaveText('1,800');
  await expect(page.getByLabel('報酬合計（円）', { exact: true })).toHaveText('10,500');
  await storeId.fill(foreign);
  await submit.click();
  await expect(total).toHaveText('800');
  await storeId.fill(STORE1_ID);
  await submit.click();
  await expect(total).toHaveText('1,000');
  await from.fill(`${year}-02-02`);
  await submit.click();
  await expect(total).toHaveText('');
  await expect(page.getByText('対象外: 月初から月末までの完全な月を指定してください。').first()).toBeVisible();
  await from.fill(`${year}-02-01`);
  await grouping.click();
  await page.getByRole('option', { name: '店舗別', exact: true }).click();
  await submit.click();
  await expect(total).toHaveText('1,000');
  for (const theme of ['light', 'dark']) {
    await page.setViewportSize({ width: theme === 'dark' ? 390 : 1280, height: 844 });
    await page.evaluate(t => document.documentElement.classList.toggle('dark', t === 'dark'), theme);
    await from.focus();
    await page.keyboard.press('Tab');
    await expect(to).toBeFocused();
    if (theme === 'dark') await total.scrollIntoViewIfNeeded();
    await $testInfo.attach(`広告費込み帳票-${theme}`, {
      body: await page.screenshot({ fullPage: true, animations: 'disabled' }), contentType: 'image/png',
    });
  }
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.evaluate(() => document.documentElement.classList.remove('dark'));
  let release!: () => void, fetched!: () => void, finished!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  const received = new Promise<void>(resolve => { fetched = resolve; });
  const completed = new Promise<void>(resolve => { finished = resolve; });
  await page.route('**/api/platform/operational-reports?*', async route => {
    if (new URL(route.request().url()).searchParams.get('from') !== `${year}-02-01`) return route.continue();
    const response = await route.fetch();
    fetched();
    await gate;
    try { await route.fulfill({ response }); } finally { finished(); }
  });
  try {
    await submit.click();
    await received;
    await from.fill(`${year}-03-01`);
    await to.fill(`${year}-03-31`);
    await submit.click();
    await expect(total).toHaveText('200');
    release();
    await completed;
    await expect(total).toHaveText('200');
  } finally {
    release();
    await page.unroute('**/api/platform/operational-reports?*');
  }
  await submit.click();
  await expect(total).toHaveText('200');
  await submit.click();
  await expect(total).toHaveText('200');
  let count = 0, downloads = 0, releaseExport!: () => void, fetchedExport!: () => void, finishedExport!: () => void;
  const exportGate = new Promise<void>(resolve => { releaseExport = resolve; });
  const exportReceived = new Promise<void>(resolve => { fetchedExport = resolve; });
  const exportCompleted = new Promise<void>(resolve => { finishedExport = resolve; });
  const onDownload = () => { downloads++; };
  page.on('download', onDownload);
  await page.route('**/api/platform/operational-reports/exports?*', async route => {
    count++;
    const url = new URL(route.request().url());
    expect(url.searchParams.get('include_advertising')).toBe('true');
    expect(url.searchParams.get('include_remuneration')).toBe('true');
    const response = await route.fetch();
    fetchedExport();
    await exportGate;
    try { await route.fulfill({ response }); } finally { finishedExport(); }
  });
  try {
    const csv = page.getByRole('button', { name: 'CSV 全件出力', exact: true });
    await csv.click();
    await exportReceived;
    await expect(csv).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Excel 全件出力', exact: true })).toBeDisabled();
    await csv.evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click(); });
    expect(count).toBe(1);
    await advertising.uncheck();
    await remuneration.uncheck();
    await submit.click();
    await expect(total).toHaveCount(0);
    releaseExport();
    await exportCompleted;
    expect(downloads).toBe(0);
  } finally {
    releaseExport();
    await page.unroute('**/api/platform/operational-reports/exports?*');
    page.off('download', onDownload);
  }
  await advertising.check();
  await submit.click();
  await expect(total).toHaveText('200');
  const downloaded = page.waitForEvent('download');
  await page.getByRole('button', { name: 'CSV 全件出力', exact: true }).click();
  expect((await downloaded).suggestedFilename()).toBe(`operational-report-${year}-03-01-${year}-03-31.csv`);
});

Then('広告費帳票は受注と保証と賞与と費用を同じ時点で読む', async ({ request }) => {
  const lock = await database(), observer = await database();
  let pending: ReturnType<typeof request.get> | undefined;
  try {
    await lock.query('BEGIN');
    await lock.query('LOCK TABLE t_orders IN ACCESS EXCLUSIVE MODE');
    pending = request.get(platform, { headers: headers(), params: parameters({ store_id: STORE1_ID }) });
    await expect.poll(async () => {
      const result = await observer.query(`SELECT count(*)::int n FROM pg_stat_activity
        WHERE wait_event_type='Lock' AND application_name <> 'report-advertising388-bdd' AND query LIKE '%t_orders%'`);
      return result.rows[0].n;
    }, { timeout: 5000 }).toBeGreaterThan(0);
    await lock.query('UPDATE t_orders SET total_fee=total_fee+100, accrued_remuneration=accrued_remuneration+100 WHERE id=$1', [order]);
    await lock.query('UPDATE t_attendances SET actual_end_at=actual_end_at - interval \'1 hour\' WHERE id=$1', [attendance]);
    await lock.query('UPDATE t_guarantee_terms SET daily_amount=daily_amount+1000 WHERE person_id=$1', [person]);
    await lock.query('UPDATE t_bonus_awards SET amount=amount+100 WHERE id=$1', [bonus.id]);
    await lock.query('UPDATE t_advertising_costs SET amount=amount+100 WHERE id=$1', [sales.id]);
    await lock.query('COMMIT');
    const response = await pending;
    expect(response.status(), await response.text()).toBe(200);
    expect(await response.json()).toMatchObject({ total_remuneration: 7000,
      remuneration: { guarantee_total: 3000, bonus_total: 500, total: 10500 }, advertising: recorded(2, 400, 600) });
    expect(await report(request, { store_id: STORE1_ID })).toMatchObject({ total_remuneration: 7100,
      remuneration: { guarantee_total: 0, bonus_total: 600, total: 7700 }, advertising: recorded(2, 500, 600) });
  } finally {
    await lock.query('ROLLBACK');
    await lock.query('UPDATE t_orders SET total_fee=12000, accrued_remuneration=7000 WHERE id=$1', [order]);
    await lock.query('UPDATE t_attendances SET actual_end_at=$2 WHERE id=$1', [attendance, `${year}-02-15T14:00:00`]);
    await lock.query('UPDATE t_guarantee_terms SET daily_amount=10000 WHERE person_id=$1', [person]);
    await lock.query('UPDATE t_bonus_awards SET amount=500 WHERE id=$1', [bonus.id]);
    await lock.query('UPDATE t_advertising_costs SET amount=400 WHERE id=$1', [sales.id]);
    await lock.end();
    await observer.end();
    if (pending) await pending.catch(() => undefined);
  }
});

When('月次費用を原月で訂正して留痕削除する', async ({ request }) => {
  sales = await updateAdvertisingCost(request, full, sales, values(700, 'RECRUITMENT'), secret);
  expect((await report(request, { store_id: STORE1_ID })).advertising).toEqual(recorded(2, 0, 1300));
  await deleteAdvertisingCost(request, full, recruitment, secret);
});

Then('広告費帳票の再照会と再出力だけが原月の変更を反映する', async ({ request }) => {
  expect((await report(request, { store_id: STORE1_ID })).advertising).toEqual(recorded(1, 0, 700));
  expect((await report(request, { from: `${year}-03-01`, to: `${year}-03-31` })).advertising).toEqual(recorded(1, 200));
  const csv = await request.get(platform + '/exports', { headers: headers(), params: parameters({ format: 'csv' }) });
  expect(csv.status(), await csv.text()).toBe(200);
  const current = (await csv.body()).toString();
  expect(current).toContain(sales.id);
  expect(current).not.toContain(recruitment.id);
  expect(archivedCsv).toContain(recruitment.id);
  expect(archivedCsv).not.toEqual(current);
  await deleteAdvertisingCost(request, full, sales, secret);
  const empty = await report(request, { store_id: STORE1_ID });
  expect(empty.advertising).toEqual(recorded(0, 0));
  expect(empty.rows.content[0].advertising).toEqual(recorded(0, 0));
  expect(empty.remuneration.total).toBe(10500);
});

Then('広告費帳票の全件出力は2001行と精度と取得上限を守る', async ({ request, $testInfo }) => {
  const seed = await createAdvertisingCost(request, full, `${year}-06`, values(100));
  const prefix = '=ad388-' + randomUUID() + '-';
  const db = await database();
  const range = { from: `${year}-06-01`, to: `${year}-06-30`, include_remuneration: false };
  try {
    await db.query(`INSERT INTO t_advertising_costs(id,store_id,month,category,media_name,agency_name,plan_name,inquiry_count,amount,deleted,revision,version,created_at,updated_at)
      SELECT $2 || lpad(n::text,5,'0'),store_id,month,category,$3,NULL,NULL,NULL,100,false,0,0,created_at,updated_at
      FROM t_advertising_costs CROSS JOIN generate_series(1,2000) n WHERE id=$1`, [seed.id, prefix, secret]);
    expect((await report(request, range)).advertising).toEqual(recorded(2001, 200100));
    for (const format of ['csv', 'xlsx']) {
      const response = await request.get(platform + '/exports', {
        headers: headers(), params: parameters({ ...range, format, page: 99, size: 1 }),
      });
      expect(response.status(), await response.text()).toBe(200);
      const bytes = await response.body();
      if (format === 'csv') {
        const text = bytes.toString();
        expect(text.split('\r\n').filter(line => line.startsWith('"\'advertising_cost"'))).toHaveLength(2001);
        expect(text).toContain(`"'${prefix}02000"`);
        expect(text).not.toContain(secret);
      } else {
        const sheet = zipEntry(bytes, 'xl/worksheets/sheet4.xml');
        expect(sheet.match(/<row /g)).toHaveLength(2002);
        expect(sheet).toContain(prefix + '02000');
        expect(sheet).toContain('t="n"');
        expect(sheet).toContain('t="inlineStr"');
        expect(sheet).not.toContain('<f>');
        expect(sheet).not.toContain(secret);
      }
      await $testInfo.attach(`広告費2001行.${format}`, { body: bytes, contentType: response.headers()['content-type'] });
    }
    await db.query('UPDATE t_advertising_costs SET amount=2147483647 WHERE id=$1 OR id LIKE $2', [seed.id, prefix + '%']);
    expect((await report(request, range)).advertising).toEqual(recorded(2001, 2147483647 * 2001));
    await db.query('UPDATE t_advertising_costs SET amount=100 WHERE id=$1 OR id LIKE $2', [seed.id, prefix + '%']);
    const excelUnsafe = await createBonusAward(request, full, person, `${year}-06-01`, 1000000000000001, secret);
    try {
      const csv = await request.get(platform + '/exports', { headers: headers(), params: parameters({ ...range, include_remuneration: true, format: 'csv' }) });
      expect(csv.status(), await csv.text()).toBe(200);
      expect((await csv.body()).toString()).toContain('1000000000000001');
      const xlsx = await request.get(platform + '/exports', { headers: headers(), params: parameters({ ...range, include_remuneration: true, format: 'xlsx' }) });
      expect(xlsx.status(), await xlsx.text()).toBe(503);
      expect(xlsx.headers()['content-disposition']).toBeUndefined();
    } finally { await cancelBonusAward(request, full, excelUnsafe.id, excelUnsafe.version, secret); }
    const boundary = await createBonusAward(request, full, person, `${year}-06-01`, Number.MAX_SAFE_INTEGER, secret);
    const overflow = await createBonusAward(request, full, person, `${year}-06-01`, 1, secret);
    try {
      for (const format of [undefined, 'csv', 'xlsx']) {
        const response = await request.get(platform + (format ? '/exports' : ''), {
          headers: headers(), params: parameters({ ...range, include_remuneration: true, ...(format ? { format } : {}) }),
        });
        expect(response.status(), await response.text()).toBe(503);
        expect(response.headers()['content-disposition']).toBeUndefined();
      }
    } finally {
      await cancelBonusAward(request, full, boundary.id, boundary.version, secret);
      await cancelBonusAward(request, full, overflow.id, overflow.version, secret);
    }
    await db.query(`INSERT INTO t_advertising_costs(id,store_id,month,category,media_name,agency_name,plan_name,inquiry_count,amount,deleted,revision,version,created_at,updated_at)
      SELECT $2 || lpad(n::text,5,'0'),store_id,month,category,$3,NULL,NULL,NULL,100,false,0,0,created_at,updated_at
      FROM t_advertising_costs CROSS JOIN generate_series(2001,20000) n WHERE id=$1`, [seed.id, prefix, secret]);
    for (const format of [undefined, 'csv', 'xlsx']) {
      const response = await request.get(platform + (format ? '/exports' : ''), {
        headers: headers(), params: parameters({ ...range, ...(format ? { format } : {}) }),
      });
      expect(response.status(), await response.text()).toBe(503);
      expect(response.headers()['content-disposition']).toBeUndefined();
    }
    const omitted = await report(request, { ...range, include_advertising: false });
    expect(omitted).not.toHaveProperty('advertising');
    expect((await report(request, { ...range, group_by: 'day' })).advertising).toEqual(notApplicable('NOT_APPLICABLE_DAY_GROUPING'));
  } finally {
    await db.query('DELETE FROM t_advertising_costs WHERE id LIKE $1', [prefix + '%']);
    await db.query('UPDATE t_advertising_costs SET amount=100 WHERE id=$1', [seed.id]);
    await db.end();
  }
  expect((await report(request, range)).advertising).toEqual(recorded(1, 100));
  const recovered = await request.get(platform + '/exports', { headers: headers(), params: parameters({ ...range, format: 'xlsx' }) });
  expect(recovered.status(), await recovered.text()).toBe(200);
});

After({ tags: '@operational-report-advertising' }, async ({ request }) => {
  if (!course) return;
  await deleteService(request, manager, course, 1);
  course = '';
});
