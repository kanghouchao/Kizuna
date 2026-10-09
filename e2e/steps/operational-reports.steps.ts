import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { Client } from 'pg';
import { zipEntry } from './report-export';
import { writeFile } from 'node:fs/promises';
import { PLATFORM_URL } from '../base-url';
import { createCast, createCourse, createAgreedOrder, completeAgreedOrder, correctOrderExtension,
  invalidateOrder, cancelOrder, loginAsStoreAdmin, loginPlatformUser, getAuthorizedStores,
  STORE1_ID, STORE_HEADERS } from './store-api';

const { Given, When, Then } = createBdd();
const base = '/api/platform/operational-reports';
const params = { from: '1999-09-01', to: '1999-10-31', group_by: 'month' };
let manager = '', writer = '', viewer = '', secondReader = '', orderId = '', foreign = '', email = '', password = '', prefix = '';
const headers = (token: string) => ({ Authorization: 'Bearer ' + token });
async function database() {
  if (process.env.PGHOST !== 'database') throw new Error('専用 E2E DB でのみ実行できます');
  const db = new Client({ application_name: 'report388-bdd' });
  await db.connect(); return db;
}
Given('運営帳票用の完了受注と独立した閲覧出力権限がある', async ({ request, $testInfo }) => {
  $testInfo.setTimeout(120000);
  params.from = `${1999 - $testInfo.retry}-09-01`;
  params.to = `${1999 - $testInfo.retry}-10-31`;
  manager = await loginAsStoreAdmin(request);
  const admin = await loginPlatformUser(request, 'admin@kizuna.test', 'pass');
  const suffix = String(Date.now()); prefix = 'R388-' + suffix + '-'; password = 'Report-' + suffix + '-pass';
  const stores = await getAuthorizedStores(request, manager);
  foreign = String(stores.find(store => String(store.id) !== STORE1_ID)!.id);
  const cast = await createCast(request, manager, '帳票キャスト');
  const course = await createCourse(request, manager, '帳票コース');
  orderId = await createAgreedOrder(request, manager, cast, course, '出力禁止顧客氏名', { businessDate: params.from.slice(0, 4) + '-09-30' });
  await completeAgreedOrder(request, manager, orderId);
  await createAgreedOrder(request, manager, cast, course, '未完了', { businessDate: params.from.slice(0, 4) + '-09-30' });
  const cancelled = await createAgreedOrder(request, manager, cast, course, '取消', { businessDate: params.from.slice(0, 4) + '-09-30' });
  await cancelOrder(request, manager, cancelled, '帳票対象外を検証');
  const otherCast = await createCast(request, manager, '他店帳票', foreign);
  const otherCourse = await createCourse(request, manager, '他店帳票コース', foreign);
  const otherOrder = await createAgreedOrder(request, manager, otherCast, otherCourse, '他店', { businessDate: params.from.slice(0, 4) + '-09-30', storeId: foreign });
  await completeAgreedOrder(request, manager, otherOrder, foreign);
  const tokens: string[] = [];
  for (let index = 0; index < 3; index++) {
    const permissions = ['ORDER_SET_MANAGE', 'PLATFORM_MENU_VIEW', 'OPERATIONAL_REPORT_VIEW'];
    if (index !== 1) permissions.push('OPERATIONAL_REPORT_EXPORT');
    const role = await request.post('/api/platform/roles', { headers: headers(admin), data: { name: prefix + index, permissions } });
    expect(role.status(), await role.text()).toBe(201);
    const address = prefix + index + '@kizuna.test'; if (index === 0) email = address;
    const staff = await request.post('/api/platform/staff', { headers: headers(admin), data: {
      email: address, password, display_name: '帳票担当', role_ids: [(await role.json()).id],
      store_scope_type: 'SPECIFIC_STORES', store_ids: [Number(index === 2 ? foreign : STORE1_ID)],
    } });
    expect(staff.status(), await staff.text()).toBe(201);
    tokens.push(await loginPlatformUser(request, address, password));
  }
  [writer, viewer, secondReader] = tokens;
  const db = await database();
  try {
    // 実際に完了した受注を専用 DB 内で複製し、一覧上限を超える帳票だけを検証する。
    const metadata = await db.query(`SELECT attname FROM pg_attribute WHERE attrelid = 't_orders'::regclass
      AND attnum > 0 AND NOT attisdropped AND attgenerated = '' ORDER BY attnum`);
    const names = metadata.rows.map(row => '"' + String(row.attname).replaceAll('"', '""') + '"');
    await db.query(`INSERT INTO t_orders (${names.join(',')}) SELECT ${names.map(name => 'cloned.' + name).join(',')}
      FROM t_orders o CROSS JOIN generate_series(1, 2000) i CROSS JOIN LATERAL
      jsonb_populate_record(NULL::t_orders, to_jsonb(o) || jsonb_build_object('id', $2::text || lpad(i::text,4,'0'))) cloned WHERE o.id=$1`, [orderId, prefix]);
  } finally { await db.end(); }
});
When('運営帳票を画面とCSVとExcelで取得する', async ({ request, page, $testInfo }) => {
  await page.goto(PLATFORM_URL + '/platform/login');
  await page.getByLabel('メールアドレス', { exact: true }).fill(email);
  await page.getByLabel('パスワード', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login$/, { timeout: 15000 });
  await page.goto(PLATFORM_URL + '/platform/orders');
  await page.getByRole('link', { name: '運営金額集計', exact: true }).click();
  await page.getByLabel('開始営業日').fill(params.from); await page.getByLabel('終了営業日').fill(params.to);
  await page.getByRole('button', { name: '照会', exact: true }).click();
  await expect(page.getByLabel('有効完了件数', { exact: true })).toHaveText('2,001');
  await expect(page.getByLabel('請求額（円）', { exact: true })).toHaveText('24,012,000');
  for (const format of ['csv', 'xlsx']) {
    const response = await request.get(base + '/exports', { headers: headers(writer), params: { ...params, format, page: 99, size: 1 } });
    expect(response.status(), await response.text()).toBe(200);
    expect(response.headers()['cache-control']).toBe('no-store');
    const bytes = await response.body();
    if (format === 'csv') {
      const text = bytes.toString('utf8');
      expect(text.startsWith('\ufeff')).toBeTruthy();
      expect(text).toContain(prefix + '0001'); expect(text).toContain(prefix + '1000'); expect(text).toContain(prefix + '2000');
      expect(text).not.toContain('出力禁止顧客氏名');
      expect(text.split('\r\n').filter(line => line.startsWith('"\'order"'))).toHaveLength(2001);
     } else {
      const path = $testInfo.outputPath('operational-report.xlsx');
      await writeFile(path, bytes);
      const orders = zipEntry(bytes, 'xl/worksheets/sheet3.xml');
      const totals = zipEntry(bytes, 'xl/worksheets/sheet1.xml');
      expect(orders).toContain(prefix + '0001'); expect(orders).toContain(prefix + '1000'); expect(orders).toContain(prefix + '2000');
      expect((orders.match(/<row /g) ?? []).length).toBe(2002);
      expect(orders).not.toContain('<f>'); expect(orders).not.toContain('出力禁止顧客氏名');
      expect(orders).toContain('t="inlineStr"');
      expect(Number(totals.match(/<c r="O2" t="n"><v>([^<]+)<\/v>/)?.[1])).toBe(24012000);
      expect(Number(totals.match(/<c r="P2" t="n"><v>([^<]+)<\/v>/)?.[1])).toBe(14007000);
    }
    await $testInfo.attach('全件帳票.' + format, { body: bytes, contentType: response.headers()['content-type'] });
  }
  await page.setViewportSize({ width: 1024, height: 900 });
  for (const theme of ['light', 'dark']) {
    await page.evaluate(t => document.documentElement.classList.toggle('dark', t === 'dark'), theme);
    await page.getByLabel('開始営業日').focus(); await page.keyboard.press('Tab');
    await expect(page.getByLabel('終了営業日')).toBeFocused();
    await $testInfo.attach('帳票-' + theme, { body: await page.screenshot({ fullPage: true, animations: 'disabled', path: $testInfo.outputPath('operational-report-' + theme + '.png') }), contentType: 'image/png' });
  }
});
Then('運営帳票は全件同一時点で授権内だけを出力する', async ({ request }) => {
  expect((await request.get(base, { params })).status()).toBe(401);
  const parts = writer.split('.');
  const forgedClaims = JSON.parse(Buffer.from(parts[1], 'base64url').toString());
  forgedClaims.storeScopeType = 'ALL_STORES';
  parts[1] = Buffer.from(JSON.stringify(forgedClaims)).toString('base64url');
  expect((await request.get(base, { headers: headers(parts.join('.')), params })).status()).toBe(401);

  expect((await request.get(base + '/exports', { headers: headers(viewer), params: { ...params, format: 'csv' } })).status()).toBe(403);
  expect((await request.get(base, { headers: headers(writer), params: { ...params, store_id: foreign } })).status()).toBe(403);
  expect((await request.get(base, { headers: headers(manager), params })).status()).toBe(403);
  expect((await request.get('/api/store/operational-reports', { headers: { ...STORE_HEADERS, ...headers(writer) }, params })).status()).toBe(403);
  const other = await request.get(base, { headers: headers(secondReader), params });
  expect(await other.json()).toMatchObject({ total_order_count: 1, total_fee: 12000, stores: [{ store_id: Number(foreign) }] });
  const empty = await request.get(base, { headers: headers(writer), params: { from: '1998-01-01', to: '1998-01-31' } });
  expect(await empty.json()).toMatchObject({ total_order_count: 0, total_fee: 0, stores: [{ store_id: Number(STORE1_ID) }], rows: { content: [] } });
  const lock = await database(); const observer = await database();
  let pending: ReturnType<typeof request.get> | undefined;
  try {
    await lock.query('BEGIN'); await lock.query('LOCK TABLE t_orders IN ACCESS EXCLUSIVE MODE');
    pending = request.get(base, { headers: headers(writer), params });
    await expect.poll(async () => {
      const result = await observer.query(`SELECT count(*)::int n FROM pg_stat_activity WHERE wait_event_type='Lock'
        AND application_name <> 'report388-bdd' AND query LIKE '%t_orders%'`);
      return result.rows[0].n;
    }, { timeout: 5000 }).toBeGreaterThan(0);
    await lock.query('UPDATE t_orders SET total_fee=total_fee+100, accrued_remuneration=accrued_remuneration+50 WHERE id=$1', [prefix + '0001']);
    await lock.query('COMMIT');
    const response = await pending;
    expect(response.status(), await response.text()).toBe(200);
    const result = await response.json();
    expect(result.total_fee).toBe(24012000); expect(result.rows.content[0].total_fee).toBe(24012000);
    const after = await request.get(base, { headers: headers(writer), params });
    expect((await after.json()).total_fee).toBe(24012100);
  } finally {
    await lock.query('ROLLBACK');
    await lock.query('UPDATE t_orders SET total_fee=12000, accrued_remuneration=7000 WHERE id=$1', [prefix + '0001']);
    await lock.end(); await observer.end(); if (pending) await pending.catch(() => undefined);
  }
});
Then('運営帳票の再取得は原期の訂正と無効化を反映する', async ({ request, page }) => {
  await correctOrderExtension(request, manager, orderId, '原期の提供訂正');
  let response = await request.get(base, { headers: headers(writer), params });
  expect(await response.json()).toMatchObject({ total_order_count: 2001, total_fee: 24016000, total_remuneration: 14009000 });
  await invalidateOrder(request, manager, orderId, '全く未提供の誤完了');
  response = await request.get(base, { headers: headers(writer), params });
  expect(await response.json()).toMatchObject({ total_order_count: 2000, invalidated_order_count: 1, total_fee: 24000000, total_remuneration: 14000000 });
  await page.getByRole('button', { name: '照会', exact: true }).click();
  await expect(page.getByLabel('有効完了件数', { exact: true })).toHaveText('2,000');
  await expect(page.getByLabel('無効化件数', { exact: true })).toHaveText('1');
});
