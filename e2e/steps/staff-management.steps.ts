import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';

const { When, Then } = createBdd();

const STAFF_URL = `${PLATFORM_URL}/platform/staff`;

// 作成する氏名はシナリオごとに一意化する（過去 run の残骸との重複＝strict モード違反を避ける。
// platform-login.steps.ts の createCast 手法に倣う、前提事実 25）。
// メールは氏名と別に採番する（type="email" のネイティブ検証は local-part に日本語を許さないため、
// 氏名をそのままメールへ流用できない）。
let createdStaffName = '';
let createdStaffEmail = '';

// Modal/Drawer の Dialog ルート要素は子要素がすべて fixed 配置のため自身は 0x0 サイズになり、
// role=dialog を直接 toBeVisible/toBeHidden で判定すると常に「非表示」扱いになる（DESIGN.md の
// Modal/Drawer 仕様どおりの構造。ShiftFormModal と同型）。開閉判定は実サイズを持つ見出し
// （DialogTitle）で行う。

When('スタッフ管理画面を開く', async ({ page }) => {
  await page.goto(STAFF_URL, { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'スタッフを追加', exact: true })).toBeVisible();
});

When(
  '氏名 {string}・権限束 {string}・店舗 {string} でスタッフを追加する',
  async ({ page }, baseName: string, bundleLabel: string, storeName: string) => {
    createdStaffName = `${baseName}-${Date.now()}`;
    createdStaffEmail = `staff-e2e-${Date.now()}@kizuna.test`;
    await page.getByRole('button', { name: 'スタッフを追加', exact: true }).click();

    const heading = page.getByRole('heading', { name: 'スタッフを追加', exact: true });
    await expect(heading).toBeVisible();

    const dialog = page.getByRole('dialog', { name: 'スタッフを追加' });
    await dialog.getByLabel('メールアドレス', { exact: true }).fill(createdStaffEmail);
    await dialog.getByLabel('初期パスワード', { exact: true }).fill('pass1234');
    await dialog.getByLabel('氏名', { exact: true }).fill(createdStaffName);
    // 権限束はチェックボックス複数選択（#398 — ロール単選ドロップダウンの後継）。
    await dialog.getByRole('checkbox', { name: bundleLabel, exact: true }).check();
    await dialog.getByRole('radio', { name: '個別店舗', exact: true }).click();
    await dialog.getByRole('checkbox', { name: storeName, exact: true }).check();
    await dialog.getByRole('button', { name: '追加する', exact: true }).click();
    // 成功時のみモーダルが閉じる（失敗時はエラートーストのまま開いたまま、#325）。
    await expect(heading).toBeHidden({ timeout: 15000 });
  }
);

Then(
  'スタッフ一覧に {string} が {string} として表示される',
  async ({ page }, _label: string, bundleLabel: string) => {
    // {string} は可読性のための表記。実際の照合は一意名（createdStaffName）で行う。
    const row = page.getByRole('row', { name: new RegExp(createdStaffName) });
    await expect(row).toBeVisible({ timeout: 15000 });
    await expect(row.getByText(bundleLabel, { exact: true })).toBeVisible();
  }
);

When('{string} の編集ドロワーを開く', async ({ page }, _label: string) => {
  const row = page.getByRole('row', { name: new RegExp(createdStaffName) });
  await expect(row).toBeVisible({ timeout: 15000 });
  await row.getByRole('button', { name: '編集', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: `${createdStaffName} の権限を編集`, exact: true })
  ).toBeVisible();
});

When('店舗集合を {string} に変更する', async ({ page }, scopeLabel: string) => {
  const dialog = page.getByRole('dialog', { name: `${createdStaffName} の権限を編集` });
  // ドロワーには担当店舗と精算範囲の 2 つのラジオ群があり「全店舗」ラベルが重複するため、
  // input の name 属性（store-scope-type）で担当店舗側に限定する（strict mode 対応、#398）。
  await dialog
    .locator('label:has(input[name="store-scope-type"])', { hasText: scopeLabel })
    .click();
});

Then('設定結果の要約に {string} が表示される', async ({ page }, text: string) => {
  // 「この設定の結果」見出しの直後の要約段落を特定する（店舗名ラベルと文言が重複しうるため、
  // dialog.getByText(text) だけでは strict mode 違反になる。store-settings.steps.ts の
  // 隣接セレクタ手法に倣う）。
  const dialog = page.getByRole('dialog', { name: `${createdStaffName} の権限を編集` });
  const summary = dialog.locator('p:text-is("この設定の結果") + p');
  await expect(summary).toContainText(text);
});

When('保存する', async ({ page }) => {
  const dialog = page.getByRole('dialog', { name: `${createdStaffName} の権限を編集` });
  const heading = page.getByRole('heading', {
    name: `${createdStaffName} の権限を編集`,
    exact: true,
  });
  await dialog.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(heading).toBeHidden({ timeout: 15000 });
});

Then(
  'スタッフ一覧の {string} の担当店舗が {string} と表示される',
  async ({ page }, _label: string, scopeLabel: string) => {
    const row = page.getByRole('row', { name: new RegExp(createdStaffName) });
    await expect(row.getByText(scopeLabel, { exact: true })).toBeVisible({ timeout: 15000 });
  }
);
