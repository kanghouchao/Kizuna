import { Page, expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';

const { When, Then } = createBdd();

// このシナリオが使う他ステップ（統一ログイン画面を開く / メール...でログインする /
// 中央ダッシュボード・店舗業務画面へ遷移する）は platform-login.steps.ts が定義する。

// 任命した店長はシナリオごとに一意化する（過去 run の残骸との重複＝strict モード違反を避ける）。
// メールは氏名と別に採番する（type="email" のネイティブ検証は local-part に日本語を許さない）。
let appointedName = '';
let appointedEmail = '';

const APPOINTED_PASSWORD = 'pass1234';

/** 店長設定の節。頁本体（店舗の基本情報）と同じ画面に並ぶので、名前付き region で絞る。 */
function managerSection(page: Page) {
  return page.getByRole('region', { name: '店長設定' });
}

async function dismissAppointed(page: Page) {
  const row = managerSection(page).getByRole('listitem').filter({ hasText: appointedName });
  await expect(row).toBeVisible({ timeout: 15000 });
  await row.getByRole('button', { name: '解任', exact: true }).click();
  await page.getByRole('button', { name: '解任する', exact: true }).click();
}

async function openAppointDialog(page: Page) {
  await page.getByRole('button', { name: '店長を任命', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '店長を任命' });
  await expect(dialog).toBeVisible();
  return dialog;
}

// 一覧からの遷移にすることで、編集画面へ辿り着く導線そのものも同時に固定する。
When('店舗 {string} の編集画面を開く', async ({ page }, storeName: string) => {
  await page.locator('aside').getByRole('link', { name: '店舗一覧', exact: true }).click();
  await expect(page).toHaveURL(/\/platform\/stores\/?$/, { timeout: 15000 });
  // 店舗名は前方一致で重なる（"Sample Tenant" と "Sample Tenant 2"）ため、行はセルの完全一致で絞る。
  await page
    .getByRole('row')
    .filter({ has: page.getByRole('cell', { name: storeName, exact: true }) })
    .getByRole('button', { name: '編集' })
    .click();
  await expect(page.getByRole('heading', { name: '店長設定', exact: true })).toBeVisible({
    timeout: 15000,
  });
});

When('氏名 {string} で新規作成して店長に任命する', async ({ page }, baseName: string) => {
  const stamp = Date.now();
  appointedName = `${baseName}-${stamp}`;
  appointedEmail = `store-manager-e2e-${stamp}@kizuna.test`;
  const dialog = await openAppointDialog(page);
  await dialog.getByRole('tab', { name: '新規作成', exact: true }).click();
  await dialog.getByLabel('メールアドレス', { exact: true }).fill(appointedEmail);
  await dialog.getByLabel('初期パスワード', { exact: true }).fill(APPOINTED_PASSWORD);
  await dialog.getByLabel('氏名', { exact: true }).fill(appointedName);
  await dialog.getByRole('button', { name: '作成して任命', exact: true }).click();
  // 成功時のみモーダルが閉じる（失敗時はエラートーストのまま開いたまま）。
  await expect(dialog).toBeHidden({ timeout: 15000 });
});

// 候補は「まだこの店舗の店長でない」母集団なので、別店舗で任命済みの本人がここに現れること自体が
// 外積の帰結（担当店舗の追加として任命が効くこと）の観測点になる。
When('任命した店長を既存アカウントから任命する', async ({ page }) => {
  const dialog = await openAppointDialog(page);
  await dialog.getByLabel('任命候補を検索', { exact: true }).fill(appointedName);
  await dialog.getByRole('button', { name: '検索', exact: true }).click();
  const row = dialog.getByRole('listitem').filter({ hasText: appointedName });
  await expect(row).toBeVisible({ timeout: 15000 });
  await row.getByRole('button', { name: '任命', exact: true }).click();
  await expect(dialog).toBeHidden({ timeout: 15000 });
});

When('任命した店長を解任する', async ({ page }) => {
  await dismissAppointed(page);
});

// 拒否側も自分で作った店長で撃つ。種子の店長（田中花子）は 2 店舗を担当しているので最後の 1 店では
// なく、しかも解任が通ってしまうと以降のシナリオが使う共有の生きた店舗を壊す（実測: 3 本が連鎖して赤）。
When('任命した店長を解任しようとする', async ({ page }) => {
  await dismissAppointed(page);
  // 確認ダイアログが畳まれるのを待ってから頁を読み直す。押した直後の一覧はまだ解任前のままで、
  // 「行が残っている」は解任が通った世界でも一瞬は真になる — 読み直して初めてサーバの状態を見る。
  await expect(page.getByRole('button', { name: '解任する', exact: true })).toBeHidden({
    timeout: 15000,
  });
  await page.reload({ waitUntil: 'domcontentloaded' });
});

When('任命した店長でログインし直す', async ({ page, context }) => {
  // token cookie を捨ててからログイン画面へ戻る。残したままだと守衛が前の身分のまま
  // 着地先へ飛ばし、新しい資格情報が使われない。
  await context.clearCookies();
  await page.goto(`${PLATFORM_URL}/platform/login`, { waitUntil: 'domcontentloaded' });
  await page.getByLabel('メールアドレス', { exact: true }).fill(appointedEmail);
  await page.getByLabel('パスワード', { exact: true }).fill(APPOINTED_PASSWORD);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
});

Then('店長一覧に任命した店長が表示される', async ({ page }) => {
  await expect(managerSection(page).getByText(appointedName, { exact: true })).toBeVisible({
    timeout: 15000,
  });
});

/**
 * 「行が消える」だけを見る。トーストは寿命が短くて取り逃すため、解任が通ったことの根拠は
 * 一覧の取り直し後に対象が居ないことに置く。
 */
Then('店長一覧から任命した店長が消える', async ({ page }) => {
  await expect(managerSection(page).getByText(appointedName, { exact: true })).toHaveCount(0, {
    timeout: 15000,
  });
});

// 拒否は「読み直しても行が残る」ことで観測する。撥ねたのがサーバであることは統合テストが持つ（IT の 400 断言）。
Then('店長一覧に任命した店長が残る', async ({ page }) => {
  await expect(managerSection(page).getByText(appointedName, { exact: true })).toBeVisible({
    timeout: 15000,
  });
});
