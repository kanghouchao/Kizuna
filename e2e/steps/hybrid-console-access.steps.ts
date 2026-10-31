import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';

const { Then, When } = createBdd();

// シェルが自分で取るもの。/admin と /store を跨いでも再取得されないことを要求回数で見る。
const SHELL_REQUESTS = ['/api/stores/me', '/api/me', '/api/menus/me'];
let shellRequests: string[] = [];

// このシナリオが使う他ステップ（ログイン・従業員追加・サイドバー遷移）は playwright-bdd が
// steps/**/*.ts を横断解決するため再定義しない。

Then('受注一覧が権限エラーなく表示される', async ({ page }) => {
  // 正当な 403 はページ遷移を起こさないため、URL 断言だけでは権限エラーを検出できない。
  // 受注一覧を再取得し、一覧が読む作業キュー（X-Store-ID が注入された GET /api/orders/work-queue）の応答が
  // 200 であることを直接確認する。
  const [response] = await Promise.all([
    page.waitForResponse(
      resp =>
        new URL(resp.url()).pathname === '/api/orders/work-queue' &&
        resp.request().method() === 'GET',
      { timeout: 15000 }
    ),
    page.reload({ waitUntil: 'domcontentloaded' }),
  ]);
  expect(response.status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'オーダー一覧', exact: true })).toBeVisible();
});

When('管理画面のシェルの取得要求を数え始める', async ({ page }) => {
  // ログイン直後の文書のシェルはまだ取得中でありうる。数え始める前に終わらせないと、
  // reload で打ち切られる直前の旧文書の要求まで数えてしまう。ヘッダーの本人表示は /me、
  // サイドバーの項目は授権店舗とメニューの両方が応答してから描かれる。
  await expect(page.locator('header').getByText(/@/)).toBeVisible();
  await expect(page.locator('aside').getByRole('link').first()).toBeVisible();
  shellRequests = [];
  page.on('request', request => {
    const path = new URL(request.url()).pathname;
    if (request.method() === 'GET' && SHELL_REQUESTS.includes(path)) shellRequests.push(path);
  });
  // 数え始めた時点のシェルを確実に一度描かせる。以降の遷移はすべてクライアント遷移になる。
  await page.reload({ waitUntil: 'networkidle' });
});

Then('授権店舗一覧と本人情報とメニューはシェルの初回描画で一度ずつだけ取得されている', async ({ page }) => {
  await page.waitForLoadState('networkidle');
  expect([...shellRequests].sort()).toEqual([...SHELL_REQUESTS].sort());
});
