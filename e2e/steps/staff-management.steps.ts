import { Page, expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';

const { When, Then } = createBdd();

const ADMIN_URL = `${PLATFORM_URL}/platform/staff`;

// 作成する氏名はシナリオごとに一意化する（過去 run の残骸との重複＝strict モード違反を避ける。
// platform-login.steps.ts の createCast 手法に倣う、前提事実 25）。
// メールは氏名と別に採番する（type="email" のネイティブ検証は local-part に日本語を許さないため、
// 氏名をそのままメールへ流用できない）。
let createdAdminName = '';
let createdAdminEmail = '';

/**
 * 作成した一意名で一覧を絞り込み、その行を返す。
 *
 * 一覧は 10 件ごとのページングのため、直前に作った 1 件が先頭ページに載る保証はない。
 * 検索を通してから照合することで、過去 run の残骸が何件積もっても行を特定できる。
 */
async function findCreatedAdminRow(page: Page) {
  const search = page.getByLabel('管理者を検索', { exact: true });
  await search.fill(createdAdminName);
  await page.getByRole('button', { name: '検索', exact: true }).click();
  const row = page.getByRole('row', { name: new RegExp(createdAdminName) });
  await expect(row).toBeVisible({ timeout: 15000 });
  return row;
}

When('管理者管理画面を開く', async ({ page }) => {
  await page.goto(ADMIN_URL, { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: '管理者を追加', exact: true })).toBeVisible();
});

When(
  '氏名 {string}・ロール {string}・店舗 {string} で管理者を追加する',
  async ({ page }, baseName: string, roleLabel: string, storeName: string) => {
    createdAdminName = `${baseName}-${Date.now()}`;
    createdAdminEmail = `staff-e2e-${Date.now()}@kizuna.test`;
    await page.getByRole('button', { name: '管理者を追加', exact: true }).click();

    const heading = page.getByRole('heading', { name: '管理者を追加', exact: true });
    await expect(heading).toBeVisible();

    const dialog = page.getByRole('dialog', { name: '管理者を追加' });
    await dialog.getByLabel('メールアドレス', { exact: true }).fill(createdAdminEmail);
    await dialog.getByLabel('初期パスワード', { exact: true }).fill('pass1234');
    await dialog.getByLabel('氏名', { exact: true }).fill(createdAdminName);
    // ロールはチェックボックス複数選択（兼務があるため単選ドロップダウンではない）。
    await dialog.getByRole('checkbox', { name: roleLabel, exact: true }).check();
    await dialog.getByRole('radio', { name: '個別店舗', exact: true }).click();
    await dialog.getByRole('checkbox', { name: storeName, exact: true }).check();
    await dialog.getByRole('button', { name: '追加する', exact: true }).click();
    // 成功時のみモーダルが閉じる（失敗時はエラートーストのまま開いたまま、#325）。
    await expect(heading).toBeHidden({ timeout: 15000 });
  }
);

When(
  '氏名 {string}・ロール {string} と {string}・店舗 {string} で管理者を追加する',
  async (
    { page },
    baseName: string,
    roleLabel1: string,
    roleLabel2: string,
    storeName: string
  ) => {
    createdAdminName = `${baseName}-${Date.now()}`;
    createdAdminEmail = `staff-e2e-${Date.now()}@kizuna.test`;
    await page.getByRole('button', { name: '管理者を追加', exact: true }).click();

    const heading = page.getByRole('heading', { name: '管理者を追加', exact: true });
    await expect(heading).toBeVisible();

    const dialog = page.getByRole('dialog', { name: '管理者を追加' });
    await dialog.getByLabel('メールアドレス', { exact: true }).fill(createdAdminEmail);
    await dialog.getByLabel('初期パスワード', { exact: true }).fill('pass1234');
    await dialog.getByLabel('氏名', { exact: true }).fill(createdAdminName);
    // ロールを2つチェックして混成ロール（例: HQ管理者＋店長）ユーザーを作る。
    await dialog.getByRole('checkbox', { name: roleLabel1, exact: true }).check();
    await dialog.getByRole('checkbox', { name: roleLabel2, exact: true }).check();
    await dialog.getByRole('radio', { name: '個別店舗', exact: true }).click();
    await dialog.getByRole('checkbox', { name: storeName, exact: true }).check();
    await dialog.getByRole('button', { name: '追加する', exact: true }).click();
    // 成功時のみモーダルが閉じる（失敗時はエラートーストのまま開いたまま、#325）。
    await expect(heading).toBeHidden({ timeout: 15000 });
  }
);

When(
  '作成した管理者のメールとパスワード {string} でログインする',
  async ({ page }, password: string) => {
    // 直前の「管理者を追加する」ステップで採番した一意メール（createdAdminEmail）でログインする。
    // 統一ログインのフォーム操作は platform-login.steps.ts の「メール...でログインする」と同型。
    await page.getByLabel('メールアドレス', { exact: true }).fill(createdAdminEmail);
    await page.getByLabel('パスワード', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  }
);

When('{string} で管理者を検索する', async ({ page }, keyword: string) => {
  await page.getByLabel('管理者を検索', { exact: true }).fill(keyword);
  await page.getByRole('button', { name: '検索', exact: true }).click();
});

Then('管理者一覧に該当が無いと表示される', async ({ page }) => {
  await expect(page.getByText('該当する管理者が見つかりません', { exact: true })).toBeVisible({
    timeout: 15000,
  });
});

Then(
  '管理者一覧に {string} が {string} として表示される',
  async ({ page }, _label: string, roleLabel: string) => {
    // {string} は可読性のための表記。実際の照合は一意名（createdAdminName）で行う。
    const row = await findCreatedAdminRow(page);
    await expect(row.getByText(roleLabel, { exact: true })).toBeVisible();
  }
);

When('{string} の編集モーダルを開く', async ({ page }, _label: string) => {
  const row = await findCreatedAdminRow(page);
  await row.getByRole('button', { name: '編集', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: `${createdAdminName} の権限を編集`, exact: true })
  ).toBeVisible();
});

When('店舗集合を {string} に変更する', async ({ page }, scopeLabel: string) => {
  const dialog = page.getByRole('dialog', { name: `${createdAdminName} の権限を編集` });
  // input の name 属性で担当店舗（store-scope-type）のラジオ群に限定し、ラベル文言の衝突に依存しない。
  await dialog
    .locator('label:has(input[name="store-scope-type"])', { hasText: scopeLabel })
    .click();
});

Then('設定結果の要約に {string} が表示される', async ({ page }, text: string) => {
  // 「この設定の結果」見出しの直後の要約段落を特定する（店舗名ラベルと文言が重複しうるため、
  // dialog.getByText(text) だけでは strict mode 違反になる。store-settings.steps.ts の
  // 隣接セレクタ手法に倣う）。
  const dialog = page.getByRole('dialog', { name: `${createdAdminName} の権限を編集` });
  const summary = dialog.locator('p:text-is("この設定の結果") + p');
  await expect(summary).toContainText(text);
});

When('保存する', async ({ page }) => {
  const dialog = page.getByRole('dialog', { name: `${createdAdminName} の権限を編集` });
  const heading = page.getByRole('heading', {
    name: `${createdAdminName} の権限を編集`,
    exact: true,
  });
  await dialog.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(heading).toBeHidden({ timeout: 15000 });
});
