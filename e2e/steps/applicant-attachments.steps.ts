import { expect, type APIRequestContext, type Request } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import { Client } from "pg";
import { PLATFORM_URL } from "../base-url";
import { ADMIN_PASSWORD, STORE1_ID, STORE_HEADERS, createPermissionRole, loginAsStoreAdmin } from "./store-api";
const { Given, When, Then } = createBdd();
let storeId = "";
let token = "";
let managerToken = "";
let email = "";
let applicantId = "";
let attachmentId = "";
let recoveryId = "";
let key = "";
let recoveryKey = "";
let image = Buffer.alloc(0);
const path = () => `/api/store/applicants/${applicantId}`;
const headers = () => ({ ...STORE_HEADERS, Authorization: `Bearer ${token}` });
const uploadHeaders = (operation: string) => ({ ...headers(), "Content-Type": "image/png", "Idempotency-Key": operation });
async function database() { const client = new Client(); await client.connect(); return client; }
async function upload(request: APIRequestContext, operation: string) {
  return request.post(`${path()}/attachments`, { headers: uploadHeaders(operation), data: image });
}
Given("添付検証専用の担当者と応募者が存在する", async ({ page, request, $testInfo }) => {
  $testInfo.setTimeout(120000);
  const suffix = randomUUID();
  managerToken = await loginAsStoreAdmin(request);
  const ownerLogin = await request.post("/api/platform/login", { data: { email: "admin@kizuna.test", password: ADMIN_PASSWORD } });
  expect(ownerLogin.status()).toBe(200);
  const owner = (await ownerLogin.json()).token;
  const role = await createPermissionRole(request, owner, `添付検証-${suffix}`, ["RECRUITMENT_VIEW", "RECRUITMENT_MANAGE", "RECRUITMENT_ATTACHMENT_VIEW", "RECRUITMENT_ATTACHMENT_MANAGE", "STORE_VIEW", "STORE_MENU_VIEW"]);
  email = `attachment-${suffix}@example.invalid`;
  const password = randomUUID();
  const staff = await request.post("/api/store/staff-members", { headers: { ...STORE_HEADERS, Authorization: `Bearer ${managerToken}` }, data: { email, password, display_name: "添付検証担当", role_ids: [role], store_scope_type: "SPECIFIC_STORES", store_ids: [Number(STORE1_ID)] } });
  expect(staff.status(), await staff.text()).toBe(201);
  const login = await request.post("/api/platform/login", { data: { email, password } });
  expect(login.status()).toBe(200);
  token = (await login.json()).token;
  const applicant = await request.post("/api/store/applicants", { headers: headers(), data: { name: `画像検証-${suffix}`, channel: "WEB", source_type: "DIRECT" } });
  expect(applicant.status(), await applicant.text()).toBe(201);
  applicantId = (await applicant.json()).id;
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel("メールアドレス", { exact: true }).fill(email);
  await page.getByLabel("パスワード", { exact: true }).fill(password);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page).toHaveURL(/\/store\/\d+\//, { timeout: 20000 });
  storeId = new URL(page.url()).pathname.match(/\/store\/(\d+)/)![1];
  image = Buffer.from(await page.evaluate(() => { const canvas = document.createElement('canvas'); canvas.width = 8; canvas.height = 8; return canvas.toDataURL('image/png').split(',')[1]; }), 'base64');
  const policy = await request.get('/api/store/applicants/attachment-policy', { headers: headers() });
  expect(policy.status()).toBe(200);
  expect((await policy.json()).configured).toBe(true);
});
When("非公開添付の初回登録と同一操作の再送を行う", async ({ request, page }) => {
  key = randomUUID();
  const first = await upload(request, key);
  expect(first.status(), await first.text()).toBe(201);
  const summary = await first.json(); attachmentId = summary.id;
  expect(Object.keys(summary).sort()).toEqual(['created_at', 'id', 'media_type', 'size_bytes']);
  const again = await upload(request, key);
  expect(again.status(), await again.text()).toBe(200);
  expect((await again.json()).id).toBe(attachmentId);
  const different = await request.post(`${path()}/attachments`, { headers: uploadHeaders(key), data: Buffer.concat([image, Buffer.from('different')]) });
  expect(different.status()).toBe(409);
  const client = await database();
  try { expect(Number((await client.query('select count(*) from t_audit_events where target_id=$1', [attachmentId])).rows[0].count)).toBe(1); }
  finally { await client.end(); }
  await page.goto(`${PLATFORM_URL}/store/${storeId}/applicants/${applicantId}`);
  await page.getByLabel('追加する画像').setInputFiles({ name: 'synthetic-ui.png', mimeType: 'image/png', buffer: image });
  let submissions = 0;
  let storedStatus = 0;
  let firstUiKey = '';
  const lostPost = `**/applicants/${applicantId}/attachments`;
  const countSubmission = (request: Request) => {
    if (request.method() === 'POST' && request.url().endsWith(`${path()}/attachments`)) submissions++;
  };
  page.on('request', countSubmission);
  await page.route(lostPost, async route => {
    if (route.request().method() !== 'POST') return route.continue();
    firstUiKey = route.request().headers()['idempotency-key'];
    storedStatus = (await route.fetch()).status();
    await route.abort('failed');
  });
  try {
    await page.getByLabel('追加する画像').evaluate(input => {
      const form = input.closest('form')!;
      form.requestSubmit(); form.requestSubmit();
    });
    await expect.poll(() => storedStatus).toBe(201);
    await expect(page.getByRole('button', { name: 'ダウンロード', exact: true })).toHaveCount(2);
    await expect(page.getByLabel('追加する画像')).toBeEnabled();
    expect(submissions).toBe(1);
    const result = await request.get(`${path()}/attachment-operations/${firstUiKey}`, { headers: headers() });
    expect(result.status()).toBe(200);
    expect((await result.json()).status).toBe('READY');
  } finally { page.off('request', countSubmission); await page.unroute(lostPost); }
  const nextImage = Buffer.from(await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 9; canvas.height = 9;
    return canvas.toDataURL('image/png').split(',')[1];
  }), 'base64');
  await page.getByLabel('追加する画像').setInputFiles({ name: 'next.png', mimeType: 'image/png', buffer: nextImage });
  const nextPost = page.waitForResponse(value => value.url().endsWith(`${path()}/attachments`) && value.request().method() === 'POST');
  await page.getByRole('button', { name: '画像を追加', exact: true }).click();
  const next = await nextPost;
  expect(next.status()).toBe(201);
  expect(next.request().headers()['idempotency-key']).not.toBe(firstUiKey);
  await expect(page.getByRole('button', { name: 'ダウンロード', exact: true })).toHaveCount(3);
  let missingKey = '';
  await page.route(lostPost, async route => {
    if (route.request().method() !== 'POST') return route.continue();
    missingKey = route.request().headers()['idempotency-key'];
    await route.abort('failed');
  });
  try {
    await page.getByLabel('追加する画像').setInputFiles({ name: 'not-delivered.png', mimeType: 'image/png', buffer: image });
    await page.getByRole('button', { name: '画像を追加', exact: true }).click();
    await expect(page.getByRole('button', { name: '同じ画像を再送', exact: true })).toBeEnabled();
    await expect(page.getByLabel('追加する画像')).toBeDisabled();
  } finally { await page.unroute(lostPost); }
  const retry = page.waitForResponse(value => value.url().endsWith(`${path()}/attachments`) && value.request().method() === 'POST');
  await page.getByRole('button', { name: '同じ画像を再送', exact: true }).click();
  const retried = await retry;
  expect(retried.status()).toBe(201);
  expect(retried.request().headers()['idempotency-key']).toBe(missingKey);
  await expect(page.getByRole('button', { name: 'ダウンロード', exact: true })).toHaveCount(4);
  await page.getByLabel('追加する画像').setInputFiles({ name: 'invalid.png', mimeType: 'image/png', buffer: Buffer.from('not an image') });
  const rejected = page.waitForResponse(value => value.url().endsWith(`${path()}/attachments`) && value.request().method() === 'POST');
  await page.getByRole('button', { name: '画像を追加', exact: true }).click();
  const invalid = await rejected;
  expect(invalid.status()).toBe(400);
  await expect(page.getByText(/画像が拒否され、予約も確認されませんでした/)).toBeVisible();
  await page.getByLabel('追加する画像').setInputFiles({ name: 'corrected.png', mimeType: 'image/png', buffer: image });
  const corrected = page.waitForResponse(value => value.url().endsWith(`${path()}/attachments`) && value.request().method() === 'POST');
  await page.getByRole('button', { name: '同じ画像を再送', exact: true }).click();
  const correction = await corrected;
  expect(correction.status()).toBe(201);
  expect(correction.request().headers()['idempotency-key']).toBe(invalid.request().headers()['idempotency-key']);
  await expect(page.getByRole('button', { name: 'ダウンロード', exact: true })).toHaveCount(5);
  const verify = await database();
  try { expect(Number((await verify.query('select count(*) from t_applicant_attachment_uploads where applicant_id=$1', [applicantId])).rows[0].count)).toBe(5); }
  finally { await verify.end(); }
  const downloaded = page.waitForEvent('download');
  await page.getByRole('button', { name: 'ダウンロード', exact: true }).first().click();
  expect((await downloaded).suggestedFilename()).toMatch(/^attachment-\d+\.png$/);
});
Then("添付は認証取得だけに公開され店外と権限不足を拒否する", async ({ request }) => {
  const contentPath = `${path()}/attachments/${attachmentId}/content`;
  const file = await request.get(contentPath, { headers: { ...headers(), Range: 'bytes=0-1' } });
  expect(file.status(), await file.text()).toBe(200);
  expect(file.headers()['cache-control']).toContain('no-store');
  expect(file.headers()['x-content-type-options']).toBe('nosniff');
  expect(file.headers()['accept-ranges']).toBe('none');
  expect(file.headers()['content-disposition']).toContain(`attachment-${attachmentId}.png`);
  expect((await file.body()).length).toBeGreaterThan(2);
  const head = await request.head(contentPath, { headers: headers() });
  expect(head.status()).toBe(200);
  expect(head.headers()['content-length']).toBe(file.headers()['content-length']);
  expect((await request.get(contentPath, { headers: STORE_HEADERS })).status()).toBe(401);
  expect((await request.get(contentPath, { headers: { ...STORE_HEADERS, Authorization: `Bearer ${managerToken}` } })).status()).toBe(403);
  const operationPath = `${path()}/attachment-operations/${key}`;
  for (const [url, auth, code] of [
    [operationPath, headers(), 200],
    [`${path()}/attachment-operations/1-1-1-1-1`, headers(), 400],
    [operationPath, STORE_HEADERS, 401],
    [operationPath, { ...STORE_HEADERS, Authorization: `Bearer ${managerToken}` }, 403],
    [`${path()}/attachment-operations/${randomUUID()}`, headers(), 404],
  ] as const) {
    const response = await request.get(url, { headers: auth });
    expect(response.status(), await response.text()).toBe(code);
    expect(response.headers()['cache-control']).toBe('private, no-store');
    expect(response.headers()['x-content-type-options']).toBe('nosniff');
    if (code === 200) expect(Object.keys(await response.json()).sort()).toEqual(['created_at','id','idempotency_key','media_type','size_bytes','status']);
    if (code === 400) expect((await response.json()).error).toBe('アップロードの操作キーが不正です');
  }
  const client = await database();
  try {
    const row = (await client.query('select object_id from t_applicant_attachment_uploads where id=$1', [attachmentId])).rows[0];
    for (const method of ['GET', 'HEAD', 'PUT']) expect((await request.fetch(`http://private-storage:8333/applicant-images/applicant-images/${row.object_id}`, { method })).status()).toBe(403);
    expect((await request.get('http://private-storage:8333/applicant-images')).status()).toBe(403);
    const other = (await client.query('select id from t_stores where id <> $1 order by id limit 1', [STORE1_ID])).rows[0].id;
    expect((await request.get(contentPath, { headers: { ...headers(), 'X-Store-ID': String(other) } })).status()).toBe(403);
    expect((await request.get(operationPath, { headers: { ...headers(), 'X-Store-ID': String(other) } })).status()).toBe(403);
    const hidden = `outside-${randomUUID()}`;
    await client.query('INSERT INTO t_applicants (id,store_id,name,channel,source_type,source_media,referrer,status,created_at,updated_at,version,modified_by,edit_sequence) SELECT $1,$2,$3,channel,source_type,source_media,referrer,status,created_at,updated_at,version,modified_by,edit_sequence FROM t_applicants WHERE id=$4', [hidden,other,'別店舗応募者',applicantId]);
    try { expect((await request.get(`/api/store/applicants/${hidden}/attachment-operations/${key}`, { headers: headers() })).status()).toBe(404); }
    finally { await client.query('delete from t_applicants where id=$1',[hidden]); }

  } finally { await client.end(); }
});
Then("監査障害で未完了になった添付を画面から同じ画像で回復できる", async ({ request, page, $testInfo }) => {
  const client = await database();
  recoveryKey = randomUUID();
  try {
    await client.query("alter table t_audit_events add constraint ck_attachment_e2e_fail check(action <> 'APPLICANT_ATTACHMENT_STORED') not valid");
    try { const failed = await upload(request, recoveryKey); expect(failed.status(), await failed.text()).toBe(503); }
    finally { await client.query('alter table t_audit_events drop constraint ck_attachment_e2e_fail'); }
    const pending = await request.get(`${path()}/attachment-uploads`, { headers: headers() });
    expect(pending.status()).toBe(200);
    const rows = (await pending.json()).content;
    expect(rows).toHaveLength(1); recoveryId = rows[0].id;
    expect(rows[0].idempotency_key).toBe(recoveryKey);
    expect(rows[0].status).toBe('RECOVERY_REQUIRED');
    for (const field of ['original_sha256', 'canonical_sha256', 'object_id', 'bucket', 'url']) expect(rows[0]).not.toHaveProperty(field);
    expect((await request.get(`${path()}/attachments/${recoveryId}/content`, { headers: headers() })).status()).toBe(404);
    await client.query("update t_applicant_attachment_uploads set failure='CONTENT_MISMATCH' where id=$1", [recoveryId]);
    await page.goto(`${PLATFORM_URL}/store/${storeId}/applicants/${applicantId}`);
    const resend = page.getByRole('button', { name: '修復後の同じ画像を再送' });
    await expect(resend).toBeDisabled();
    await expect(page.getByText(/同じ画像の再送だけでは回復できません/)).toBeVisible();
    const confirmed = page.getByRole('checkbox', { name: '管理者による保存先の確認・修復が完了している' });
    for (const theme of ['light', 'dark']) {
      await page.evaluate(value => { localStorage.setItem('theme', value); document.documentElement.classList.toggle('dark', value === 'dark'); }, theme);
      for (const width of [1280, 390]) {
        await page.setViewportSize({ width, height: 960 });
        await confirmed.scrollIntoViewIfNeeded();
        await confirmed.focus();
        await expect(confirmed).toBeFocused();
        await page.screenshot({ path: $testInfo.outputPath(`attachment-repair-${theme}-${width}.png`), fullPage: true });
      }
    }
    await page.keyboard.press('Space');
    await expect(confirmed).toBeChecked();
    await expect(resend).toBeEnabled();
    await page.getByLabel('最初に送信した画像').setInputFiles({ name: 'synthetic.png', mimeType: 'image/png', buffer: image });
    const lostResponse = `**/attachment-uploads/${recoveryId}/content`;
    let storedStatus = 0;
    await page.route(lostResponse, async route => {
      const response = await route.fetch();
      storedStatus = response.status();
      await route.abort('failed');
    });
    try {
      await resend.click();
      await expect.poll(() => storedStatus).toBe(200);
      await expect(page.getByText('未完了のアップロードはありません。')).toBeVisible();
      await expect(page.getByRole('button', { name: 'ダウンロード', exact: true })).toHaveCount(6);
    } finally { await page.unroute(lostResponse); }
    expect(Number((await client.query('select count(*) from t_audit_events where target_id=$1', [recoveryId])).rows[0].count)).toBe(1);
    const repeated = await request.put(`${path()}/attachment-uploads/${recoveryId}/content`, { headers: uploadHeaders(recoveryKey), data: image });
    expect(repeated.status()).toBe(200);
    expect((await repeated.json()).id).toBe(recoveryId);
    const unavailable = '**/attachments/*/content';
    await page.route(unavailable, route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ error: '画像処理が混み合っています' }) }));
    try {
      await page.getByRole('button', { name: 'ダウンロード', exact: true }).first().click();
      await expect(page.getByText('画像処理が混み合っています', { exact: true })).toBeVisible();
    } finally { await page.unroute(unavailable); }
  } finally { await client.end(); }
});
Then("添付画面は両テーマと狭幅で操作でき認証失効後は再送も拒否する", async ({ page, request, $testInfo }) => {
  for (const theme of ['light','dark']) {
    await page.evaluate(value => { localStorage.setItem('theme', value); document.documentElement.classList.toggle('dark', value === 'dark'); }, theme);
    for (const width of [1280,390]) {
      await page.setViewportSize({ width, height: 960 });
      const panel = page.getByRole('heading', { name: '非公開の添付画像' }).locator('..');
      await panel.scrollIntoViewIfNeeded();
      const input = page.getByLabel('追加する画像'); await input.focus(); await expect(input).toBeFocused();
      await page.keyboard.press('Tab'); await expect(page.getByRole('button', { name: '画像を追加' })).toBeFocused();
      await page.screenshot({ path: $testInfo.outputPath(`attachments-${theme}-${width}.png`), fullPage: true });
      if (width === 390) {
        const action = page.getByRole('button', { name: 'ダウンロード', exact: true }).first();
        await action.scrollIntoViewIfNeeded(); await expect(action).toBeInViewport();
        await page.screenshot({ path: $testInfo.outputPath(`attachments-${theme}-390-actions.png`), fullPage: true });
      }
    }
  }
  const applicant = await request.get(path(), { headers: headers() });
  expect(applicant.status()).toBe(200);
  const withdrawn = await request.post(`${path()}/transitions`, { headers: headers(), data: { version: (await applicant.json()).version, status: 'WITHDRAWN', reason: '合成検証による本人辞退' } });
  expect(withdrawn.status(), await withdrawn.text()).toBe(200);
  const rejectedBeforeAdmission = await request.post(`${path()}/attachments`, { headers: { ...headers(), 'Content-Type': 'image/svg+xml', 'Idempotency-Key': randomUUID() }, data: 'synthetic' });
  expect(rejectedBeforeAdmission.status()).toBe(400);
  expect((await rejectedBeforeAdmission.json()).error).toContain('選考が終了');
  const check = await database();
  const saved = (await check.query('select completed_by,completed_at from t_applicant_attachment_uploads where id=$1',[attachmentId])).rows[0];
  try {
    const before = (await check.query('select count(*) from t_audit_events where target_id=$1', [attachmentId])).rows[0].count;
    for (const state of ['PENDING','RECOVERY_REQUIRED','READY']) {
      await check.query('update t_applicant_attachment_uploads set status=$1,failure=$2,completed_by=$3,completed_at=$4 where id=$5', [state,state === 'RECOVERY_REQUIRED' ? 'CONTENT_MISMATCH' : null,state === 'READY' ? saved.completed_by : null,state === 'READY' ? saved.completed_at : null,attachmentId]);
      const result = await request.get(`${path()}/attachment-operations/${key.toUpperCase()}`, { headers: headers() });
      expect(result.status()).toBe(200);
      expect((await result.json()).status).toBe(state);
      expect((await check.query('select status from t_applicant_attachment_uploads where id=$1',[attachmentId])).rows[0].status).toBe(state);
      expect((await check.query('select count(*) from t_audit_events where target_id=$1', [attachmentId])).rows[0].count).toBe(before);
    }
  } finally {
    await check.query("update t_applicant_attachment_uploads set status='READY',failure=null,completed_by=$1,completed_at=$2 where id=$3",[saved.completed_by,saved.completed_at,attachmentId]);
    await check.end();
  }
  expect((await upload(request, key)).status()).toBe(200);
  expect((await request.put(`${path()}/attachment-uploads/${recoveryId}/content`, { headers: uploadHeaders(recoveryKey), data: image })).status()).toBe(200);
  const client = await database();
  try { await client.query('update t_users set enabled=false, credential_version=credential_version+1 where email=$1', [email]); }
  finally { await client.end(); }
  expect((await upload(request,key)).status()).toBe(401);
  expect((await request.get(`${path()}/attachment-operations/${key}`, { headers: headers() })).status()).toBe(401);
  expect((await request.put(`${path()}/attachment-uploads/${recoveryId}/content`, { headers: uploadHeaders(recoveryKey), data: image })).status()).toBe(401);
  expect((await request.get(`${path()}/attachments/${attachmentId}/content`, { headers: headers() })).status()).toBe(401);
});
