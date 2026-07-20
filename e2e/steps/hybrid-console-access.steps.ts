import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';

const { Then } = createBdd();

// このシナリオが使う他ステップ（統一ログイン画面を開く / メール...でログインする /
// 中央ダッシュボードへ遷移する / スタッフ管理画面を開く / 氏名...でスタッフを追加する /
// 作成したスタッフのメールとパスワード...でログインする / サイドバーの...リンクをクリックする /
// URL が...で終わる）は playwright-bdd が steps/**/*.ts を横断解決するため再定義しない。

Then('受注一覧が権限エラーなく表示される', async ({ page }) => {
  // #413 の直接回帰確認: 混成束ユーザーは console が 'platform' に解決されるが、apiClient は
  // パス由来（/store/{id}/orders）の X-Store-ID を注入するため受注一覧 API が 403 にならず 200 を返す。
  // 新形式セッションの正当な 403 はページ遷移を起こさない（client.ts の設計）ため、URL 断言だけでは
  // 403 を検出できない。現在の受注一覧を再取得し、GET /store/orders 応答が 200 であることを直接確認する。
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
