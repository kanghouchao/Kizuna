import { Page, expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';

const { When, Then } = createBdd();

// 作成する氏名はシナリオごとに一意化する（過去 run の残骸との重複＝strict モード違反を避ける）。
// メールは氏名と別に採番する（type="email" のネイティブ検証は local-part に日本語を許さない）。
let createdStaffName = '';

/**
 * 作成した一意名で一覧を絞り込み、その行を返す。
 *
 * 一覧は 10 件ごとのページングのため、直前に作った 1 件が先頭ページに載る保証はない。
 * 検索を通してから照合することで、過去 run の残骸が何件積もっても行を特定できる。
 */
async function findCreatedStaffRow(page: Page) {
  await page.getByLabel('スタッフを検索', { exact: true }).fill(createdStaffName);
  await page.getByRole('button', { name: '検索', exact: true }).click();
  const row = page.getByRole('row', { name: new RegExp(createdStaffName) });
  await expect(row).toBeVisible({ timeout: 15000 });
  return row;
}

async function openCreateDialog(page: Page) {
  await page.getByRole('button', { name: 'スタッフを追加', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'スタッフを追加', exact: true })).toBeVisible();
  return page.getByRole('dialog', { name: 'スタッフを追加' });
}

// 遷移をサイドバーのリンク経由にすることで、メニュー行そのものの可視性も同時に固定する
// （店舗コンソールの着地先はメニュー由来なので、行が無ければこの画面には辿り着けない）。
When('店舗スタッフ管理画面を開く', async ({ page }) => {
  await page.locator('aside').getByRole('link', { name: 'スタッフ管理', exact: true }).click();
  await expect(page.getByRole('button', { name: 'スタッフを追加', exact: true })).toBeVisible({
    timeout: 15000,
  });
});

When('スタッフ追加モーダルを開く', async ({ page }) => {
  await openCreateDialog(page);
});

Then('ロールの選択肢に {string} がある', async ({ page }, roleLabel: string) => {
  const dialog = page.getByRole('dialog', { name: 'スタッフを追加' });
  await expect(dialog.getByRole('checkbox', { name: roleLabel, exact: true })).toBeVisible();
});

// 可授ロールはサーバが絞った集合そのままなので、選択肢に現れないこと自体が再委譲の禁止（G1）の観測点になる。
Then('ロールの選択肢に {string} がない', async ({ page }, roleLabel: string) => {
  const dialog = page.getByRole('dialog', { name: 'スタッフを追加' });
  await expect(dialog.getByRole('checkbox', { name: roleLabel, exact: true })).toHaveCount(0);
});

When(
  '氏名 {string}・ロール {string}・店舗 {string} でスタッフを追加する',
  async ({ page }, baseName: string, roleLabel: string, storeName: string) => {
    createdStaffName = `${baseName}-${Date.now()}`;
    const dialog = await openCreateDialog(page);
    await dialog.getByLabel('メールアドレス', { exact: true }).fill(`store-staff-e2e-${Date.now()}@kizuna.test`);
    await dialog.getByLabel('初期パスワード', { exact: true }).fill('pass1234');
    await dialog.getByLabel('氏名', { exact: true }).fill(createdStaffName);
    await dialog.getByRole('checkbox', { name: roleLabel, exact: true }).check();
    await dialog.getByRole('radio', { name: '個別店舗', exact: true }).click();
    await dialog.getByRole('checkbox', { name: storeName, exact: true }).check();
    await dialog.getByRole('button', { name: '追加する', exact: true }).click();
    // 成功時のみモーダルが閉じる（失敗時はエラートーストのまま開いたまま）。
    await expect(page.getByRole('heading', { name: 'スタッフを追加', exact: true })).toBeHidden({
      timeout: 15000,
    });
  }
);

When(
  '氏名 {string}・ロール {string}・全店舗でスタッフを追加しようとする',
  async ({ page }, baseName: string, roleLabel: string) => {
    createdStaffName = `${baseName}-${Date.now()}`;
    const dialog = await openCreateDialog(page);
    await dialog.getByLabel('メールアドレス', { exact: true }).fill(`store-staff-e2e-${Date.now()}@kizuna.test`);
    await dialog.getByLabel('初期パスワード', { exact: true }).fill('pass1234');
    await dialog.getByLabel('氏名', { exact: true }).fill(createdStaffName);
    await dialog.getByRole('checkbox', { name: roleLabel, exact: true }).check();
    // 全店舗は担当店舗集合（2 店舗）の上位集合なので、店舗部分集合の守衛（G2）がサーバで撥ねる。
    // 選択肢として出しておくのは、前端に「何を選べるか」の判定を複製させないためである。
    await dialog.getByRole('radio', { name: '全店舗', exact: true }).click();
    await dialog.getByRole('button', { name: '追加する', exact: true }).click();
  }
);

Then('スタッフ追加モーダルは開いたままになる', async ({ page }) => {
  await expect(page.getByRole('heading', { name: 'スタッフを追加', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '追加する', exact: true })).toBeEnabled({
    timeout: 15000,
  });
});

/**
 * 「モーダルが閉じない」だけだと、送信そのものが起きなかった場合にも真になる。
 * 行が作られていないことまで見て、拒否したのがサーバであることを確かめる（toast の寿命に依存しない）。
 */
Then('拒否されたスタッフは一覧に現れない', async ({ page }) => {
  await page.getByRole('button', { name: 'キャンセル', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'スタッフを追加', exact: true })).toBeHidden();
  await page.getByLabel('スタッフを検索', { exact: true }).fill(createdStaffName);
  await page.getByRole('button', { name: '検索', exact: true }).click();
  await expect(page.getByText('該当するスタッフが見つかりません', { exact: true })).toBeVisible({
    timeout: 15000,
  });
});

Then(
  'スタッフ一覧に {string} が {string} として表示される',
  async ({ page }, _label: string, roleLabel: string) => {
    // {string} は可読性のための表記。実際の照合は一意名（createdStaffName）で行う。
    const row = await findCreatedStaffRow(page);
    await expect(row.getByText(roleLabel, { exact: true })).toBeVisible();
  }
);
