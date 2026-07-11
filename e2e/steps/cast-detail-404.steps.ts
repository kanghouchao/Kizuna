import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { createCast, deleteCast, loginAsTenantAdmin } from './tenant-api';

const { Given, Then, After } = createBdd();

// store1 に属さない整形式 UUID（実在しない id）。テナント scoped 一覧に決して現れないため即断言できる。
const FOREIGN_CAST_ID = '00000000-0000-0000-0000-000000000000';

let createdCastId = '';
let createdCastName = '';

Given('店舗 {string} にキャスト {string} を作成する', async ({ request }, _store: string, name: string) => {
  const token = await loginAsTenantAdmin(request);
  createdCastId = await createCast(request, token, name);
  createdCastName = name;
});

Then('作成したキャストの詳細ページが表示される', async ({ page, $testInfo }) => {
  // fetchCasts は revalidate: 60 の ISR キャッシュのため、作成した cast が公開詳細に
  // 現れるまで最長 60 秒かかる。固定 sleep は使わず有界リトライで吸収する。
  // toPass の 90000ms は既定のテストタイムアウト（30000ms）を超えるため、既定値への加算で延長する
  // （絶対値だと前置ステップの消費時間次第でリトライ完了前にシナリオが打ち切られる）。
  $testInfo.setTimeout($testInfo.timeout + 100000);
  await expect(async () => {
    await page.goto(`/casts/${createdCastId}`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: createdCastName }).first()).toBeVisible();
  }).toPass({ timeout: 90000, intervals: [2000, 5000, 10000] });
});

Then('店舗に属さないキャスト ID の詳細ページは 404 になる', async ({ page }) => {
  const res = await page.goto(`/casts/${FOREIGN_CAST_ID}`);
  expect(res?.status()).toBe(404);
  await expect(page.getByRole('heading', { name: createdCastName }).first()).toHaveCount(0);
});

// 播種した cast を無条件で片付ける（テスト失敗・途中クラッシュでも実行）。
// 復元 hook 自身が独立してログインし直すため、テスト側の状態に依存しない。ベストエフォート。
After({ tags: '@cast-detail-404' }, async ({ request }) => {
  const token = await loginAsTenantAdmin(request);
  await deleteCast(request, token, createdCastId).catch(() => {});
  createdCastId = '';
  createdCastName = '';
});
