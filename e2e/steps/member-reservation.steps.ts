import { expect, type APIRequestContext } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { BASE_URL, PLATFORM_URL } from '../base-url';
import {
  createCast,
  createCustomer,
  createShift,
  declineOrder,
  deleteCast,
  deleteCustomer,
  cancelOrder,
  deleteShift,
  linkMemberToCustomer,
  loginAsStoreAdmin,
  loginViaUiAndEnterStore,
  registerMember,
  STORE_HEADERS,
} from './store-api';

const { Given, When, Then, After } = createBdd();

// 後端の「本日」判定（app.timezone 既定 Asia/Tokyo）に合わせて日付を計算する（cast-portal.steps.ts と同型）。
const todayInTokyo = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date());

const MEMBER_PASSWORD = 'pass12345';

// 申請の必須項目。店舗が知る名前はこれだけで、会員の登録情報（表示名・メール）は店舗へ渡らない。
const DECLARED_NAME = '名乗り太郎';

// 播種した実体の id / 認証情報。MEMBER PlatformUser には削除 API が無いためタイムスタンプ付き
// メールアドレスで隔離し残留を許容する（顧客・キャスト・シフト・受注は明示削除する）。
let memberEmail = '';
// 会員コードはこの run のシナリオを一意に指す鍵。inbox は店舗共有で他の未処理申請が先に並ぶため、
// 確定する行の特定にも、後片付けで自分の申請を引き直すのにも使う。
let memberCode = '';
let createdCustomerId = '';
let createdCastId = '';
let createdCastName = '';
let createdShiftId = '';
let createdOrderId = '';

/**
 * 未処理の予約申請のうち、このシナリオの会員が出したものの id を引く
 * （GET /api/store/orders/work-queue の未確定群）。UI から出した申請は応答を取り損ねると id が
 * 手元に無いため、後片付けはこの引き直しを拠り所にする。
 */
async function findPendingRequestIds(
  request: APIRequestContext,
  token: string,
  code: string
): Promise<string[]> {
  const res = await request.get('/api/store/orders/work-queue', {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    params: { statuses: 'CREATED', size: 100 },
  });
  if (!res.ok()) {
    throw new Error(`list work queue failed: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  const rows = body.content as Array<{ id: string; requester_member_code: string | null }>;
  return rows.filter(row => row.requester_member_code === code).map(row => row.id);
}

// 後片付けの失敗を黙って握り潰すと、消し残った申請が次の run を落とす連鎖になる。
// 後続の片付けは続けたいのでテストは倒さず、痕跡だけ残す。
const warnCleanupFailure = (error: unknown) => {
  console.warn(`[member-reservation cleanup] ${String(error)}`);
};

Given('会員を登録し店舗側で顧客台帳に紐づける', async ({ request }) => {
  const suffix = Date.now();
  memberEmail = `member-reservation-e2e-${suffix}@kizuna.test`;
  memberCode = await registerMember(request, memberEmail, MEMBER_PASSWORD, `E2E会員-${suffix}`);

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
  createdCastName = `E2E予約キャスト-${Date.now()}`;
  createdCastId = await createCast(request, adminToken, createdCastName);
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
  // 件数ではなく作成したキャスト自身を名指すのは、共有 dev 店舗に本日出勤の別キャストが
  // 居ても成立させるため（選択肢の総数は他シナリオの残留シフトで動く）。
  await expect(
    page.getByLabel('指名（任意）').getByRole('option', { name: createdCastName })
  ).toHaveCount(1, { timeout: 15000 });
});

When('人数 {string} で予約を申請する', async ({ page }, pax: string) => {
  await page.getByLabel('利用日').fill(todayInTokyo());
  await page.getByLabel('店舗へ名乗るお名前').fill(DECLARED_NAME);
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

When('店舗管理者が受注一覧の「未確定」群で予約を確定する', async ({ page }) => {
  const storeId = await loginViaUiAndEnterStore(page);
  await page.goto(`${PLATFORM_URL}/store/${storeId}/orders`);
  // 群は店舗共有なので、先頭を掴むと先に残っている他人の申請を確定してしまう。
  // カードに出る会員コードでこのシナリオの申請だけを名指す。
  const request = page
    .getByRole('listitem')
    .filter({ hasText: 'WEB申請' })
    .filter({ hasText: memberCode });
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
  // 受注を消す口は無い（ADR 0013）。未確定のまま終わった申請は謝絶で、確定まで進んだものは
  // 理由付きの取消で終端へ送り、対応が要る群から外す。
  // 対象は id ではなく会員コードで引き直す — 申請の POST 応答を取り損ねた中断でも残さないため。
  const pendingIds = memberCode
    ? await findPendingRequestIds(request, adminToken, memberCode).catch(error => {
        warnCleanupFailure(error);
        return [] as string[];
      })
    : [];
  for (const id of pendingIds) {
    await declineOrder(request, adminToken, id).catch(warnCleanupFailure);
  }
  if (createdOrderId) {
    // 確定まで進んだ受注は取消で終端へ送る。既に終端なら撥ねられるが、それは想定内なので
    // 握り潰す（他の後片付けと違い「既に片付いている」と区別がつかない）。
    await cancelOrder(request, adminToken, createdOrderId, 'e2e の後片付け').catch(() => {});
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
  memberCode = '';
  createdCustomerId = '';
  createdCastId = '';
  createdCastName = '';
  createdShiftId = '';
  createdOrderId = '';
});
