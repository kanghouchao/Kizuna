import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { createCast, createShift, deleteCast, deleteShift, loginAsTenantAdmin } from './tenant-api';

const { Given, When, Then, After } = createBdd();

// 後端の「本日」判定（app.timezone 既定 Asia/Tokyo）に合わせて日付を計算する。
const todayInTokyo = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date());
const yesterdayInTokyo = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(
    new Date(Date.now() - 86400000)
  );

let createdShiftIds: string[] = [];
let createdCastIds: string[] = [];

Given(
  '店舗 {string} に出勤表検証用のシフトデータを準備する',
  async ({ request }, _store: string) => {
    const token = await loginAsTenantAdmin(request);
    const today = todayInTokyo();
    const yesterday = yesterdayInTokyo();

    const castA = await createCast(request, token, 'E2E出勤A');
    const castB = await createCast(request, token, 'E2E出勤B');
    const castC = await createCast(request, token, 'E2E出勤C');
    const castD = await createCast(request, token, 'E2E出勤D');
    createdCastIds.push(castA, castB, castC, castD);

    const shiftA = await createShift(request, token, {
      castId: castA,
      workDate: today,
      startTime: '20:00:00',
      endTime: '02:00:00',
      status: 'CONFIRMED',
    });
    const shiftB = await createShift(request, token, {
      castId: castB,
      workDate: today,
      startTime: '18:00:00',
      endTime: '20:00:00',
      status: 'CONFIRMED',
    });
    const shiftC = await createShift(request, token, {
      castId: castC,
      workDate: today,
      startTime: '12:00:00',
      endTime: '18:00:00',
      status: 'TENTATIVE',
    });
    const shiftD = await createShift(request, token, {
      castId: castD,
      workDate: yesterday,
      startTime: '18:00:00',
      endTime: '23:00:00',
      status: 'CONFIRMED',
    });
    createdShiftIds.push(shiftA, shiftB, shiftC, shiftD);
  }
);

When('出勤表ページを開く', async ({ page }) => {
  await page.goto('/schedule');
});

Then(
  '出勤表にキャスト {string} と時間帯 {string} が表示される',
  async ({ page }, castName: string, timeRange: string) => {
    await expect(page.getByRole('heading', { name: castName, exact: true })).toBeVisible();
    await expect(page.getByText(timeRange)).toBeVisible();
  }
);

Then('出勤表にキャスト {string} が表示されない', async ({ page }, castName: string) => {
  await expect(page.getByRole('heading', { name: castName, exact: true })).toHaveCount(0);
});

// 播種データを無条件で片付ける（テスト失敗・途中クラッシュでも実行）。
// 復元 hook 自身が独立してログインし直すため、テスト側の状態に依存しない。ベストエフォート。
After(async ({ request }) => {
  const token = await loginAsTenantAdmin(request);
  for (const id of createdShiftIds) {
    await deleteShift(request, token, id).catch(() => {});
  }
  for (const id of createdCastIds) {
    await deleteCast(request, token, id).catch(() => {});
  }
  createdShiftIds = [];
  createdCastIds = [];
});
