import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { BASE_URL, PLATFORM_URL } from '../base-url';
import {
  createCast,
  createCustomer,
  createShift,
  deleteCast,
  deleteCustomer,
  deleteOrder,
  deleteShift,
  linkMemberToCustomer,
  loginAsStoreAdmin,
  loginViaUiAndEnterStore,
  registerMember,
} from './store-api';

const { Given, When, Then, After } = createBdd();

// 後端の「本日」判定（app.timezone 既定 Asia/Tokyo）に合わせて日付を計算する（cast-portal.steps.ts と同型）。
const todayInTokyo = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date());

const MEMBER_PASSWORD = 'pass12345';

// 播種した実体の id / 認証情報。MEMBER PlatformUser には削除 API が無いためタイムスタンプ付き
// メールアドレスで隔離し残留を許容する（顧客・キャスト・シフト・受注は明示削除する）。
let memberEmail = '';
let createdCustomerId = '';
let createdCastId = '';
let createdShiftId = '';
let createdOrderId = '';

Given('会員を登録し店舗側で顧客台帳に紐づける', async ({ request }) => {
  const suffix = Date.now();
  memberEmail = `member-reservation-e2e-${suffix}@kizuna.test`;
  const memberCode = await registerMember(
    request,
    memberEmail,
    MEMBER_PASSWORD,
    `E2E会員-${suffix}`
  );

  const adminToken = await loginAsStoreAdmin(request);
  createdCustomerId = await createCustomer(
    request,
    adminToken,
    `E2E会員顧客-${suffix}`,
    `090${String(suffix).slice(-8)}`
  );
  await linkMemberToCustomer(request, adminToken, createdCustomerId, memberCode);
});

Given('予約用に本日の確定シフトを API で作成する', async ({ request }) => {
  const adminToken = await loginAsStoreAdmin(request);
  createdCastId = await createCast(request, adminToken, `E2E予約キャスト-${Date.now()}`);
  createdShiftId = await createShift(request, adminToken, {
    castId: createdCastId,
    workDate: todayInTokyo(),
    startTime: '18:00:00',
    endTime: '23:00:00',
    status: 'CONFIRMED',
  });
});

When('登録した会員のメールとパスワードでログインする', async ({ page }) => {
  await page.getByLabel('メールアドレス', { exact: true }).fill(memberEmail);
  await page.getByLabel('パスワード', { exact: true }).fill(MEMBER_PASSWORD);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
});

Then('会員ポータルのホームへ遷移する', async ({ page }) => {
  await expect(page).toHaveURL(/\/member\/?$/, { timeout: 15000 });
});

When('店舗公式サイトのトップを開く', async ({ page }) => {
  await page.goto(BASE_URL);
  // 公開サイトは年齢確認オーバーレイが全面を覆うため、先に通過させる（public-site.steps.ts と同型）。
  await page.getByRole('button', { name: 'はい' }).click();
  await expect(page.getByRole('button', { name: 'はい' })).toBeHidden();
});

When('WEB予約ボタンを押す', async ({ page }) => {
  // 公式サイトは店舗ドメイン、ポータルは平台ドメインで別オリジンのため、ページ遷移そのものを待つ。
  await page.getByRole('link', { name: 'WEB予約' }).first().click();
  await page.waitForURL(/\/member\/reservations\/new/, { timeout: 15000 });
});

Then('予約申請画面に該当店舗がプリセレクトされる', async ({ page }) => {
  const storeDomain = new URL(BASE_URL).hostname;
  expect(new URL(page.url()).searchParams.get('store')).toBe(storeDomain);
  // 店舗名はブラウザ渡しのドメインを公開照会に突き合わせた結果として描かれる。
  await expect(page.getByRole('heading', { name: 'Sample Tenant', level: 2 })).toBeVisible({
    timeout: 15000,
  });
});

Then('指名候補に本日の出勤キャストが出る', async ({ page }) => {
  await page.getByLabel('利用日').fill(todayInTokyo());
  // 指名候補はその日の確定シフトから引くため、日付を入れて初めて選択肢が現れる。
  await expect(page.getByLabel('指名（任意）').getByRole('option')).toHaveCount(2, {
    timeout: 15000,
  });
});

When('人数 {string} で予約を申請する', async ({ page }, pax: string) => {
  await page.getByLabel('利用日').fill(todayInTokyo());
  await page.getByLabel('人数').fill(pax);
  const [response] = await Promise.all([
    page.waitForResponse(
      resp => resp.url().includes('/platform/me/orders') && resp.request().method() === 'POST',
      { timeout: 15000 }
    ),
    page.getByRole('button', { name: 'この内容で申請する' }).click(),
  ]);
  const body = await response.json();
  createdOrderId = body.id as string;
});

Then('予約一覧に {string} の予約が表示される', async ({ page }, statusLabel: string) => {
  await expect(page).toHaveURL(/\/member\/reservations\/?$/, { timeout: 15000 });
  const item = page.getByRole('listitem').filter({ hasText: 'Sample Tenant' });
  await expect(item.getByText(statusLabel, { exact: true })).toBeVisible({ timeout: 15000 });
});

When('店舗管理者が予約受付 inbox で予約を確定する', async ({ page }) => {
  const storeId = await loginViaUiAndEnterStore(page);
  await page.goto(`${PLATFORM_URL}/store/${storeId}/orders`);
  const request = page.getByRole('listitem').filter({ hasText: 'WEB申請' }).first();
  await expect(request).toBeVisible({ timeout: 15000 });
  await Promise.all([
    page.waitForResponse(
      resp => resp.url().includes('/confirmation') && resp.request().method() === 'POST',
      { timeout: 15000 }
    ),
    request.getByRole('button', { name: '確定' }).click(),
  ]);
});

When('予約一覧を開き直す', async ({ page }) => {
  // 店舗コンソールへ入った後は会員セッションが失われるため、会員として入り直す。
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel('メールアドレス', { exact: true }).fill(memberEmail);
  await page.getByLabel('パスワード', { exact: true }).fill(MEMBER_PASSWORD);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).toHaveURL(/\/member\/?$/, { timeout: 15000 });
  await page.goto(`${PLATFORM_URL}/member/reservations/`);
});

When('予約を取り下げる', async ({ page }) => {
  await Promise.all([
    page.waitForResponse(
      resp => resp.url().includes('/cancellation') && resp.request().method() === 'POST',
      { timeout: 15000 }
    ),
    page.getByRole('button', { name: '取り下げる' }).first().click(),
  ]);
});

// 播種した実体を無条件で片付ける（テスト失敗・途中クラッシュでも実行）。MEMBER PlatformUser には
// 削除 API が無いため残留を許容する（タイムスタンプ付きメールアドレスで以後の run と隔離済み）。
// 受注は顧客・キャストを参照する（FK RESTRICT）ため、必ず受注 → シフト → キャスト → 顧客の順で消す。
After(async ({ request }) => {
  const adminToken = await loginAsStoreAdmin(request);
  if (createdOrderId) {
    await deleteOrder(request, adminToken, createdOrderId).catch(() => {});
  }
  if (createdShiftId) {
    await deleteShift(request, adminToken, createdShiftId).catch(() => {});
  }
  if (createdCastId) {
    await deleteCast(request, adminToken, createdCastId).catch(() => {});
  }
  if (createdCustomerId) {
    await deleteCustomer(request, adminToken, createdCustomerId).catch(() => {});
  }
  memberEmail = '';
  createdCustomerId = '';
  createdCastId = '';
  createdShiftId = '';
  createdOrderId = '';
});
