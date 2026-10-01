import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { Client } from 'pg';
import { acceptCastInvitation, issueCastInvitation, createCast, createCourse,
  createAgreedOrder, completeAgreedOrder, getOrder, invalidateOrder,
  loginAsStoreAdmin, loginPlatformUser, STORE_HEADERS, STORE1_ID } from './store-api';

const { Given, When, Then } = createBdd();
let manager = '';
let token = '';
let orderId = '';
let prefix = '';
let month = '';
let beforePdf: Buffer;
let correction: Record<string, unknown>;
const base = '/api/platform/me/monthly-remunerations/pdf';

async function database() {
  if (process.env.PGHOST !== 'database') throw new Error('専用 E2E DB でのみ実行できます');
  const client = new Client({ application_name: 'issue982-pdf-bdd' });
  await client.connect();
  return client;
}

async function pdfText(bytes: Buffer) {
  const { getDocument } = await import('pdfjs-dist/legacy/build/pdf.mjs');
  const loading = getDocument({ data: new Uint8Array(bytes), useSystemFonts: false });
  try {
    const pdf = await loading.promise;
    let result = '';
    let identifiers = '';
    for (let number = 1; number <= pdf.numPages; number++) {
      const content = await (await pdf.getPage(number)).getTextContent();
      result += content.items.map(item => 'str' in item ? item.str : '').join('');
      // 折返した受注番号は他列の読み順が挟まるため、番号列をページ間でも繋いで照合する。
      identifiers += content.items.filter(item => 'str' in item && item.str !== '受注番号'
        && item.transform[4] >= 115 && item.transform[4] < 230)
        .map(item => 'str' in item ? item.str : '').join('');
    }
    return (result + identifiers).replace(/\s/g, '');
  } finally { await loading.destroy(); }
}

Given('月次PDFの本人に二千件を超える完了受注がある', async ({ request, $testInfo }) => {
  $testInfo.setTimeout(120000);
  manager = await loginAsStoreAdmin(request);
  const suffix = String(Date.now());
  prefix = `PDF982-${suffix}-`;
  const cast = await createCast(request, manager, '全件確認さくら');
  const email = `pdf982-${suffix}@kizuna.test`;
  const password = `Cast-${suffix}-pass`;
  await acceptCastInvitation(request, await issueCastInvitation(request, manager, cast), email, password, '印刷禁止の本人アカウント名');
  token = await loginPlatformUser(request, email, password);
  const course = await createCourse(request, manager, '日本語全件サービス');
  orderId = await createAgreedOrder(request, manager, cast, course, '印刷禁止の顧客名');
  await completeAgreedOrder(request, manager, orderId);
  const order = await getOrder(request, manager, STORE1_ID, orderId);
  month = String(order.business_date).slice(0, 7);
  correction = { expected_version: order.version, reason: '印刷禁止の内部訂正理由',
    fee_lines: [{ kind: 'EXTENSION', name: '追加延長', duration_minutes: 30, amount: 4000, remuneration: 2000 }] };
  const preview = await request.post(`/api/store/orders/${orderId}/correction-preview`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${manager}` }, data: correction });
  expect(preview.status()).toBe(200);
  correction.confirmation_token = (await preview.json()).confirmation_token;
  const db = await database();
  try {
    // 生成列は DB に再計算させ、実際に完了した集約から同じ対象の fixture を複製する。
    for (const table of ['t_orders', 't_order_fee_lines'] as const) {
      const metadata = await db.query(`SELECT attname FROM pg_attribute
        WHERE attrelid = $1::regclass AND attnum > 0 AND NOT attisdropped
        AND attgenerated = '' ORDER BY attnum`, [table]);
      const names = metadata.rows.map(row => '"' + String(row.attname).replaceAll('"', '""') + '"');
      const id = "$2::text || lpad(i::text, 4, '0')";
      const patch = table === 't_orders' ? `jsonb_build_object('id', ${id})`
        : `jsonb_build_object('id', $2::text || o.id || '-' || i::text, 'order_id', ${id})`;
      const sourceKey = table === 't_orders' ? 'id' : 'order_id';
      await db.query(`INSERT INTO ${table} (${names.join(', ')})
        SELECT ${names.map(name => `cloned.${name}`).join(', ')}
        FROM ${table} o CROSS JOIN generate_series(1, 2000) i
        CROSS JOIN LATERAL jsonb_populate_record(NULL::${table}, to_jsonb(o) || ${patch}) cloned
        WHERE o.${sourceKey} = $1`, [orderId, prefix]);
    }
  } finally { await db.end(); }
});

When('本人の月次PDF取得中に店舗が受注を訂正する', async ({ request }) => {
  const lock = await database();
  const observer = await database();
  let pending: ReturnType<typeof request.get> | undefined;
  try {
    await lock.query('BEGIN');
    await lock.query('LOCK TABLE t_cast_profiles IN ACCESS EXCLUSIVE MODE');
    // 本人の認証・歴史在籍・最初の集計を読んだ後の源氏名照会を待たせる。
    pending = request.get(base, { headers: { Authorization: `Bearer ${token}` },
      params: { store_id: STORE1_ID, month, page: 99, size: 1 } });
    await expect.poll(async () => {
      const result = await observer.query(`SELECT count(*)::int AS n FROM pg_stat_activity
        WHERE datname = current_database() AND application_name <> 'issue982-pdf-bdd'
        AND wait_event_type = 'Lock' AND query LIKE '%t_cast_profiles%'`);
      return result.rows[0].n;
    }, { timeout: 5000 }).toBeGreaterThan(0);
    const changed = await request.post(`/api/store/orders/${orderId}/corrections`, {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${manager}` }, data: correction });
    expect(changed.status(), await changed.text()).toBe(201);
    await lock.query('COMMIT');
    const response = await pending;
    expect(response.status(), await response.text()).toBe(200);
    beforePdf = await response.body();
  } finally {
    await lock.query('ROLLBACK');
    await lock.end();
    await observer.end();
    if (pending) await pending.catch(() => undefined);
  }
});

