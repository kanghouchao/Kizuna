import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { seededCastName } from './cast-detail-404.steps';
import { gotoTopWithTemplate } from './template-switch.steps';

const { Given, When, Then } = createBdd();

Given('店舗 {string} の公開サイトで年齢確認を通過する', async ({ page }, _store: string) => {
  await page.goto('/');
  // 年齢確認オーバーレイの「はい」（accessible name は "はい YES"）を押下し、
  // オーバーレイが閉じきる（DOM から外れる）まで待つ。以降 localStorage 記録により再表示されない。
  await page.getByRole('button', { name: 'はい' }).click();
  await expect(page.getByRole('button', { name: 'はい' })).toBeHidden();
});

Then('トップページの主要セクションが表示される', async ({ page }) => {
  // seed データに依存しない静的セクションで判定する（Banner の CTA と Advertisement 見出し）。
  // exact 一致で CastSection の「全てのキャストを見る」等の部分一致衝突を避ける。
  await expect(page.getByRole('link', { name: 'キャストを見る', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'キャンペーン情報', exact: true })).toBeVisible();
});

Then(
  'ページ {string} に見出し {string} が表示される',
  async ({ page }, path: string, heading: string) => {
    await page.goto(path);
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
  }
);

When(
  '公開サイトのトップを {word} テンプレートで開いて年齢確認を通過する',
  async ({ page, $testInfo }, templateKey: string) => {
    await gotoTopWithTemplate(page, $testInfo, templateKey);
    await page.getByRole('button', { name: 'はい' }).click();
    await expect(page.getByRole('button', { name: 'はい' })).toBeHidden();
  }
);

Then(
  'トップに店名 {string} と作成したキャストが表示される',
  async ({ page, $testInfo }, storeName: string) => {
    await expect(page.getByRole('heading', { name: storeName, exact: true }).first()).toBeVisible();
    // キャスト一覧は frontend が 60 秒キャッシュするので、作成した直後は出ない。有界リトライで吸収する。
    $testInfo.setTimeout($testInfo.timeout + 100000);
    await expect(async () => {
      await page.reload({ waitUntil: 'domcontentloaded' });
      await expect(page.getByRole('heading', { name: seededCastName() }).first()).toBeVisible({
        timeout: 2000,
      });
    }).toPass({ timeout: 90000, intervals: [2000, 5000, 10000] });
  }
);

Then('ナビゲーションからゲスト予約のページへ進める', async ({ page }) => {
  await page.getByRole('link', { name: 'ご予約', exact: true }).first().click();
  await expect(page).toHaveURL(/\/reservation$/);
  await expect(page.getByRole('heading', { name: 'ご予約', exact: true })).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'この内容で予約を希望する', exact: true })
  ).toBeVisible();
});
