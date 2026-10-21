import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';

const { Then } = createBdd();

// このシナリオが使う他ステップ（ログイン・従業員追加・サイドバー遷移）は playwright-bdd が
// steps/**/*.ts を横断解決するため再定義しない。

Then('受注一覧が権限エラーなく表示される', async ({ page }) => {
  // 正当な 403 はページ遷移を起こさないため、URL 断言だけでは権限エラーを検出できない。
  // 受注一覧を再取得し、X-Store-ID が注入された GET /store/orders 応答が 200 であることを直接確認する。
  const [response] = await Promise.all([
    page.waitForResponse(
      resp => resp.url().includes('/store/orders') && resp.request().method() === 'GET',
      { timeout: 15000 }
    ),
    page.reload({ waitUntil: 'domcontentloaded' }),
  ]);
  expect(response.status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'オーダー一覧', exact: true })).toBeVisible();
});
