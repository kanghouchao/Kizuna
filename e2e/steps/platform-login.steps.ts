import { expect, Locator, Page } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import { createCast, deleteCast, loginAsStoreAdmin } from './store-api';

const { Given, When, Then, After } = createBdd();

// 統一ログインは platform ドメイン（kizuna.test）で提供され、店舗ロールのセッションも
// 平台 cookie + X-Store-ID 注入で platform ドメイン上のまま /store/* を操作する（#324 D8/D9）。
// cookie はオリジン別に分離されるため、ログイン後の遷移先も含め常に PLATFORM_URL を用いる。
const PLATFORM_LOGIN_URL = `${PLATFORM_URL}/platform/login`;
const CASTS_URL = `${PLATFORM_URL}/store/casts`;

// Header の店舗切替ドロップダウンは Headless UI Menu（唯一の aria-haspopup 要素）。
// アカウントメニューは group-hover の素の要素で aria-haspopup を持たないため一意に特定できる。
const storeSwitchToggle = (page: Page): Locator => page.locator('header [aria-haspopup]');

// 播種したキャストの id / 一意名。scenario 2 の Given でのみ設定し、断言と後始末で共有する。
let createdCastId = '';
let createdCastName = '';

/** 店舗切替ドロップダウンを開いた状態にし、開いたメニュー要素を返す（既に開いていれば再クリックしない）。 */
async function openStoreSwitch(page: Page): Promise<Locator> {
  const toggle = storeSwitchToggle(page);
  // stores() の取得完了までボタンは disabled。有効化＝一覧ロード済みを待ってから開く。
  await expect(toggle).toBeEnabled();
  const menu = page.getByRole('menu');
  if ((await menu.count()) === 0) {
    await toggle.click();
    await expect(menu).toBeVisible();
  }
  return menu;
}

Given('統一ログイン画面を開く', async ({ page }) => {
  await page.goto(PLATFORM_LOGIN_URL);
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

Then('中央ダッシュボードへ遷移する', async ({ page }) => {
  await expect(page).toHaveURL(/\/platform\/dashboard\/?$/, { timeout: 15000 });
});

Then('店舗ダッシュボードへ遷移する', async ({ page }) => {
  await expect(page).toHaveURL(/\/store\/dashboard\/?$/, { timeout: 15000 });
});

Then(
  '店舗切替に {string} と {string} が表示される',
  async ({ page }, first: string, second: string) => {
    const menu = await openStoreSwitch(page);
    await expect(menu.getByText(first, { exact: true })).toBeVisible();
    await expect(menu.getByText(second, { exact: true })).toBeVisible();
  }
);

Then('店舗切替に {string} が表示される', async ({ page }, storeName: string) => {
  const menu = await openStoreSwitch(page);
  await expect(menu.getByText(storeName, { exact: true })).toBeVisible();
});

Then('店舗切替に {string} が表示されない', async ({ page }, storeName: string) => {
  // 直前の「表示される」ステップで開いたメニューに対して、非授権店舗が項目に無いことを断言する。
  const menu = await openStoreSwitch(page);
  await expect(menu.getByText(storeName, { exact: true })).toHaveCount(0);
});

Then('キャスト一覧に {string} が表示される', async ({ page }, _label: string) => {
  // {string} は可読性のための表記。実際の照合は一意名（createdCastName）で行う。
  await page.goto(CASTS_URL, { waitUntil: 'domcontentloaded' });
  await expect(page.getByText(createdCastName, { exact: true })).toBeVisible({ timeout: 15000 });
});

When('店舗を {string} に切り替える', async ({ page }, storeName: string) => {
  const menu = await openStoreSwitch(page);
  await menu.getByText(storeName, { exact: true }).click();
  // handleStoreSelect は setPlatformStore(id) 後に window.location.reload() する。
  // リロード後のヘッダに切替先店舗名が反映される＝新しい店舗文脈が有効になったことを待つ。
  await expect(storeSwitchToggle(page)).toContainText(storeName, { timeout: 15000 });
});

Then('キャスト一覧に {string} が表示されない', async ({ page }, _label: string) => {
  // 切替後の店舗2はキャスト未登録のため一覧は空。cookie は切替時に同期設定済みなので
  // 再取得のため一覧を開き直し、空表示の確定後に播種キャストの非表示を断言する。
  await page.goto(CASTS_URL, { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('キャストが登録されていません', { exact: true })).toBeVisible({
    timeout: 15000,
  });
  await expect(page.getByText(createdCastName, { exact: true })).toHaveCount(0);
});

// 播種した cast を無条件で片付ける（テスト失敗・途中クラッシュでも実行）。
// 復元 hook 自身が独立してログインし直すため、テスト側の状態に依存しない。ベストエフォート。
After({ tags: '@platform-login-store-switch' }, async ({ request }) => {
  if (!createdCastId) return;
  const token = await loginAsStoreAdmin(request);
  await deleteCast(request, token, createdCastId).catch(() => {});
  createdCastId = '';
  createdCastName = '';
});
