import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { APP_URL } from '../base-url';
import {
  createCast,
  createCourse,
  createCustomer,
  getOrder,
  getOrderReceptionists,
  linkMemberToCustomer,
  loginAsStoreAdmin,
  loginViaUiAndEnterStore,
  registerMember,
  submitConfirmedStoreRequest,
  STORE1_ID,
  STORE_HEADERS,
  todayInTokyo,
} from './store-api';

const { Given, When, Then } = createBdd();
let token = '';
let customerId = '';
let customerName = '';
let orderId = '';
let attributionId = 0;

// 冪等キーの発行など、ダイアログを開いた瞬間に走る処理の例外は画面に文言として出ないことがある。
// ページ例外を拾って、成功の表示と合わせて確かめる。
let pageErrors: string[] = [];

async function prepareLinkedCustomer(request: Parameters<typeof loginAsStoreAdmin>[0], label: string) {
  const suffix = Date.now().toString();
  token = await loginAsStoreAdmin(request);
  customerName = `${label}-${suffix}`;
  customerId = await createCustomer(request, token, customerName);
  const memberCode = await registerMember(
    request,
    `point-dialog-${suffix}@example.test`,
    `E2e-${suffix}-member`,
    `${label}会員`,
  );
  await linkMemberToCustomer(request, token, customerId, memberCode);
}

async function expectBalance(page: import('@playwright/test').Page, points: number) {
  await page.goto(`${APP_URL}/store/customers/${customerId}/edit`);
  await expect(page.getByText(`${points} ポイント`, { exact: true })).toBeVisible({ timeout: 15000 });
}

Given('会員に紐づいたポイント調整用の顧客を用意する', async ({ page, request }) => {
  await prepareLinkedCustomer(request, 'ポイント調整画面');
  pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  await loginViaUiAndEnterStore(page);
});

When(
  '顧客詳細のポイント調整ダイアログで {int} ポイントを加算する',
  async ({ page }, delta: number) => {
    await expectBalance(page, 0);
    await page.getByRole('button', { name: 'ポイント調整', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'ポイント調整' });
    await dialog.getByLabel('増減ポイント', { exact: true }).fill(String(delta));
    await dialog.getByRole('combobox', { name: '事由の分類' }).click();
    await page.getByRole('option', { name: '補填', exact: true }).click();
    await dialog.getByLabel('事由', { exact: true }).fill('画面操作の確認');
    await dialog.getByRole('button', { name: '調整する', exact: true }).click();
    await expect(page.getByText('ポイントを調整しました', { exact: true })).toBeVisible();
    await expect(dialog).toBeHidden();
  },
);

Then('顧客詳細の残高が {int} ポイントになる', async ({ page }, points: number) => {
  // 応答の残高で差し替えた表示と、読み直した台帳の両方が同じ額を指すこと
  await expect(page.getByText(`${points} ポイント`, { exact: true })).toBeVisible();
  await expectBalance(page, points);
  expect(pageErrors).toEqual([]);
});

Given('会員に帰属した完了受注を用意する', async ({ page, request, $testInfo }) => {
  $testInfo.setTimeout(120000);
  await prepareLinkedCustomer(request, '帰属訂正画面');
  const suffix = Date.now().toString();
  const castId = await createCast(request, token, `帰属訂正画面キャスト-${suffix}`);
  const courseId = await createCourse(request, token, `帰属訂正画面コース-${suffix}`);
  const receptionistId = (await getOrderReceptionists(request, token))[0].id;
  const headers = { ...STORE_HEADERS, Authorization: `Bearer ${token}` };
  const created = await (
    await submitConfirmedStoreRequest(request, token, '/api/orders/preview', '/api/orders', {
      cast_id: castId,
      course_id: courseId,
      receptionist_id: receptionistId,
      customer_selection: { mode: 'EXISTING', customer_id: customerId },
      business_date: todayInTokyo(),
      pax: 1,
    })
  ).json();
  orderId = created.id;
  await submitConfirmedStoreRequest(
    request,
    token,
    `/api/orders/${orderId}/completion-preview`,
    `/api/orders/${orderId}/completion`,
    { expected_version: (await getOrder(request, token, STORE1_ID, orderId)).version, fee_lines: [] },
  );
  const attribution = await request.get(`/api/orders/${orderId}/attribution`, { headers });
  expect(attribution.status()).toBe(200);
  attributionId = (await attribution.json()).id;
  pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  await loginViaUiAndEnterStore(page);
});

When('完了アーカイブから会員帰属を開いて無効化し付与分を差し引く', async ({ page }) => {
  await page.goto(`${APP_URL}/store/orders`);
  await page.getByLabel('お客様名', { exact: true }).fill(customerName);
  await page.getByRole('button', { name: '検索', exact: true }).click();
  await page.getByRole('button', { name: /^完了 \d+ 件$/ }).click();
  await page.getByRole('link', { name: '会員帰属', exact: true }).click();

  await expect(page).toHaveURL(new RegExp(`/store/orders/${orderId}/attribution$`));
  await expect(page.getByRole('heading', { level: 1, name: '会員帰属の訂正' })).toBeVisible();
  await page.getByLabel('無効化の理由', { exact: true }).fill('別の会員の来店として記録された');
  await page.getByRole('button', { name: '無効化する', exact: true }).click();
  await expect(page.getByText('帰属を無効化しました', { exact: true })).toBeVisible();

  // 無効化の直後に差し引きの節へ送られ、差し引く額は付与の残り全額で埋まっている
  await expect(page.getByRole('heading', { level: 2, name: '誤付与の差し引き' })).toBeVisible();
  await expect(page.getByLabel('差し引くポイント', { exact: true })).not.toHaveValue('');
  await page.getByLabel('訂正の理由', { exact: true }).fill('誤帰属で付与された分の取り消し');
  await page.getByRole('button', { name: '差し引く', exact: true }).click();
  await expect(page.getByText('誤って付与されたポイントを差し引きました', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { level: 2, name: '現在の帰属' })).toBeVisible();
  await expect(page.getByText('この来店は会員へ帰属していません。', { exact: true })).toBeVisible();
});

Then('付与分がすべて差し引かれ顧客詳細の残高が 0 ポイントになる', async ({ page, request }) => {
  const progress = await request.get(`/api/orders/${orderId}/attribution/correction`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    params: { attribution_id: attributionId },
  });
  expect(progress.status(), await progress.text()).toBe(200);
  const { granted_points, corrected_points } = await progress.json();
  expect(granted_points).toBeGreaterThan(0);
  expect(corrected_points).toBe(granted_points);
  await expectBalance(page, 0);
  expect(pageErrors).toEqual([]);
});
