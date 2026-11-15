import { expect, type Page, type TestInfo } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { getPublicTemplateKey, loginAsStoreAdmin, setTemplateKey } from './store-api';
import { BASE_URL } from '../base-url';

const { Given, When, Then, After } = createBdd();

/**
 * 公開サイトのトップを開き、殻が最も外側に出す標識が指定のテンプレートになるまで待つ。
 * サイト設定は frontend が 60 秒キャッシュするので、切り替えた直後は古いテンプレートで描かれる。
 * 固定 sleep は使わず、キャッシュの寿命を越える有界リトライで吸収する。
 */
export async function gotoTopWithTemplate(
  page: Page,
  testInfo: TestInfo,
  templateKey: string,
): Promise<void> {
  // toPass の 90 秒は既定のテストタイムアウトを超えるため、既定値への加算で延ばす。
  testInfo.setTimeout(testInfo.timeout + 100000);
  await expect(async () => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('[data-storefront-template]').first()).toHaveAttribute(
      'data-storefront-template',
      templateKey,
      { timeout: 2000 },
    );
  }).toPass({ timeout: 90000, intervals: [2000, 5000, 10000] });
}

Given('店舗 {string} の template_key が {string} である', async ({ page, request, $testInfo }, _store: string, templateKey: string) => {
  const token = await loginAsStoreAdmin(request);
  await setTemplateKey(request, token, templateKey);
  expect(await getPublicTemplateKey(request)).toBe(templateKey);
  // 設定値だけでなく、公開サイトがそのテンプレートで描かれるところまで前提にする。直前のシナリオが
  // 残したキャッシュのまま進むと、後続の切替の検証が切替を経ずに通ってしまう。
  await gotoTopWithTemplate(page, $testInfo, templateKey);
});

When('template_key を {string} に変更する', async ({ request }, templateKey: string) => {
  const token = await loginAsStoreAdmin(request);
  await setTemplateKey(request, token, templateKey);
});

Then('新しいブラウザコンテキストで公開サイトが {word} テンプレートで描画される', async ({ browser, $testInfo }, templateKey: string) => {
  // cookie も localStorage も持たない来訪者として開く。
  const context = await browser.newContext({ baseURL: BASE_URL });
  try {
    await gotoTopWithTemplate(await context.newPage(), $testInfo, templateKey);
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
