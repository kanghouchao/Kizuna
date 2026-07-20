import { expect, Page } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import {
  ADMIN_EMAIL,
  ADMIN_PASSWORD,
  STORE_HEADERS,
  deleteCast,
  deleteCastFieldDefinition,
  loginAsStoreAdmin,
} from './store-api';

const { Given, When, Then, After } = createBdd();

// {ts} は実装時に Date.now() で一意化する（staff-management.steps.ts の手法を踏襲）。
// key/label は生成時に一度だけ解決し、以降のステップは受け取った引数の文字列ではなく
// この解決済みの値を使って照合する（{string} 引数は可読性のための表記、staff-management.steps.ts
// の _label 引数と同じ扱い）。
let createdFieldKey = '';
let createdFieldLabel = '';
// キャストの編集画面遷移時、一覧行の編集リンク（/store/casts/{id}/edit）から id を取り出して
// 後続の公開詳細ページ遷移・After での削除に使う（作成ステップは cast-detail-404.steps.ts の
// 既存 Given を再利用するため、その内部状態にはここから直接アクセスできない）。
let currentCastId = '';
let currentCastName = '';

async function addFieldDefinition(page: Page, rawKey: string, rawLabel: string, isPublic: boolean) {
  const ts = Date.now();
  createdFieldKey = rawKey.replace('{ts}', String(ts));
  createdFieldLabel = rawLabel.replace('{ts}', String(ts));

  await page.getByRole('button', { name: 'フィールドを追加', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'フィールドを追加' });
  await dialog.getByLabel('key', { exact: true }).fill(createdFieldKey);
  await dialog.getByLabel('label', { exact: true }).fill(createdFieldLabel);
  if (isPublic) {
    await dialog.getByRole('checkbox').check();
  }
  await dialog.getByRole('button', { name: '追加する', exact: true }).click();
  // 成功時のみモーダルが閉じる（失敗時はエラートーストのまま開いたまま、staff-management.steps.ts と同型）。
  await expect(page.getByRole('heading', { name: 'フィールドを追加', exact: true })).toBeHidden({
    timeout: 15000,
  });
}

Given('キャストカスタムフィールド管理画面を開く', async ({ page }) => {
  // 統一ログイン（/platform/login）は platform ドメインで提供され、店長ロールのセッションも
  // platform ドメイン上のまま /store/* を操作する（store-settings.steps.ts と同じ）。
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel('メールアドレス', { exact: true }).fill(ADMIN_EMAIL);
  await page.getByLabel('パスワード', { exact: true }).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'ログイン', exact: true }).click();
  await expect(page).toHaveURL(/\/store\/dashboard\/?$/, { timeout: 15000 });
  await page.goto(`${PLATFORM_URL}/store/casts/fields`);
  await expect(page.getByRole('button', { name: 'フィールドを追加', exact: true })).toBeVisible();
});

When(
  'key {string}・label {string}・公開する のフィールドを追加する',
  async ({ page }, key: string, label: string) => {
    await addFieldDefinition(page, key, label, true);
  }
);

When(
  'key {string}・label {string}・非公開 のフィールドを追加する',
  async ({ page }, key: string, label: string) => {
    await addFieldDefinition(page, key, label, false);
  }
);

Then('フィールド一覧に {string} が表示される', async ({ page }, _label: string) => {
  await expect(
    page.getByRole('row', { name: new RegExp(createdFieldLabel) })
  ).toBeVisible({ timeout: 15000 });
});

