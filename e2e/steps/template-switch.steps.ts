import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { getPublicTemplateKey, loginAsStoreAdmin, setTemplateKey } from './store-api';
import { BASE_URL } from '../base-url';

const { Given, When, Then, After } = createBdd();

Given('店舗 {string} の template_key が {string} である', async ({ request }, _store: string, templateKey: string) => {
  // 前回失敗の残留に依存しないよう前提を明示的に確定させる。
  const token = await loginAsStoreAdmin(request);
  await setTemplateKey(request, token, templateKey);
  expect(await getPublicTemplateKey(request)).toBe(templateKey);
});

When('template_key を {string} に変更する', async ({ request }, templateKey: string) => {
  const token = await loginAsStoreAdmin(request);
  await setTemplateKey(request, token, templateKey);
});

Then('新しいブラウザコンテキストで公開サイトが {word} 模版で描画される', async ({ browser }, templateKey: string) => {
  // cookie 無しの新規コンテキスト → middleware が no-store で最新 template_key を取得し
  // 新模版を即描画する（Q3-A）。伝播の一過性は有界リトライで吸収し、固定 sleep は使わない。
  const context = await browser.newContext({ baseURL: BASE_URL });
  try {
    const page = await context.newPage();
    await expect(async () => {
      await page.goto('/', { waitUntil: 'domcontentloaded' });
      await expect(page.locator(`.storefront-${templateKey}`)).toBeVisible();
    }).toPass({ timeout: 30000, intervals: [1000, 2000, 3000, 5000] });
    // default 模版へ落ちていない（＝切替が実際に反映されている）ことを確認。
    await expect(page.locator('.storefront-default')).toHaveCount(0);
  } finally {
    await context.close();
  }
});

// template_key を無条件で default へ復元する（テスト失敗・途中クラッシュでも実行）。
// 復元 hook 自身が独立してログインし直すため、テスト側の状態に依存しない。
After({ tags: '@template-switch' }, async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  await setTemplateKey(request, token, 'default');
});
