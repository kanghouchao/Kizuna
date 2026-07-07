import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';

const { Given, Then } = createBdd();

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
