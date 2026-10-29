import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { APP_URL } from '../base-url';
import { createCast, deleteCast, loginAsStoreAdmin, switchStoreOnRail } from './store-api';

const { Given, When, Then, After } = createBdd();

// 統一ログインは アプリドメイン（kizuna.test）で提供され、店舗業務画面も同じドメイン上で
// X-Store-ID 注入により操作する。cookie はオリジン別に分離されるため、遷移先は常に APP_URL を用いる。
const LOGIN_URL = `${APP_URL}/auth/login`;

// 店舗業務 URL は店舗 id を持たず、表示店舗はサイドバーのレールが決める。
const CASTS_URL = `${APP_URL}/store/casts`;

// 播種したキャストの id / 一意名。scenario 2 の Given でのみ設定し、断言と後始末で共有する。
let createdCastId = '';
let createdCastName = '';

Given('統一ログイン画面を開く', async ({ page }) => {
  await page.goto(LOGIN_URL);
  await expect(page.getByRole('button', { name: 'ログイン', exact: true })).toBeVisible();
});

Given('店舗1に一意なキャスト {string} を API で登録する', async ({ request }, baseName: string) => {
  // 一意名で作成し、失敗した過去 run の残骸との重複（strict モード違反）を避ける（前提事実 25）。
  createdCastName = `${baseName}-${Date.now()}`;
  const token = await loginAsStoreAdmin(request);
  createdCastId = await createCast(request, token, createdCastName);
});

When(
  'メール {string} とパスワード {string} でログインする',
  async ({ page }, email: string, password: string) => {
    await page.getByLabel('メールアドレス', { exact: true }).fill(email);
    await page.getByLabel('パスワード', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  }
);

Then('ダッシュボードへ遷移する', async ({ page }) => {
  // ロールや授権店舗によらず、すべての従業員の着地先はダッシュボードである。
  await expect(page).toHaveURL(/\/admin\/dashboard\/?$/, { timeout: 15000 });
});

Then('キャスト一覧に {string} が表示される', async ({ page }, _label: string) => {
  // {string} は可読性のための表記。実際の照合は一意名（createdCastName）で行う。
  await page.goto(CASTS_URL, { waitUntil: 'domcontentloaded' });
  await page.getByPlaceholder('名前で検索...').fill(createdCastName);
  await page.getByRole('button', { name: '検索', exact: true }).click();
  await expect(page.getByText(createdCastName, { exact: true })).toBeVisible({ timeout: 15000 });
});

When('レールで店舗を {string} に切り替える', async ({ page }, storeName: string) => {
  await switchStoreOnRail(page, storeName);
});

Then('キャスト一覧に {string} が表示されない', async ({ page }, _label: string) => {
  // 対象の源氏名で検索し、他のキャストの有無によらず店舗境界を検証する。
  await page.goto(CASTS_URL, { waitUntil: 'domcontentloaded' });
  await page.getByPlaceholder('名前で検索...').fill(createdCastName);
  await page.getByRole('button', { name: '検索', exact: true }).click();
  await expect(page.getByText('キャストが登録されていません', { exact: true })).toBeVisible({
    timeout: 15000,
  });
  await expect(page.getByText(createdCastName, { exact: true })).toHaveCount(0);
});

// 播種した cast を無条件で片付ける（テスト失敗・途中クラッシュでも実行）。
// 復元 hook 自身が独立してログインし直すため、テスト側の状態に依存しない。ベストエフォート。
After({ tags: '@login-store-switch' }, async ({ request }) => {
  if (!createdCastId) return;
  const token = await loginAsStoreAdmin(request);
  await deleteCast(request, token, createdCastId).catch(() => {});
  createdCastId = '';
  createdCastName = '';
});
