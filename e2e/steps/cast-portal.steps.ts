import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import {
  acceptCastInvitation,
  approveShiftRequest,
  createCast,
  createShift,
  deleteCast,
  deleteShift,
  issueCastInvitation,
  loginAsStoreAdmin,
} from './store-api';

const { Given, When, Then, After } = createBdd();

// 後端の「本日」判定（app.timezone 既定 Asia/Tokyo）に合わせて日付を計算する（shift-public.steps.ts と同型）。
const todayInTokyo = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date());

const CAST_PASSWORD = 'pass12345';

// 播種した実体の id / 認証情報。CAST PlatformUser には削除 API が無いためタイムスタンプ付き
// メールアドレスで隔離し残留を許容する（cast_id/shift_id/shift_request_id は明示削除 + FK CASCADE で片付ける）。
let createdCastId = '';
let createdCastEmail = '';
let createdShiftId = '';
let createdShiftRequestId = '';

Given(
  '店舗管理者としてキャストを作成し招待を受諾してキャスト用アカウントを用意する',
  async ({ request }) => {
    const suffix = Date.now();
    createdCastEmail = `cast-portal-e2e-${suffix}@kizuna.test`;

    const adminToken = await loginAsStoreAdmin(request);
    createdCastId = await createCast(request, adminToken, `E2Eキャストポータル-${suffix}`);
    const invitationToken = await issueCastInvitation(request, adminToken, createdCastId);
    await acceptCastInvitation(
      request,
      invitationToken,
      createdCastEmail,
      CAST_PASSWORD,
      `E2Eキャストポータル-${suffix}`
    );
  }
);

Given('本日の確定シフトを API で作成する', async ({ request }) => {
  const adminToken = await loginAsStoreAdmin(request);
  createdShiftId = await createShift(request, adminToken, {
    castId: createdCastId,
    workDate: todayInTokyo(),
    startTime: '18:00:00',
    endTime: '20:00:00',
    status: 'CONFIRMED',
  });
});

When('作成したキャストのメールとパスワードでログインする', async ({ page }) => {
  // 統一ログインのフォーム操作は platform-login.steps.ts の「メール...でログインする」と同型。
  await page.getByLabel('メールアドレス', { exact: true }).fill(createdCastEmail);
  await page.getByLabel('パスワード', { exact: true }).fill(CAST_PASSWORD);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
});

Then('キャストポータルのスケジュール画面へ遷移する', async ({ page }) => {
  await expect(page).toHaveURL(/\/cast\/schedule\/?$/, { timeout: 15000 });
});

Then(
  'スケジュールに {string} と時間帯 {string} が表示される',
  async ({ page }, storeName: string, timeRange: string) => {
    await expect(page.getByText(storeName, { exact: true })).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(timeRange)).toBeVisible();
  }
);

When('希望提出タブを開く', async ({ page }) => {
  await page.locator('nav').getByRole('link', { name: '希望提出', exact: true }).click();
  // 所属店舗セレクタの描画完了（マウント時の非同期読み込み）を待ってから後続の操作に入る。
  await expect(page.getByLabel('店舗')).not.toHaveValue('');
});

Then('希望提出タブを開き直す', async ({ page }) => {
  // 承認結果の反映には再取得が要る。同一ルートの再入場は remount しないため明示リロードする。
  await page.reload({ waitUntil: 'domcontentloaded' });
  await expect(page.getByLabel('店舗')).not.toHaveValue('');
});

When('スケジュールタブを開く', async ({ page }) => {
  await page.locator('nav').getByRole('link', { name: 'スケジュール', exact: true }).click();
});

When(
  '店舗 {string}・開始 {string}・終了 {string}・備考 {string} で出勤希望を提出する',
  async ({ page }, storeName: string, startTime: string, endTime: string, note: string) => {
    await page.getByLabel('店舗').selectOption({ label: storeName });
    // 勤務日は本日を明示指定する。フォーム既定の明日だと週末実行時に翌週へ落ち、
    // 承認後のスケジュール断言（当週ビュー）が曜日依存で失敗するため。
    await page.getByLabel('日付').fill(todayInTokyo());
    await page.getByLabel('開始').fill(startTime);
    await page.getByLabel('終了').fill(endTime);
    await page.getByLabel('備考').fill(note);
    const [response] = await Promise.all([
      page.waitForResponse(
        resp =>
          resp.url().includes('/platform/me/shift-requests') && resp.request().method() === 'POST',
        { timeout: 15000 }
      ),
      page.getByRole('button', { name: '提出する' }).click(),
    ]);
    const body = await response.json();
    createdShiftRequestId = body.id as string;
  }
);

Then(
  '提出履歴に {string} の希望が {string} として表示される',
  async ({ page }, note: string, statusLabel: string) => {
    const item = page.locator('li').filter({ hasText: note });
    await expect(item).toBeVisible({ timeout: 15000 });
    await expect(item.getByText(statusLabel, { exact: true })).toBeVisible();
  }
);

When('店舗管理者が API で出勤希望を承認する', async ({ request }) => {
  const adminToken = await loginAsStoreAdmin(request);
  await approveShiftRequest(request, adminToken, createdShiftRequestId);
});

// 播種した実体を無条件で片付ける（テスト失敗・途中クラッシュでも実行）。CAST PlatformUser には
// 削除 API が無いため残留を許容する（タイムスタンプ付きメールアドレスで以後の run と隔離済み）。
// t_shift_requests は cast_id の FK CASCADE により cast 削除で自動的に片付く。
After(async ({ request }) => {
  const adminToken = await loginAsStoreAdmin(request);
  if (createdShiftId) {
    await deleteShift(request, adminToken, createdShiftId).catch(() => {});
  }
  if (createdCastId) {
    await deleteCast(request, adminToken, createdCastId).catch(() => {});
  }
  createdCastId = '';
  createdCastEmail = '';
  createdShiftId = '';
  createdShiftRequestId = '';
});
