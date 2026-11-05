import { Page, expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { revealSidebarLink } from './store-api';

const { When, Then } = createBdd();

// 作成する氏名はシナリオごとに一意化する（過去 run の残骸との重複＝strict モード違反を避ける。
// login.steps.ts の createCast 手法に倣う）。
// メールは氏名と別に採番する（type="email" のネイティブ検証は local-part に日本語を許さないため、
// 氏名をそのままメールへ流用できない）。
let createdStaffName = '';
let createdStaffEmail = '';

const CREATE_URL = /\/admin\/staff\/create\/?$/;
const LIST_URL = /\/admin\/staff\/?$/;
const EDIT_URL = /\/admin\/staff\/\d+\/edit\/?$/;
const createHeading = (page: Page) => page.getByRole('heading', { name: '従業員を追加', exact: true });
const editHeading = (page: Page) =>
  page.getByRole('heading', { name: `${createdStaffName} の権限を編集`, exact: true });
const resultPanel = (page: Page) => page.getByRole('region', { name: 'この設定の結果' });

async function searchStaff(page: Page, keyword: string) {
  await page.getByLabel('従業員を検索', { exact: true }).fill(keyword);
  await page.getByRole('button', { name: '検索', exact: true }).click();
}

/**
 * 作成した一意名で一覧を絞り込み、その行を返す。
 *
 * 一覧は 10 件ごとのページングのため、直前に作った 1 件が先頭ページに載る保証はない。
 * 検索を通してから照合することで、過去 run の残骸が何件積もっても行を特定できる。
 */
async function findCreatedStaffRow(page: Page) {
  await searchStaff(page, createdStaffName);
  const row = page.getByRole('row', { name: new RegExp(createdStaffName) });
  await expect(row).toBeVisible({ timeout: 15000 });
  return row;
}

// 追加は一覧の link から作成画面へ遷移する（画面遷移は link のまま保つ）。
async function openCreatePage(page: Page) {
  await page.getByRole('link', { name: '従業員を追加', exact: true }).click();
  await expect(page).toHaveURL(CREATE_URL, { timeout: 15000 });
  await expect(createHeading(page)).toBeVisible();
}

/** 作成画面の入力欄を埋め、ロールを選ぶ。担当店舗の指定と送信は呼び手が行う。 */
async function fillCreatePage(page: Page, baseName: string, roleLabels: string[]) {
  createdStaffName = `${baseName}-${Date.now()}`;
  createdStaffEmail = `staff-e2e-${Date.now()}@kizuna.test`;
  await openCreatePage(page);
  await page.getByLabel('メールアドレス', { exact: true }).fill(createdStaffEmail);
  await page.getByLabel('初期パスワード', { exact: true }).fill('pass1234');
  await page.getByLabel('氏名', { exact: true }).fill(createdStaffName);
  // ロールはチェックボックス複数選択（兼務があるため単選ドロップダウンではない）。
  for (const roleLabel of roleLabels) {
    await page.getByRole('checkbox', { name: roleLabel, exact: true }).check();
  }
}

async function addStaffInStore(page: Page, baseName: string, roleLabels: string[], storeName: string) {
  await fillCreatePage(page, baseName, roleLabels);
  await page.getByRole('radio', { name: '個別店舗', exact: true }).click();
  await page.getByRole('checkbox', { name: storeName, exact: true }).check();
  await page.getByRole('button', { name: '追加する', exact: true }).click();
  // 成功時だけ一覧へ戻る（失敗時はエラートーストのまま作成画面に留まる）。
  await expect(page).toHaveURL(LIST_URL, { timeout: 15000 });
}

// 遷移をサイドバーのリンク経由にすることで、メニュー行そのものの可視性も同時に固定する。
When('従業員管理画面を開く', async ({ page }) => {
  const link = await revealSidebarLink(page, aside => aside.getByRole('link', { name: '従業員管理', exact: true }));
  await link.click();
  await expect(page).toHaveURL(/\/admin\/staff\/?$/, { timeout: 15000 });
  await expect(page.getByRole('link', { name: '従業員を追加', exact: true })).toBeVisible();
});

When(
  '氏名 {string}・ロール {string}・店舗 {string} で従業員を追加する',
  async ({ page }, baseName: string, roleLabel: string, storeName: string) => {
    await addStaffInStore(page, baseName, [roleLabel], storeName);
  }
);

When(
  '氏名 {string}・ロール {string} と {string}・店舗 {string} で従業員を追加する',
  async ({ page }, baseName: string, roleLabel1: string, roleLabel2: string, storeName: string) => {
    await addStaffInStore(page, baseName, [roleLabel1, roleLabel2], storeName);
  }
);

When(
  '氏名 {string}・ロール {string}・全店舗で従業員を追加しようとする',
  async ({ page }, baseName: string, roleLabel: string) => {
    await fillCreatePage(page, baseName, [roleLabel]);
    // 全店舗は担当店舗集合の上位集合なので、店舗部分集合の守衛がサーバで撥ねる。
    // 選択肢として出しておくのは、前端に「何を選べるか」の判定を複製させないためである。
    await page.getByRole('radio', { name: '全店舗', exact: true }).click();
    await page.getByRole('button', { name: '追加する', exact: true }).click();
  }
);

When('従業員追加画面を開く', async ({ page }) => {
  await openCreatePage(page);
});

Then('ロールの選択肢に {string} がある', async ({ page }, roleLabel: string) => {
  await expect(page.getByRole('checkbox', { name: roleLabel, exact: true })).toBeVisible();
});

// 可授ロールはサーバが絞った集合そのままなので、選択肢に現れないこと自体が委譲上限の観測点になる。
// 先に 1 つが描かれるのを待ち、読み込み中の空の組を「無い」と取り違えない。
Then('ロールの選択肢に {string} がない', async ({ page }, roleLabel: string) => {
  await expect(page.getByRole('group', { name: 'ロール', exact: true }).getByRole('checkbox').first()).toBeVisible();
  await expect(page.getByRole('checkbox', { name: roleLabel, exact: true })).toHaveCount(0);
});

Then('従業員追加画面に留まる', async ({ page }) => {
  await expect(page).toHaveURL(CREATE_URL);
  await expect(createHeading(page)).toBeVisible();
  await expect(page.getByRole('button', { name: '追加する', exact: true })).toBeEnabled({
    timeout: 15000,
  });
});

/**
 * 「画面に留まる」だけだと、送信そのものが起きなかった場合にも真になる。
 * 行が作られていないことまで見て、拒否したのがサーバであることを確かめる（toast の寿命に依存しない）。
 */
Then('拒否された従業員は一覧に現れない', async ({ page }) => {
  await page.getByRole('link', { name: 'キャンセル', exact: true }).click();
  await expect(page).toHaveURL(LIST_URL, { timeout: 15000 });
  await searchStaff(page, createdStaffName);
  await expect(page.getByText('該当する従業員が見つかりません', { exact: true })).toBeVisible({
    timeout: 15000,
  });
});

When(
  '作成した従業員のメールとパスワード {string} でログインする',
  async ({ page }, password: string) => {
    // 直前の「従業員を追加する」ステップで採番した一意メール（createdStaffEmail）でログインする。
    // 統一ログインのフォーム操作は login.steps.ts の「メール...でログインする」と同型。
    await page.getByLabel('メールアドレス', { exact: true }).fill(createdStaffEmail);
    await page.getByLabel('パスワード', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  }
);

When('{string} で従業員を検索する', async ({ page }, keyword: string) => {
  await searchStaff(page, keyword);
});

Then('従業員一覧に {string} を含む行が表示される', async ({ page }, keyword: string) => {
  await expect(page.getByRole('row', { name: new RegExp(keyword) })).toBeVisible({ timeout: 15000 });
});

Then(
  '従業員一覧に {string} が {string} として表示される',
  async ({ page }, _label: string, roleLabel: string) => {
    // {string} は可読性のための表記。実際の照合は一意名（createdStaffName）で行う。
    const row = await findCreatedStaffRow(page);
    await expect(row.getByText(roleLabel, { exact: true })).toBeVisible();
  }
);

When('{string} の編集画面を開く', async ({ page }, _label: string) => {
  const row = await findCreatedStaffRow(page);
  await row.getByRole('link', { name: '編集', exact: true }).click();
  await expect(page).toHaveURL(EDIT_URL, { timeout: 15000 });
  await expect(editHeading(page)).toBeVisible();
});

When('店舗集合を {string} に変更する', async ({ page }, scopeLabel: string) => {
  await page
    .getByRole('group', { name: '担当店舗', exact: true })
    .getByRole('radio', { name: scopeLabel, exact: true })
    .click();
});

Then('設定結果の要約に {string} が表示される', async ({ page }, text: string) => {
  // 結果は名前付きの領域に閉じている。同じ文言の選択肢（「全店舗」のラジオ等）と取り違えない。
  await expect(resultPanel(page)).toContainText(text);
});

When('保存する', async ({ page }) => {
  await editHeading(page).waitFor();
  await page.getByRole('button', { name: '保存する', exact: true }).click();
  await expect(page).toHaveURL(LIST_URL, { timeout: 15000 });
});