When(
  '{string} の編集画面で {string} に {string} と入力して保存する',
  async ({ page }, castName: string, _fieldLabel: string, value: string) => {
    currentCastName = castName;
    await page.goto(`${PLATFORM_URL}/store/casts`);
    const row = page.getByRole('row', { name: new RegExp(castName) });
    await expect(row).toBeVisible({ timeout: 15000 });
    const editLink = row.locator('a[href$="/edit"]');
    const href = await editLink.getAttribute('href');
    currentCastId = href?.match(/\/store\/casts\/([^/]+)\/edit/)?.[1] ?? '';
    await editLink.click();

    await expect(page.getByRole('heading', { name: 'キャスト編集', exact: true })).toBeVisible();
    await page.getByLabel(createdFieldLabel, { exact: true }).fill(value);
    await page.getByRole('button', { name: '保存する', exact: true }).click();
    await expect(page.getByText('キャスト情報を更新しました', { exact: true })).toBeVisible({
      timeout: 15000,
    });
  }
);

Then(
  '作成したキャストの詳細ページに {string} と {string} が表示される',
  async ({ page, $testInfo }, _fieldLabel: string, value: string) => {
    // fetchCasts は revalidate: 60 の ISR キャッシュのため、編集した値が公開詳細に
    // 現れるまで最長 60 秒かかる（cast-detail-404.steps.ts と同じ制約）。
    $testInfo.setTimeout($testInfo.timeout + 100000);
    await expect(async () => {
      await page.goto(`/casts/${currentCastId}`, { waitUntil: 'domcontentloaded' });
      await expect(page.getByText(createdFieldLabel, { exact: true })).toBeVisible();
      await expect(page.getByText(value, { exact: true })).toBeVisible();
    }).toPass({ timeout: 90000, intervals: [2000, 5000, 10000] });
  }
);

Then(
  '作成したキャストの詳細ページに {string} が表示されない',
  async ({ page, $testInfo }, _fieldLabel: string) => {
    // 非公開フィールドは ISR キャッシュの新旧に関わらず公開APIレスポンスに含まれないが、
    // ページ自体がキャッシュ未反映で読み込めていないだけの偽陽性を避けるため、
    // 見出し表示（＝最新データの取得成功）を確認してから非表示を断言する。
    $testInfo.setTimeout($testInfo.timeout + 100000);
    await expect(async () => {
      await page.goto(`/casts/${currentCastId}`, { waitUntil: 'domcontentloaded' });
      await expect(page.getByRole('heading', { name: currentCastName }).first()).toBeVisible();
    }).toPass({ timeout: 90000, intervals: [2000, 5000, 10000] });
    await expect(page.getByText(createdFieldLabel, { exact: true })).toHaveCount(0);
  }
);

When('キャストカスタムフィールド管理画面で {string} を削除する', async ({ page }, _label: string) => {
  await page.goto(`${PLATFORM_URL}/store/casts/fields`);
  const row = page.getByRole('row', { name: new RegExp(createdFieldLabel) });
  await expect(row).toBeVisible({ timeout: 15000 });
  page.once('dialog', dialog => dialog.accept());
  await row.getByRole('button', { name: '削除', exact: true }).click();
  await expect(row).toHaveCount(0, { timeout: 15000 });
});

Then('フィールド一覧から {string} が消える', async ({ page }, _label: string) => {
  await expect(page.getByRole('row', { name: new RegExp(createdFieldLabel) })).toHaveCount(0);
});

// 播種したキャスト・フィールド定義を無条件で片付ける（テスト失敗・途中クラッシュでも実行）。
// フィールド定義は UI 経由で作成するため id を直接持たず、key で一覧を照会して特定する。
// シナリオ2は本文中で UI から既に削除済みのため、その場合は該当なしで何もしない（ベストエフォート）。
After({ tags: '@cast-custom-fields' }, async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  if (currentCastId) {
    await deleteCast(request, token, currentCastId).catch(() => {});
  }
  if (createdFieldKey) {
    const res = await request.get('/api/store/casts/fields', {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    });
    if (res.ok()) {
      const definitions = (await res.json()) as { id: string; key: string }[];
      const match = definitions.find(definition => definition.key === createdFieldKey);
      if (match) {
        await deleteCastFieldDefinition(request, token, match.id).catch(() => {});
      }
    }
  }
  currentCastId = '';
  currentCastName = '';
  createdFieldKey = '';
  createdFieldLabel = '';
});