Then('PDFの全明細と合計は訂正前の同一時点であり再生成に訂正と無効化が反映される', async ({ request, $testInfo }) => {
  const text = await pdfText(beforePdf);
  expect(text).toContain('全2001件');
  expect(text).toContain('月次報酬合計:14,007,000円');
  expect(text).toContain(orderId);
  for (let index = 1; index <= 2000; index++) expect(text).toContain(prefix + String(index).padStart(4, '0'));
  expect(text).not.toContain('追加延長');
  expect(text).not.toContain('印刷禁止');
  await $testInfo.attach('2001件の同時訂正前PDF', { body: beforePdf, contentType: 'application/pdf' });
  const after = await request.get(base, { headers: { Authorization: `Bearer ${token}` }, params: { store_id: STORE1_ID, month } });
  expect(after.status()).toBe(200);
  const afterText = await pdfText(await after.body());
  expect(afterText).toContain('月次報酬合計:14,009,000円');
  expect(afterText).toContain('追加延長');
  expect(afterText).not.toContain('印刷禁止');
  await invalidateOrder(request, manager, orderId, '印刷禁止の無効化理由');
  const invalidated = await request.get(base, { headers: { Authorization: `Bearer ${token}` }, params: { store_id: STORE1_ID, month } });
  expect(invalidated.status()).toBe(200);
  const invalidatedText = await pdfText(await invalidated.body());
  expect(invalidatedText).toContain('月次報酬合計:14,000,000円');
  expect(invalidatedText).toContain('無効化済み（有効報酬0円）');
});

Then('DB の待機で PDF は503となりロック解除後に再生成できる', async ({ request }) => {
  const lock = await database();
  try {
    await lock.query('BEGIN');
    await lock.query('LOCK TABLE t_cast_profiles IN ACCESS EXCLUSIVE MODE');
    const timedOut = await request.get(base, { headers: { Authorization: `Bearer ${token}` }, params: { store_id: STORE1_ID, month } });
    expect(timedOut.status(), await timedOut.text()).toBe(503);
    expect(timedOut.headers()['content-type']).toContain('application/json');
    expect((await timedOut.json()).error).toContain('再試行');
  } finally {
    await lock.query('ROLLBACK');
    await lock.end();
  }
  const retry = await request.get(base, { headers: { Authorization: `Bearer ${token}` }, params: { store_id: STORE1_ID, month } });
  expect(retry.status(), await retry.text()).toBe(200);
  expect(retry.headers()['content-type']).toContain('application/pdf');
});
