import { expect, type APIRequestContext } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { ADMIN_PASSWORD, STORE_HEADERS, createPermissionRole, createStaffFixture, loginUser } from './store-api';

const { Given, When, Then, After } = createBdd();
let token = '';
let reviewId = '';
let version = 0;
let marker = '';
let suffix = '';
const headers = () => ({ ...STORE_HEADERS, Authorization: `Bearer ${token}` });

async function publicBodies(request: APIRequestContext): Promise<string[]> {
  const response = await request.get('/api/reviews/public', { headers: STORE_HEADERS, params: { size: 100 } });
  expect(response.status()).toBe(200);
  return (await response.json()).content.map((r: { body: string }) => r.body);
}

async function change(request: APIRequestContext, path: string, data: Record<string, unknown>, expected = 200) {
  const response = await request.post(`/api/reviews/${reviewId}/${path}`, { headers: headers(), data: { version, ...data } });
  expect(response.status(), await response.text()).toBe(expected);
  version = (await response.json()).review.version;
}

Given('口コミを受付し内部承認と公開許可を記録している', async ({ request }) => {
  const admin = await loginUser(request, 'admin@kizuna.test', ADMIN_PASSWORD);
  suffix = Date.now().toString();
  const email = `review-public-${suffix}@kizuna.test`;
  const password = `Review-${suffix}-fixture`;
  const role = await createPermissionRole(request, admin, `口コミ掲出-${suffix}`, ['REVIEW_VIEW', 'REVIEW_MANAGE', 'REVIEW_MODERATE']);
  await createStaffFixture(request, admin, email, password, [role]);
  token = await loginUser(request, email, password);
  marker = `掲出検証-${suffix}`;
  const created = await request.post('/api/reviews', {
    headers: headers(),
    data: { body: `${marker}\n二行目の感想です。`, display_name: '掲出テスト', received_via: 'PAPER', received_at: '2026-01-01T20:00:00Z', dedupe_key: `public-${suffix}` },
  });
  expect(created.status(), await created.text()).toBe(201);
  const review = (await created.json()).review;
  reviewId = review.id;
  version = review.version;
  await change(request, 'decisions', { decision: 'APPROVE', reason: '内容を確認', dedupe_key: `public-approve-${suffix}` });
  await change(request, 'permissions', { basis_type: 'WRITTEN', granted_at: '2026-01-02T09:00:00+09:00', evidence_note: '書面で掲載を確認', dedupe_key: `public-grant-${suffix}` }, 201);
  // 承認と公開許可だけでは公式サイトに載らない
  expect((await publicBodies(request)).some(body => body.startsWith(marker))).toBe(false);
});

When('口コミを公開に設定する', async ({ request }) => {
  const response = await request.put(`/api/reviews/${reviewId}/publication`, {
    headers: headers(),
    data: { version, published: true, publish_from: new Date(Date.now() - 60_000).toISOString(), dedupe_key: `public-publish-${suffix}` },
  });
  expect(response.status(), await response.text()).toBe(200);
  const result = await response.json();
  expect(result.review.displayed).toBe(true);
  version = result.review.version;
});

Then('口コミページに本文が改行を保って表示される', async ({ page }) => {
  await page.goto('/reviews');
  await expect(page.getByRole('heading', { name: '口コミ', exact: true })).toBeVisible();
  const item = page.getByRole('article').filter({ hasText: marker });
  await expect(item).toBeVisible();
  expect(await item.getByText(marker).innerText()).toBe(`${marker}\n二行目の感想です。`);
  await expect(item.getByText('掲出テスト')).toBeVisible();
  // 受領日は店舗の現地日付（2026-01-01T20:00Z は東京で 1 月 2 日）
  await expect(item.locator('time')).toHaveText('2026年1月2日');
});

Then('壊れた Bearer 付きでも公開口コミは表示名・本文・受領日だけで返る', async ({ request }) => {
  const response = await request.get('/api/reviews/public', {
    headers: { ...STORE_HEADERS, Authorization: 'Bearer broken-token-value' },
    params: { size: 100 },
  });
  expect(response.status()).toBe(200);
  const shown = (await response.json()).content.find((r: { body: string }) => r.body.startsWith(marker));
  expect(Object.keys(shown).sort()).toEqual(['body', 'display_name', 'received_on']);
  expect(shown.received_on).toBe('2026-01-02');
});

When('口コミの公開許可を撤回する', async ({ request }) => {
  await change(request, 'permission-revocations', { withdrawal_received_at: new Date(Date.now() - 60_000).toISOString(), reason: '本人から撤回', dedupe_key: `public-revoke-${suffix}` });
});

Then('口コミページから口コミが消える', async ({ page }) => {
  await page.goto('/reviews');
  await expect(page.getByRole('heading', { name: '口コミ', exact: true })).toBeVisible();
  await expect(page.getByRole('article').filter({ hasText: marker })).toHaveCount(0);
});

// 途中で失敗しても掲出したまま残さない。取り下げは承認・許可の状態を問わず通る。ベストエフォート。
After(async ({ request }) => {
  if (reviewId && token) {
    await request
      .post(`/api/reviews/${reviewId}/withdrawals`, { headers: headers(), data: { version, reason: 'E2E の後片付け', dedupe_key: `public-cleanup-${suffix}` } })
      .catch(() => {});
  }
  token = '';
  reviewId = '';
  version = 0;
  marker = '';
  suffix = '';
});
