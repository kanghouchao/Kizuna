import { expect } from '@playwright/test';
import { createBdd, DataTable } from 'playwright-bdd';
import { APP_URL } from '../base-url';
import {
  STORE1_ID,
  createStaffAccount,
  createStore,
  findRoleIdByName,
  findStoreIdByDomain,
  getAuthorizedStores,
  loginUser,
  revealSidebarLink,
} from './store-api';

// initial-setup プロジェクトはこのファイルだけを読む（他の steps の無条件 After は未作成の店長で
// ログインしに行き、最初のシナリオを落とす）。本体プロジェクトからは読まれるため、ここには
// 無条件の hook も既存と重なる文言も置かない。
const { Given, When, Then } = createBdd();

const INITIAL_SETUP_API = `${APP_URL}/api/auth/initial-setup`;
const INITIAL_SETUP_URL = `${APP_URL}/auth/initial-setup`;

let adminToken = '';
let createdStaff: { email: string; password: string; storeIds: string[] }[] = [];

Given('初期設定の要否 API が required: {word} を返す', async ({ request }, required: string) => {
  const response = await request.get(INITIAL_SETUP_API);
  expect(response.status()).toBe(200);
  expect(await response.json()).toEqual({ required: JSON.parse(required) });
});

Given('初期設定画面を開く', async ({ page }) => {
  await page.goto(INITIAL_SETUP_URL);
  await expect(page.getByRole('heading', { name: '初期設定', exact: true })).toBeVisible();
  await expect(page.getByText('最初の管理者アカウントを登録してください', { exact: true })).toBeVisible();
});

When(
  'メールアドレス {string}・表示名 {string}・パスワード {string} で最初の管理者を登録する',
  async ({ page }, email: string, displayName: string, password: string) => {
    await page.getByLabel('メールアドレス', { exact: true }).fill(email);
    await page.getByLabel('表示名', { exact: true }).fill(displayName);
    await page.getByLabel('パスワード（8文字以上）', { exact: true }).fill(password);
    await page.getByLabel('パスワード（確認）', { exact: true }).fill(password);
    await page.getByRole('button', { name: '登録する', exact: true }).click();
  }
);

Then('自動でログインしてダッシュボードへ遷移する', async ({ page }) => {
  // 自動ログインだけが失敗した場合はログイン画面へ戻るため、着地先で両者を見分けられる。
  await expect(page).toHaveURL(/\/admin\/dashboard\/?$/, { timeout: 15000 });
});

// 遷移はサイドバー経由にし、メニューに出ていること自体も到達の条件に含める。
When('サイドバーの {string} から {string} を開く', async ({ page }, linkName: string, path: string) => {
  const link = await revealSidebarLink(page, aside => aside.getByRole('link', { name: linkName, exact: true }));
  await link.click();
  await expect(page).toHaveURL(new RegExp(`${path}/?$`), { timeout: 15000 });
});

Then('一覧の {string} の行に {string} が表示される', async ({ page }, rowText: string, cellText: string) => {
  const row = page.getByRole('row').filter({ hasText: rowText });
  await expect(row.getByRole('cell', { name: cellText, exact: true })).toBeVisible({ timeout: 15000 });
});

Then('店舗一覧に {string} と表示される', async ({ page }, message: string) => {
  await expect(page.getByText(message, { exact: true })).toBeVisible({ timeout: 15000 });
});

Given('{string} とパスワード {string} で API にログインする', async ({ request }, email: string, password: string) => {
  adminToken = await loginUser(request, email, password);
});

When('次の店舗を登録する', async ({ request }, table: DataTable) => {
  // 登録順が採番順になり、先頭の店舗が STORE1_ID を得る。
  for (const row of table.hashes()) {
    await createStore(request, adminToken, {
      name: row['店舗名'],
      short_name: row['略称'],
      domain: row['ドメイン'],
    });
  }
});

Then('店舗 {string} の id が店舗文脈の固定 id と一致する', async ({ request }, domain: string) => {
  // store-api.ts は店舗文脈の id を STORE1_ID に固定している。採番がずれると、それを使う全シナリオが
  // 別の店舗を指すため、ここで止める。
  expect(await findStoreIdByDomain(request, adminToken, domain)).toBe(STORE1_ID);
});

When('次の従業員を作成する', async ({ request }, table: DataTable) => {
  createdStaff = [];
  for (const row of table.hashes()) {
    const storeIds: string[] = [];
    for (const domain of row['店舗'].split(',')) {
      storeIds.push(await findStoreIdByDomain(request, adminToken, domain.trim()));
    }
    await createStaffAccount(request, adminToken, {
      email: row['メールアドレス'],
      password: row['パスワード'],
      displayName: row['氏名'],
      roleIds: [await findRoleIdByName(request, adminToken, row['ロール'])],
      storeIds: storeIds.map(Number),
    });
    createdStaff.push({ email: row['メールアドレス'], password: row['パスワード'], storeIds });
  }
});

Then('作成した各従業員がそのパスワードでログインでき、授権店舗が指定どおりになる', async ({ request }) => {
  expect(createdStaff.length).toBeGreaterThan(0);
  for (const staff of createdStaff) {
    const token = await loginUser(request, staff.email, staff.password);
    const stores = await getAuthorizedStores(request, token);
    expect(stores.map((store) => String(store.id)).sort()).toEqual([...staff.storeIds].sort());
  }
});

When('初期設定画面の URL へアクセスする', async ({ page }) => {
  await page.goto(INITIAL_SETUP_URL);
});

Then('ログイン画面へリダイレクトされる', async ({ page }) => {
  await expect(page).toHaveURL(/\/auth\/login\/?$/, { timeout: 15000 });
  await expect(page.getByRole('button', { name: 'ログイン', exact: true })).toBeVisible();
});

Then(
  '初期設定の登録 API にメールアドレス {string}・パスワード {string} を送ると {int} が返る',
  async ({ request }, email: string, password: string, status: number) => {
    // 入力検証が先に 400 で撥ねないよう、未使用のメールアドレスで正しい形の要求を送る。
    const response = await request.post(INITIAL_SETUP_API, {
      data: { email, password, display_name: '後続の管理者' },
    });
    expect(response.status(), await response.text()).toBe(status);
  }
);
